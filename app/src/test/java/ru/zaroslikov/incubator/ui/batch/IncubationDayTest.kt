package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.zaroslikov.incubator.ui.incubator.atTimeOf
import ru.zaroslikov.incubator.ui.parseDate

/**
 * Номер дня инкубации — арифметика, которую делят шторка закладки и шторка замеров
 * инкубатора; здесь она пришпилена, чтобы замер по инкубатору не мог лечь не в тот день.
 */
class IncubationDayTest {

    @Test
    fun `в день закладки идёт первый день`() {
        assertEquals(1, incubationDay(date("01.08.2026"), 21, date("01.08.2026")))
    }

    @Test
    fun `на шестые сутки от начала идёт шестой день`() {
        assertEquals(6, incubationDay(date("01.08.2026"), 21, date("06.08.2026")))
    }

    @Test
    fun `после срока день упирается в срок`() {
        assertEquals(21, incubationDay(date("01.08.2026"), 21, date("30.08.2026")))
    }

    @Test
    fun `без срока день растёт дальше`() {
        assertEquals(30, incubationDay(date("01.08.2026"), null, date("30.08.2026")))
    }

    @Test
    fun `дата начала в будущем — всё равно первый день`() {
        assertEquals(1, incubationDay(date("10.08.2026"), 21, date("01.08.2026")))
    }

    @Test
    fun `неразобранная дата начала — первый день`() {
        assertEquals(1, incubationDay(null, 21, date("15.08.2026")))
    }

    @Test
    fun `день начинается в час закладки, а не в полночь`() {
        // Заложили 01.08 в 10:00: 21.08 в 08:00 идёт ещё двадцатый день, с 10:00 — двадцать первый.
        val start = at("01.08.2026", "10:00")
        assertEquals(20, incubationDay(start, 21, at("21.08.2026", "08:00")))
        assertEquals(21, incubationDay(start, 21, at("21.08.2026", "10:00")))
        // Вывод — 22.08 в 10:00, и до него весь 21-й день: ровно сутки, не больше.
        assertEquals(21, incubationDay(start, 21, at("22.08.2026", "09:59")))
    }

    @Test
    fun `без часа закладки день меняется в полночь`() {
        val start = at("01.08.2026", "")
        assertEquals(21, incubationDay(start, 21, at("21.08.2026", "00:01")))
    }

    private fun date(text: String) = checkNotNull(parseDate(text))

    private fun at(day: String, time: String) = date(day).atTimeOf(time)
}
