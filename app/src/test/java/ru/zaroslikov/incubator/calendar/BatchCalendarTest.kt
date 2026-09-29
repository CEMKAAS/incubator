package ru.zaroslikov.incubator.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BatchCalendarTest {

    private val start = LocalDate.of(2026, 10, 1)

    /** Куриный режим: овоскопирования 7, 11, 16; переворот до 18-го, с 19-го — ноль. */
    private val chickenTurns: List<Int?> = (1..21).map { if (it <= 18) 6 else 0 }
    private val chickenCandling: (Int) -> Int = { day ->
        when (day) {
            7 -> 1
            11 -> 2
            16 -> 3
            else -> 0
        }
    }

    @Test
    fun chickenBatchGetsCandlingsStopTurningAndHatch() {
        val dates = batchCalendarDates(start, 21, chickenCandling, chickenTurns, today = start)

        assertEquals(
            listOf(
                CalendarDateKind.Candling to LocalDate.of(2026, 10, 7),
                CalendarDateKind.Candling to LocalDate.of(2026, 10, 11),
                CalendarDateKind.Candling to LocalDate.of(2026, 10, 16),
                CalendarDateKind.StopTurning to LocalDate.of(2026, 10, 19),
                CalendarDateKind.Hatch to LocalDate.of(2026, 10, 22),
            ),
            dates.map { it.kind to it.date },
        )
        assertEquals(listOf(1, 2, 3), dates.filter { it.kind == CalendarDateKind.Candling }.map { it.stage })
        assertEquals(19, dates.single { it.kind == CalendarDateKind.StopTurning }.day)
    }

    @Test
    fun hatchIsStartPlusTermLikeTheFinishPrompt() {
        val hatch = batchCalendarDates(start, 21, { 0 }, emptyList(), today = start).single()
        assertEquals(CalendarDateKind.Hatch, hatch.kind)
        assertEquals(start.plusDays(21), hatch.date)
    }

    @Test
    fun pastDatesAreDropped_todayIsKept() {
        // Закладку завели задним числом: сегодня — 11-й день, второе овоскопирование.
        val today = LocalDate.of(2026, 10, 11)
        val dates = batchCalendarDates(start, 21, chickenCandling, chickenTurns, today)
        assertEquals(LocalDate.of(2026, 10, 11), dates.first().date)
        assertTrue(dates.none { it.date.isBefore(today) })
        assertEquals(4, dates.size)
    }

    @Test
    fun allPastMeansNothingToOffer() {
        val dates = batchCalendarDates(start, 21, chickenCandling, chickenTurns, today = start.plusDays(40))
        assertTrue(dates.isEmpty())
    }

    @Test
    fun stopTurningNeedsATailOfZeros() {
        assertEquals(19, stopTurningDay(chickenTurns))
        // Переворачивают до последнего дня — дня перекладки нет.
        assertNull(stopTurningDay(List(21) { 4 }))
        // Переворотов не было вовсе — не о чем напоминать.
        assertNull(stopTurningDay(List(21) { 0 }))
        // Пустая клетка в хвосте — «квоты нет», а не «не переворачивать».
        assertNull(stopTurningDay(chickenTurns.toMutableList().also { it[20] = null }))
        // Ноль посреди режима, а потом снова перевороты — не хвост.
        assertNull(stopTurningDay(listOf(3, 0, 3, 3)))
        assertNull(stopTurningDay(emptyList()))
    }

    @Test
    fun eventTextNamesTheBatchAndTheDay() {
        val offer = CalendarOffer("Весенняя", "Курицы", 21, emptyList())
        val candling = CalendarDate(CalendarDateKind.Candling, 7, LocalDate.of(2026, 10, 7), 1)
        assertEquals("Овоскопирование №1 — Весенняя", candling.eventTitle(offer))
        assertTrue(candling.eventDescription(offer).contains("День 7 из 21"))
        assertEquals("7 октября, ср · 7-й день", candling.whenLine())
        val hatch = CalendarDate(CalendarDateKind.Hatch, 21, LocalDate.of(2026, 10, 22))
        assertEquals("22 октября, чт · срок 21 сут.", hatch.whenLine())
    }
}
