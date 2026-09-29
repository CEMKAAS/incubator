package ru.zaroslikov.incubator.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch

/**
 * Закрепляет сводку поздравления: вывод считается от заложенного, деньги — только
 * введённые, а партия складывается, не выдумывая пропущенных цен.
 */
class HatchSummaryTest {

    private fun batch(
        eggAll: Int,
        hatched: Int,
        price: Int = 0,
        pricePerEgg: Boolean = true,
        chickPrice: Int = 0,
        chickPricePerHead: Boolean = true,
        breed: String = "",
    ) = Batch(
        title = "Весенняя",
        type = "Курицы",
        data = "01.08.2026",
        eggAll = eggAll,
        eggAllEND = hatched,
        airing = "false",
        over = "false",
        arhive = "1",
        dateEnd = "22.08.2026",
        note = "",
        price = price,
        pricePerEgg = pricePerEgg,
        chickPrice = chickPrice,
        chickPricePerHead = chickPricePerHead,
        breed = breed,
    )

    @Test
    fun `вывод считается от заложенного, а не от оставшихся`() {
        val summary = hatchSummaryOf(batch(eggAll = 40, hatched = 30), rejected = 5, termDays = 21)

        assertEquals(40, summary.eggs)
        assertEquals(5, summary.rejected)
        assertEquals(30, summary.hatched)
        assertEquals(75, summary.rate)
        assertEquals(21, summary.termDays)
    }

    @Test
    fun `без цен денег нет вовсе`() {
        val summary = hatchSummaryOf(batch(eggAll = 10, hatched = 8), rejected = 0, termDays = null)

        assertFalse(summary.hasMoney)
        assertNull(summary.profit)
        assertEquals(0, summary.invested)
        assertEquals(0, summary.income)
        assertNull(summary.termDays)
    }

    @Test
    fun `прибыль только при обеих ценах`() {
        val onlyEggs = hatchSummaryOf(batch(eggAll = 10, hatched = 8, price = 20), 0, 21)
        assertTrue(onlyEggs.hasMoney)
        assertEquals(200, onlyEggs.invested)
        assertNull(onlyEggs.profit)

        val both = hatchSummaryOf(
            batch(eggAll = 10, hatched = 8, price = 20, chickPrice = 100),
            rejected = 0,
            termDays = 21,
        )
        assertEquals(800, both.income)
        assertEquals(600, both.profit)
    }

    @Test
    fun `цена за всё берётся как ввели`() {
        val summary = hatchSummaryOf(
            batch(eggAll = 10, hatched = 8, price = 350, pricePerEgg = false,
                chickPrice = 1000, chickPricePerHead = false),
            rejected = 0,
            termDays = 21,
        )
        assertEquals(350, summary.invested)
        assertEquals(1000, summary.income)
        assertEquals(650, summary.profit)
    }

    @Test
    fun `ноль яиц не делит на ноль`() {
        assertEquals(0, hatchSummaryOf(batch(eggAll = 0, hatched = 0), 0, 21).rate)
    }

    @Test
    fun `партия складывает яйца и птенцов, срок — общий`() {
        val total = listOf(
            hatchSummaryOf(batch(eggAll = 20, hatched = 18, breed = "Хайсекс"), 1, 21),
            hatchSummaryOf(batch(eggAll = 10, hatched = 5, breed = "Ломан"), 2, 21),
        ).combined()

        assertEquals(30, total.eggs)
        assertEquals(3, total.rejected)
        assertEquals(23, total.hatched)
        assertEquals(76, total.rate)
        assertEquals(21, total.termDays)
        assertEquals("", total.breed)
    }

    @Test
    fun `деньги партии — только когда цена есть у каждой породы`() {
        val partial = listOf(
            hatchSummaryOf(batch(eggAll = 20, hatched = 18, price = 10, chickPrice = 50), 0, 21),
            hatchSummaryOf(batch(eggAll = 10, hatched = 5, chickPrice = 50), 0, 21),
        ).combined()
        assertFalse(partial.hasEggPrice)
        assertTrue(partial.hasChickPrice)
        assertEquals(0, partial.invested)
        assertEquals(18 * 50 + 5 * 50, partial.income)
        assertNull(partial.profit)

        val full = listOf(
            hatchSummaryOf(batch(eggAll = 20, hatched = 18, price = 10, chickPrice = 50), 0, 21),
            hatchSummaryOf(batch(eggAll = 10, hatched = 5, price = 10, chickPrice = 50), 0, 21),
        ).combined()
        assertEquals(300, full.invested)
        assertEquals(1150, full.income)
        assertEquals(850, full.profit)
    }
}
