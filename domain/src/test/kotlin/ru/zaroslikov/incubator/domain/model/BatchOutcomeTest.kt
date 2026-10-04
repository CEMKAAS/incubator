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
            candlingRejected = 0,
        )

        assertEquals(BatchStatus.Hatched, result.status)
        assertEquals(30, result.eggAllEND) // больше заложенного не вывести
        assertEquals("22.08.2026", result.dateEnd)
        assertEquals("", result.endReason)
        assertEquals(100, result.chickPrice)
        assertTrue(result.chickPricePerHead)
        assertEquals(22, result.price)
    }

    @Test
    fun `всё, что не вылупилось, уходит в отбраковку`() {
        // 20 заложено, 4 убрано на овоскопировании, 2 вручную; вывелось 5.
        val result = batch(eggAll = 20, eggRejected = 2)
            .finishedOnTime(HatchOutcome(hatched = 5), "22.08.2026", candlingRejected = 4)

        assertEquals(5, result.eggAllEND)
        assertEquals(11, result.eggRejected) // 20 − 5 − 4: ручной брак дописан остатком
        assertEquals(20, result.eggAllEND + result.eggRejected + 4)
    }

    @Test
    fun `птенцов не больше, чем пережило овоскопирования`() {
        val result = batch(eggAll = 20)
            .finishedOnTime(HatchOutcome(hatched = 20), "", candlingRejected = 4)

        assertEquals(16, result.eggAllEND)
        assertEquals(0, result.eggRejected)
    }

    @Test
    fun `отрицательная цена птенцов становится «не указана»`() {
        val result = batch(eggAll = 30).finishedOnTime(HatchOutcome(10, chickPrice = -5), "", candlingRejected = 0)
        assertEquals(0, result.chickPrice)
    }

    @Test
    fun `досрочное завершение и возврат в инкубацию стирают вывод и цену птенцов`() {
        val finished = batch(eggAll = 50, eggRejected = 3)
            .finishedOnTime(HatchOutcome(40, 400), "22.08.2026", candlingRejected = 0)
        assertEquals(400, finished.chickPrice)

        val reopened = finished.reopened()
        assertEquals(BatchStatus.Active, reopened.status)
        assertEquals(0, reopened.eggAllEND)
        assertEquals(0, reopened.chickPrice)
        assertEquals("", reopened.dateEnd)
        assertEquals(10, reopened.eggRejected)

        val stopped = finished.stoppedEarly(" Отключили свет ", "10.08.2026")
        assertEquals(BatchStatus.Stopped, stopped.status)
        assertEquals(0, stopped.eggAllEND)
        assertEquals(0, stopped.chickPrice)
        assertEquals("Отключили свет", stopped.endReason)
        assertEquals("10.08.2026", stopped.dateEnd)
        assertEquals(10, stopped.eggRejected)
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

    @Test
    fun finishedOnTime_recordsTheEndHour_andReopenClearsIt() {
        val finished = batch(eggAll = 20)
            .finishedOnTime(HatchOutcome(15), "22.08.2026", candlingRejected = 0, timeEnd = "07:40")
        assertEquals("22.08.2026", finished.dateEnd)
        assertEquals("07:40", finished.timeEnd)
        assertEquals("", finished.reopened().timeEnd)
        assertEquals("", finished.stoppedEarly("Свет", "23.08.2026").timeEnd)
    }

    @Test
    fun stoppedEarly_recordsTheHourTheIncubatorWasSwitchedOff() {
        val stopped = batch(eggAll = 20).stoppedEarly("Отключили свет", "05.08.2026", "14:15")
        assertEquals("05.08.2026", stopped.dateEnd)
        assertEquals("14:15", stopped.timeEnd)
        assertEquals("", stopped.reopened().timeEnd)
    }
}
