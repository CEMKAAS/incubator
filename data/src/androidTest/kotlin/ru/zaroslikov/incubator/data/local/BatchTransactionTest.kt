package ru.zaroslikov.incubator.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.zaroslikov.incubator.data.repository.OfflineItemsRepository
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

/**
 * Закладка кладётся целиком — и её после этого можно удалить, а подписка на удалённую
 * строку не падает.
 *
 * Три вещи, которые проверяются здесь, а не в юнит-тесте: транзакцию исполняет настоящий
 * SQLite, каскад по внешним ключам тоже, и `Flow` одиночной строки ведёт себя так, как
 * его сгенерировал Room, — а именно на нём приложение и падало, когда результат был
 * объявлен не-nullable.
 */
@RunWith(AndroidJUnit4::class)
class BatchTransactionTest {

    private lateinit var db: InventoryDatabase
    private lateinit var repository: ItemsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // В памяти: проверяется поведение схемы и запросов, а не файл на диске.
        db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        repository = OfflineItemsRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertBatchWithSchedule_writesEverythingAndStampsOwner() = runBlocking {
        val incubatorId = repository.insertIncubator(
            Incubator(name = "Блиц", capacity = 72, autoTurn = false, autoAiring = false)
        )
        val days = (1..30).map { day ->
            Value(day = day, temp = 37.8, damp = 55.0, over = 4, airingCount = 2, airingTime = 5, note = "", idPT = 0)
        }
        val times = listOf(Time(time = "08:00", idPT = 0), Time(time = "20:00", idPT = 0))

        val batchId = repository.insertBatchWithSchedule(
            batch = batch(incubatorId),
            species = Species(species = "Гуси", idPT = 0),
            days = days,
            times = times,
        )

        val saved = repository.getBatchValues(batchId).first()
        assertEquals("расписание пришло неполным", 30, saved.size)
        assertEquals("дни перепутаны", (1..30).toList(), saved.map { it.day })
        assertTrue("дни не привязаны к закладке", saved.all { it.idPT == batchId })
        assertTrue("дни не получили своих идентификаторов", saved.all { it.id != 0L })

        assertEquals(2, repository.getTimeList(batchId).size)
        assertEquals(listOf("Гуси"), repository.getSpeciesList(batchId).map { it.species })
    }

    /**
     * Дни держатся на внешнем ключе, и удаление закладки должно унести их с собой — ровно
     * то, ради чего у `Batch_value.idPT` теперь есть индекс.
     */
    @Test
    fun deletingBatch_cascadesToSchedule() = runBlocking {
        val incubatorId = repository.insertIncubator(
            Incubator(name = "Блиц", capacity = 72, autoTurn = false, autoAiring = false)
        )
        val batchId = repository.insertBatchWithSchedule(
            batch = incubatorId.let { batch(it) },
            species = Species(species = "Курицы", idPT = 0),
            days = listOf(Value(day = 1, temp = null, damp = null, over = null, airingCount = null, airingTime = null, note = "", idPT = 0)),
            times = listOf(Time(time = "08:00", idPT = 0)),
        )

        val stored = repository.getBatch(batchId).first()!!
        repository.deleteBatch(stored)

        assertTrue(repository.getBatchValues(batchId).first().isEmpty())
        assertTrue(repository.getTimeList(batchId).isEmpty())
    }

    /**
     * Подписка на закладку переживает её удаление: пустой ответ — это `null`, а не
     * исключение в коллекторе. Раньше Room генерировал здесь `error(...)`, и удаление
     * закладки при открытой шторке роняло приложение.
     */
    @Test
    fun missingBatch_emitsNullInsteadOfCrashing() = runBlocking {
        assertNull(repository.getBatch(404).first())
        assertNull(repository.getIncubator(404).first())
        assertNull(repository.getValue(404).first())
    }

    /**
     * Порода едет с закладкой как обычное поле — при создании с расписанием, при правке
     * и при чтении списком, — а овоскопирование за уже записанный день переписывается,
     * а не ложится рядом (`REPLACE` по уникальной паре `idPT` + `day`).
     */
    @Test
    fun breedAndCandling_roundTrip() = runBlocking {
        val incubatorId = repository.insertIncubator(
            Incubator(name = "Блиц", capacity = 72, autoTurn = false, autoAiring = false)
        )
        val batchId = repository.insertBatchWithSchedule(
            batch = batch(incubatorId).copy(breed = "Хайсекс"),
            species = Species(species = "Гуси", idPT = 0),
            days = emptyList(),
            times = emptyList(),
        )

        val stored = repository.getBatch(batchId).first()!!
        assertEquals("Хайсекс", stored.breed)
        assertEquals(30, stored.eggAll)

        repository.updateBatch(stored.copy(breed = "Ломан Браун", eggAll = 25))
        val edited = repository.getBatch(batchId).first()!!
        assertEquals("Ломан Браун", edited.breed)
        assertEquals(25, edited.eggAll)
        assertEquals("Ломан Браун", repository.getAllBatches().first().single().breed)

        repository.saveCandling(Candling(idPT = batchId, day = 7, date = "08.09.2026", rejected = 3))
        val saved = repository.getCandlings(batchId).first().single()
        assertEquals(3, saved.rejected)

        repository.saveCandling(
            Candling(id = saved.id, idPT = batchId, day = 7, date = "09.09.2026", rejected = 5)
        )
        val rewritten = repository.getCandlings(batchId).first().single()
        assertEquals(5, rewritten.rejected)

        repository.deleteBatch(edited)
        assertNull(repository.getBatch(batchId).first())
        assertTrue(repository.getCandlings(batchId).first().isEmpty())
    }

    private fun batch(incubatorId: Long) = Batch(
        title = "Гуси",
        type = "Гуси",
        data = "01.09.2026",
        eggAll = 30,
        eggAllEND = 0,
        airing = "false",
        over = "false",
        arhive = "0",
        dateEnd = "",
        note = "",
        incubatorId = incubatorId,
    )
}
