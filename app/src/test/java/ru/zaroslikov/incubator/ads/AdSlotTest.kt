package ru.zaroslikov.incubator.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Где в списке стоят объявления: первое — сразу после первой карточки, дальше через
 * каждые три, и не больше пяти мест на список.
 *
 * Правило целиком арифметическое, и держать его глазами незачем: сдвинется шаг, первое
 * место или потолок — упадёт этот тест, а не отчёт в рекламном кабинете через месяц.
 */
class AdSlotTest {

    /** Пары «после какой карточки — какое рекламное место» для списка длиной [count]. */
    private fun slotsIn(count: Int): List<Pair<Int, Int>> =
        (0 until count).mapNotNull { index -> adSlotAfter(index)?.let { slot -> index to slot } }

    @Test
    fun `the first card always gets one`() {
        assertEquals(0, adSlotAfter(index = 0))
    }

    @Test
    fun `then every third card`() {
        // Карточки 1, 4, 7, 10 — в нумерации с нуля 0, 3, 6, 9.
        assertEquals(listOf(0 to 0, 3 to 1, 6 to 2, 9 to 3), slotsIn(11))
    }

    @Test
    fun `nothing between them`() {
        assertNull(adSlotAfter(index = 1))
        assertNull(adSlotAfter(index = 2))
        assertNull(adSlotAfter(index = 4))
        assertNull(adSlotAfter(index = 5))
    }

    @Test
    fun `a short list still gets the first one`() {
        // Одна карточка — одно объявление; две и три — тоже одно, второе место дальше.
        assertEquals(listOf(0 to 0), slotsIn(1))
        assertEquals(listOf(0 to 0), slotsIn(2))
        assertEquals(listOf(0 to 0), slotsIn(3))
    }

    @Test
    fun `the second one arrives with the fourth card`() {
        assertEquals(listOf(0 to 0, 3 to 1), slotsIn(4))
    }

    @Test
    fun `slots stop at the cap`() {
        // Пять мест — потолок: каждое объявление держит свой WebView, и на списке из
        // полусотни закладок их иначе набралось бы полтора десятка.
        val slots = slotsIn(60)
        assertEquals(5, slots.size)
        assertEquals(listOf(0 to 0, 3 to 1, 6 to 2, 9 to 3, 12 to 4), slots)
    }
}
