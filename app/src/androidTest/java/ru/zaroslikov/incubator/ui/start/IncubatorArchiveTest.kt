package ru.zaroslikov.incubator.ui.start

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository

/**
 * Что архив инкубатора делает с его закладками — и, главное, чего он с ними не делает.
 *
 * Устройство выводят из работы, и идущие в нём закладки прерываются вместе с ним:
 * `arhive = "1"`, нулевой вывод и причина [StartScreenViewModel.ARCHIVE_END_REASON], то
 * есть статус «Прервано». Держать их идущими значило бы считать в «в работе» яйца,
 * которых уже никто не греет.
 *
 * Границ у этого две, и обе здесь пришпилены. Уже завершённые закладки архив не трогает
 * — ни вывод, ни чужую причину досрочного завершения: их итог записан, и переписывать
 * его архиву устройства не за чем. И возврат из архива ничего не отменяет: он снимает с
 * инкубатора «только просмотр», а закладку возвращают в работу поштучно, её собственным
 * «Вернуть в инкубацию».
 *
 * Инструментальный, а не JVM: репозиторий здесь настоящий, Room и всё остальное — тоже,
 * потому что вопрос ровно в том, что окажется в базе.
 */
@RunWith(AndroidJUnit4::class)
class IncubatorArchiveTest {

    private lateinit var repository: ItemsRepository
    private lateinit var workRepository: WorkRepository
    private lateinit var viewModel: StartScreenViewModel

    private var incubatorId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<InventoryApplication>()
        repository = application.container.itemsRepository
        workRepository = application.container.workRepository
        viewModel = StartScreenViewModel(repository, workRepository)
        incubatorId = repository.insertIncubator(
            Incubator(
                name = "Инкубатор под архив",
                capacity = 72,
                autoTurn = false,
                autoAiring = false,
            )
        )
    }

    /** Каскад унесёт закладки вместе с инкубатором — чистить их отдельно не нужно. */
    @After
    fun tearDown() = runBlocking {
        repository.getIncubator(incubatorId).first()!!.let { repository.deleteIncubator(it) }
    }

    @Test
    fun архив_инкубатора_прерывает_его_идущие_закладки() = runBlocking {
        val active = newBatch("Идёт")
        assertEquals(BatchStatus.Active, repository.getBatch(active).first()!!.status)

        archive(hidden = true)
        awaitStopped(active)

        val batch = repository.getBatch(active).first()!!
        assertEquals(BatchStatus.Stopped, batch.status)
        assertEquals(StartScreenViewModel.ARCHIVE_END_REASON, batch.endReason)
        // Ноль вывода здесь — итог, а не его отсутствие: птенцов не будет.
        assertEquals(0, batch.eggAllEND)
        // Дата окончания проставлена: без неё карточка не сможет показать «Прервано …».
        assertNotEquals("", batch.dateEnd)
    }

    @Test
    fun завершённые_закладки_архив_не_трогает() = runBlocking {
        val active = newBatch("Идёт")
        val hatched = newBatch("Довели", arhive = "1", eggAllEND = 25)
        val stopped = newBatch("Сорвалась", arhive = "1", endReason = "Отключили свет")

        archive(hidden = true)
        awaitStopped(active)

        // Вывод доведённой закладки на месте, и «Завершено» не превратилось в «Прервано».
        val done = repository.getBatch(hatched).first()!!
        assertEquals(BatchStatus.Hatched, done.status)
        assertEquals(25, done.eggAllEND)
        assertEquals("", done.endReason)

        // Чужая причина досрочного завершения не переписана причиной архива.
        val early = repository.getBatch(stopped).first()!!
        assertEquals(BatchStatus.Stopped, early.status)
        assertEquals("Отключили свет", early.endReason)
    }

    /**
     * Возврат инкубатора из архива не воскрешает прерванные закладки.
     *
     * Иначе воскресли бы и те, что были прерваны до архива, — по причине их уже не
     * отличить, а «Отключили свет» и «Инкубатор переведён в архив» это разные истории.
     */
    @Test
    fun возврат_из_архива_не_возвращает_закладки_в_работу() = runBlocking {
        val active = newBatch("Идёт")

        archive(hidden = true)
        awaitStopped(active)
        archive(hidden = false)

        assertEquals(false, repository.getIncubator(incubatorId).first()!!.hidden)
        val batch = repository.getBatch(active).first()!!
        assertEquals(BatchStatus.Stopped, batch.status)
        assertEquals(StartScreenViewModel.ARCHIVE_END_REASON, batch.endReason)
    }

    /**
     * Правка инкубатора в архиве не достаёт его оттуда.
     *
     * Форма не показывает флаг архива и не должна, но носит его через
     * [ru.zaroslikov.incubator.ui.incubator.IncubatorFormUiState]: без этого сохранение
     * писало `hidden = false`, и переименованный инкубатор всплывал в списке сам собой.
     */
    @Test
    fun правка_инкубатора_сохраняет_архив() = runBlocking {
        archive(hidden = true)

        val archived = repository.getIncubator(incubatorId).first()!!
        repository.updateIncubator(archived.copy(name = "Переименован"))

        val after = repository.getIncubator(incubatorId).first()!!
        assertEquals("Переименован", after.name)
        assertTrue(after.hidden)
    }

    /** Ставит флаг архива тем же путём, каким его ставит меню карточки, и ждёт записи. */
    private suspend fun archive(hidden: Boolean) {
        val incubator = repository.getIncubator(incubatorId).first()!!
        viewModel.onIntent(StartIntent.SetIncubatorHidden(incubator, hidden))
        // Намерение уходит в viewModelScope, поэтому дожидаемся не вызова, а
        // того, что записано в базе: спрашиваем её, пока не увидим нужное состояние.
        awaitDb("Флаг архива так и не записался") {
            repository.getIncubator(incubatorId).first()!!.hidden == hidden
        }
    }

    /**
     * Закладки прерываются после записи самого инкубатора и в той же корутине, поэтому
     * ждать приходится отдельно: флаг уже в базе, а закладка ещё нет.
     */
    private suspend fun awaitStopped(batchId: Long) = awaitDb("Закладка так и не прервалась") {
        repository.getBatch(batchId).first()!!.status == BatchStatus.Stopped
    }

    private suspend fun awaitDb(message: String, done: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + WRITE_TIMEOUT_MILLIS
        while (!done()) {
            assertTrue(
                "$message за $WRITE_TIMEOUT_MILLIS мс",
                System.currentTimeMillis() < deadline,
            )
        }
    }

    private suspend fun newBatch(
        title: String,
        arhive: String = "0",
        eggAllEND: Int = 0,
        endReason: String = "",
    ): Long = repository.insertBatch(
        Batch(
            title = title,
            type = "Курицы",
            data = "01.08.2026",
            eggAll = 30,
            eggAllEND = eggAllEND,
            airing = "false",
            over = "false",
            arhive = arhive,
            dateEnd = if (arhive == "0") "" else "22.08.2026",
            note = "",
            incubatorId = incubatorId,
            endReason = endReason,
        )
    )

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 5_000L
    }
}
