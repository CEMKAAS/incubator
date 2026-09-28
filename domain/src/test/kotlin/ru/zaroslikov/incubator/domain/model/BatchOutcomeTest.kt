package ru.zaroslikov.incubator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Закрепляет трёх писателей итога закладки — [finishedOnTime], [stoppedEarly] и
 * [reopened] — и подсказки пород по одному полю [Batch.breed].
 */
class BatchOutcomeTest {

    private fun batch(
        eggAll: Int,
        eggAllEND: Int = 0,
        arhive: String = "0",
        eggRejected: Int = 0,
        price: Int = 0,
        breed: String = "",
    ) = Batch(
        title = "Куры",
        type = "Курицы",
        data = "01.08.2026",
        eggAll = eggAll,
        eggAllEND = eggAllEND,
        airing = "false",
        over = "false",
        arhive = arhive,
        dateEnd = "",
        note = "",
        eggRejected = eggRejected,
        price = price,
        breed = breed,
    )

    @Test
    fun `завершение в срок пишет вывод, зажатый яйцами, и цену птенцов`() {
        val result = batch(eggAll = 30, eggRejected = 2, price = 22).finishedOnTime(
            outcome = HatchOutcome(hatched = 45, chickPrice = 100),
            dateEnd = "22.08.2026",
        )

        assertEquals(BatchStatus.Hatched, result.status)
        assertEquals(30, result.eggAllEND) // больше заложенного не вывести
        assertEquals("22.08.2026", result.dateEnd)
        assertEquals("", result.endReason)
        assertEquals(100, result.chickPrice)
        assertTrue(result.chickPricePerHead)
        // Яйца были куплены, и брак уже случился — это не итог, и оно не трогается.
        assertEquals(2, result.eggRejected)
        assertEquals(22, result.price)
    }

    @Test
    fun `отрицательная цена птенцов становится «не указана»`() {
        val result = batch(eggAll = 30).finishedOnTime(HatchOutcome(10, chickPrice = -5), "")
        assertEquals(0, result.chickPrice)
    }

    @Test
    fun `досрочное завершение и возврат в инкубацию стирают вывод и цену птенцов`() {
        val finished = batch(eggAll = 50, eggRejected = 3)
            .finishedOnTime(HatchOutcome(40, 400), "22.08.2026")
        assertEquals(400, finished.chickPrice)

        val reopened = finished.reopened()
        assertEquals(BatchStatus.Active, reopened.status)
        assertEquals(0, reopened.eggAllEND)
        assertEquals(0, reopened.chickPrice)
        assertEquals("", reopened.dateEnd)
        assertEquals(3, reopened.eggRejected)

        val stopped = finished.stoppedEarly(" Отключили свет ", "10.08.2026")
        assertEquals(BatchStatus.Stopped, stopped.status)
        assertEquals(0, stopped.eggAllEND)
        assertEquals(0, stopped.chickPrice)
        assertEquals("Отключили свет", stopped.endReason)
        assertEquals("10.08.2026", stopped.dateEnd)
        assertEquals(3, stopped.eggRejected)
    }

    /**
     * Подсказки — породы своего вида, свежие первыми, без повторов в другом регистре,
     * и написание берётся у самой свежей закладки.
     */
    @Test
    fun `известные породы — своего вида, свежие первыми и без повторов`() {
        val batches = listOf(
            batch(eggAll = 30, breed = "ломан браун ").copy(id = 1),
            batch(eggAll = 30, breed = "Пекинская").copy(id = 2, type = "Утки"),
            batch(eggAll = 30, breed = "Ломан Браун").copy(id = 3),
            batch(eggAll = 30, breed = "Хайсекс").copy(id = 4),
            batch(eggAll = 30, breed = "Адлерская").copy(id = 5),
            batch(eggAll = 30, breed = "  ").copy(id = 6),
        )

        assertEquals(
            listOf("Адлерская", "Хайсекс", "Ломан Браун"),
            knownBreeds(batches, "Курицы"),
        )
        assertEquals(listOf("Пекинская"), knownBreeds(batches, "Утки"))
        assertTrue(knownBreeds(batches, "Гуси").isEmpty())
    }
}
