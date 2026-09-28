package ru.zaroslikov.incubator.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

/**
 * Расписание, выведенное из базы, — то, чем уведомления переживают экспорт/импорт и
 * любую правку закладок.
 *
 * Импорт здесь не подменяется файлом: подмена базы кончается смертью процесса, и
 * воспроизвести её внутри теста нельзя. Воспроизводится то, что от неё остаётся, —
 * состояние «в базе закладки, о которых WorkManager ничего не знает» и обратное ему
 * «в WorkManager работа от расписания, которого в базе больше нет». Пересчёт обязан
 * привести и то, и другое к тому, что записано в базе, — а после импорта его запускает
 * первое же открытие приложения.
 *
 * Работа теперь одна на всё приложение, поэтому и проверяется здесь одно: стоит ли она,
 * и на тот ли момент.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSyncTest {

    private lateinit var context: Context
    private lateinit var repository: ItemsRepository
    private lateinit var scheduler: ReminderScheduler
    private lateinit var sync: ReminderSync
    private lateinit var workManager: WorkManager

    private var incubatorId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<InventoryApplication>()
        context = application
        repository = application.container.itemsRepository
        sync = application.container.reminderSync
        scheduler = ReminderScheduler(context)
        workManager = WorkManager.getInstance(context)

        scheduler.purgeLegacyWork()
        // Расписание считается по всей базе, поэтому чужие закладки — не фон, а часть
        // ответа: оставшаяся от ручной проверки закладка с более ранним временем сдвинула
        // бы ожидаемый момент. Тест начинает с пустого хозяйства.
        repository.getAllIncubators().first().forEach { repository.deleteIncubator(it) }
        incubatorId = repository.insertIncubator(
            Incubator(
                name = "Инкубатор для пересборки",
                capacity = 72,
                autoTurn = false,
                autoAiring = false,
            )
        )
    }

    @After
    fun tearDown() = runBlocking {
        scheduler.purgeLegacyWork()
        repository.getIncubator(incubatorId).first()?.let { repository.deleteIncubator(it) }
        Unit
    }

    @Test
    fun идущая_закладка_получает_работу() = runBlocking {
        addBatch(arhive = "0", times = listOf("08:00"))

        sync.sync()

        val work = liveWork()
        assertNotNull("Расписание не поставлено", work)
        assertScheduledAt(nextReminderMillis("08:00", System.currentTimeMillis())!!, work!!)
    }

    @Test
    fun работа_ставится_на_ближайшее_из_всех_времён() = runBlocking {
        // Диспетчер: работа одна, и она знает только про ближайший момент.
        addBatch(arhive = "0", times = listOf("23:30"))
        addBatch(arhive = "0", times = listOf("06:15"))

        sync.sync()

        val expected = minOf(
            nextReminderMillis("23:30", System.currentTimeMillis())!!,
            nextReminderMillis("06:15", System.currentTimeMillis())!!,
        )
        assertScheduledAt(expected, liveWork()!!)
    }

    @Test
    fun завершённая_закладка_работы_не_получает() = runBlocking {
        addBatch(arhive = "1", times = listOf("08:00"))

        sync.sync()

        assertNull("Завершённая закладка попала в расписание", liveWork())
    }

    @Test
    fun закладка_без_времён_работы_не_получает() = runBlocking {
        addBatch(arhive = "0", times = emptyList())

        sync.sync()

        assertNull("Расписание поставлено там, где звенеть нечему", liveWork())
    }

    @Test
    fun пересчёт_снимает_работу_от_исчезнувшего_расписания() = runBlocking {
        // Ровно то, что остаётся после импорта чужой базы: работа стоит, а закладок,
        // ради которых она стояла, больше нет.
        val batchId = addBatch(arhive = "0", times = listOf("08:00"))
        sync.sync()
        assertNotNull(liveWork())

        repository.deleteBatch(repository.getBatch(batchId).first()!!)
        sync.sync()

        assertNull("Работа пережила своё расписание", liveWork())
    }

    @Test
    fun повторный_пересчёт_не_переставляет_работу() = runBlocking {
        // Сверка, а не пересборка: одинаковый план не должен трогать уже стоящую работу
        // — иначе каждое открытие приложения снимало бы то, что вот-вот сработает.
        addBatch(arhive = "0", times = listOf("08:00"))
        sync.sync()
        val first = liveWork()!!.id

        sync.sync()

        assertEquals("Работу переставили без причины", first, liveWork()!!.id)
    }

    @Test
    fun новое_время_переставляет_работу() = runBlocking {
        // Времена берутся от текущего часа, а не константами: с «23:30 и 06:15» ответ
        // зависел бы от того, когда прогоняют тест — вечером ближайшим остаётся 23:30.
        val later = timeIn(minutes = 180)
        val earlier = timeIn(minutes = 60)
        val batchId = addBatch(arhive = "0", times = listOf(later))
        sync.sync()
        val before = liveWork()!!.nextScheduleTimeMillis

        repository.insertTime(Time(time = earlier, idPT = batchId))
        sync.sync()

        val work = liveWork()!!
        assertTrue("Работа осталась на прежнем моменте", work.nextScheduleTimeMillis != before)
        assertScheduledAt(nextReminderMillis(earlier, System.currentTimeMillis())!!, work)
    }

    // --- Оснастка ------------------------------------------------------------------

    /** «ЧЧ:ММ» через [minutes] минут от текущего момента. */
    private fun timeIn(minutes: Int): String {
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, minutes)
        }
        return String.format(
            java.util.Locale.US,
            "%02d:%02d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
        )
    }

    private suspend fun addBatch(arhive: String, times: List<String>): Long {
        val batchId = repository.insertBatch(
            Batch(
                title = "Закладка",
                type = "Курицы",
                data = "01.09.2026",
                eggAll = 30,
                eggAllEND = 0,
                airing = "false",
                over = "false",
                arhive = arhive,
                dateEnd = if (arhive == "0") "" else "10.09.2026",
                note = "",
                incubatorId = incubatorId,
            )
        )
        times.forEach { repository.insertTime(Time(time = it, idPT = batchId)) }
        return batchId
    }

    /**
     * Работа стоит на момент [expected].
     *
     * С допуском, а не с точностью до миллисекунды: задержку считают от «сейчас» дважды
     * — здесь и внутри WorkManager, — и между этими двумя «сейчас» проходит пара
     * миллисекунд.
     */
    private fun assertScheduledAt(expected: Long, work: WorkInfo) {
        val actual = work.nextScheduleTimeMillis
        assertTrue(
            "Работа стоит на $actual вместо $expected",
            kotlin.math.abs(actual - expected) < TOLERANCE_MILLIS,
        )
    }

    /** Живая работа-напоминание; `null` — расписания сейчас нет. */
    private fun liveWork(): WorkInfo? =
        workManager.getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()
            .firstOrNull { !it.state.isFinished }

    private companion object {
        const val TOLERANCE_MILLIS = 1_000L
    }
}
