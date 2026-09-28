package ru.zaroslikov.incubator.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.ReminderTarget
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

/**
 * Что делает напоминание, когда просыпается, — на настоящем устройстве и настоящей базе.
 *
 * Проверяется главное требование: **уведомление приходит, пока закладка идёт, и не
 * приходит после её завершения**. Работа прогоняется целиком, через
 * `TestListenableWorkerBuilder`, поэтому под тестом та же ветка кода, что и в жизни, —
 * с чтением закладки из базы и с постановкой уведомления в системную шторку, откуда
 * тест его и достаёт.
 *
 * Третий случай — удалённая закладка — стоит рядом не для полноты: `ON DELETE CASCADE`
 * до расписания WorkManager не достаёт, оно живёт вне базы, и работа удалённой закладки
 * остаётся живой до ближайшей пересборки.
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var context: Context
    private lateinit var repository: ItemsRepository
    private var incubatorId: Long = 0
    private var batchId: Long = 0

    private val time = "08:00"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<InventoryApplication>()
        repository = (context as InventoryApplication).container.itemsRepository

        incubatorId = repository.insertIncubator(
            Incubator(
                name = "Тестовый инкубатор",
                capacity = 72,
                autoTurn = false,
                autoAiring = false,
            )
        )
        batchId = repository.insertBatch(activeBatch())
        repository.insertTime(Time(time = time, idPT = batchId, note = "долить воды"))
        clearNotifications()
    }

    @After
    fun tearDown() = runBlocking {
        clearNotifications()
        // Работа ставит следующую сама — в тесте она никому не нужна.
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
        // Каскад заберёт закладку, её дни и напоминания вместе с инкубатором.
        repository.getIncubator(incubatorId).first()!!.let { repository.deleteIncubator(it) }
    }

    @Test
    fun идущая_закладка_показывает_уведомление() = runBlocking {
        // Иначе «уведомления нет» ниже означало бы отозванное разрешение, а не поведение.
        assertTrue(
            "У приложения нет разрешения на уведомления — проверять нечего",
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        )
        assertEquals(ListenableWorker.Result.success(), runWorker())

        val shown = postedNotification()
        assertNotNull("Уведомление идущей закладки не появилось", shown)
        assertEquals("Тестовая закладка", shown!!.first)
        assertEquals("долить воды", shown.second)
    }

    @Test
    fun завершённая_закладка_молчит() = runBlocking {
        repository.updateBatch(activeBatch().copy(id = batchId, arhive = "1", dateEnd = "22.08.2026"))

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertNull("Завершённая закладка всё-таки разбудила", postedNotification())
    }

    @Test
    fun досрочно_завершённая_закладка_тоже_молчит() = runBlocking {
        repository.updateBatch(
            activeBatch().copy(
                id = batchId,
                arhive = "1",
                eggAllEND = 0,
                endReason = "отключили свет",
                dateEnd = "22.08.2026",
            )
        )

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertNull("Прерванная закладка всё-таки разбудила", postedNotification())
    }

    @Test
    fun удалённая_закладка_молчит() = runBlocking {
        repository.deleteBatch(activeBatch().copy(id = batchId))

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertNull("Уведомление пришло по закладке, которой нет", postedNotification())
    }

    @Test
    fun переименованная_закладка_показывает_новое_название() = runBlocking {
        repository.updateBatch(activeBatch().copy(id = batchId, title = "Гуси, вторая"))

        runWorker()

        // Работу ставили под прежним названием — оно и лежит в inputData; в шторке
        // должно оказаться то, что сейчас в базе.
        assertEquals("Гуси, вторая", postedNotification()?.first)
    }

    @Test
    fun две_закладки_на_один_час_показывают_два_уведомления() = runBlocking {
        // Ради этого работа и стала одна на всё приложение: прежде каждая пара «закладка
        // × время» держала свою, и две закладки на восемь утра делили одно уникальное имя
        // — вторая молча вытесняла первую.
        val second = repository.insertBatch(activeBatch().copy(title = "Вторая закладка"))
        repository.insertTime(Time(time = time, idPT = second, note = "перевернуть"))

        runWorker()

        assertEquals("Тестовая закладка", postedNotification(batchId)?.first)
        assertEquals("Вторая закладка", postedNotification(second)?.first)
    }

    @Test
    fun работа_ставит_следующее_срабатывание() = runBlocking {
        // Периодической работы больше нет, и продолжает расписание только это: не
        // поставит — напоминания замолчат до следующего запуска приложения.
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)

        runWorker()

        val next = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()
            .firstOrNull { !it.state.isFinished }
        assertNotNull("Следующее срабатывание не поставлено", next)
    }

    @Test
    fun уведомление_ведёт_в_свою_закладку_своего_инкубатора() {
        // Обе стороны разом: работа собирает намерение, MainActivity его разбирает.
        // Разойдись они в имени ключа — уведомление молча открывало бы главный экран.
        val target = ReminderTarget.from(
            reminderContentIntent(context, incubatorId, batchId)
        )
        assertEquals(ReminderTarget(incubatorId, batchId), target)
    }

    @Test
    fun обычный_запуск_никуда_не_ведёт() {
        // Пустое намерение — не «инкубатор №0», а «никуда»: приложение открыли с ярлыка.
        assertNull(ReminderTarget.from(android.content.Intent()))
        assertNull(ReminderTarget.from(null))
    }

    // --- Оснастка ------------------------------------------------------------------

    private fun activeBatch() = Batch(
        title = "Тестовая закладка",
        type = "Курицы",
        data = "01.08.2026",
        eggAll = 30,
        eggAllEND = 0,
        airing = "false",
        over = "false",
        arhive = "0",
        dateEnd = "",
        note = "",
        incubatorId = incubatorId,
    )

    /**
     * Прогоняет работу так, как её запустила бы система: с моментом, на который она
     * ставилась. Что показать, работа решает сама, перечитав базу.
     */
    private suspend fun runWorker(at: Long = fireAt()): ListenableWorker.Result {
        val input = Data.Builder().putLong(KEY_FIRE_AT, at).build()
        return TestListenableWorkerBuilder<ReminderWorker>(context, input).build().doWork()
    }

    /** Ближайшее наступление тестового времени — им и помечена работа. */
    private fun fireAt(): Long = nextReminderMillis(time, System.currentTimeMillis())!!

    /**
     * Заголовок и текст уведомления этой закладки, или `null` — его нет в шторке.
     *
     * С ожиданием: `notify` уходит в системную службу через binder и возвращается
     * раньше, чем уведомление появляется в `activeNotifications`. Без ожидания тест
     * «идущая закладка будит» падал бы через раз, а тесты «молчит» проходили бы,
     * ничего не проверив.
     */
    private fun postedNotification(forBatch: Long = batchId): Pair<String, String>? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = reminderNotificationId(forBatch, time)
        val deadline = System.currentTimeMillis() + AWAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val posted = manager.activeNotifications.firstOrNull { it.id == id }
            if (posted != null) {
                val extras = posted.notification.extras
                return extras.getCharSequence("android.title").toString() to
                    extras.getCharSequence("android.text").toString()
            }
            Thread.sleep(POLL_MILLIS)
        }
        return null
    }

    private fun clearNotifications() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private companion object {
        /** Сколько ждать появления уведомления в шторке, прежде чем счесть, что его нет. */
        const val AWAIT_MILLIS = 3_000L
        const val POLL_MILLIS = 50L
    }
}
