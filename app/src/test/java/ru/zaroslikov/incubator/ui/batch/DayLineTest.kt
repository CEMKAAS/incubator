package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Закрепляет строку дня в сводке закладки ([dayLine]): остаток срока от момента вывода —
 * в днях, пока их есть хотя бы один целый, в часах в последние сутки, — округление,
 * «срок вышел» позади момента и отсутствие хвоста у завершённой.
 */
class DayLineTest {

    private val now = at(2026, Calendar.SEPTEMBER, 16, 19, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Calendar.getInstance().let {
            it.clear()
            it.set(year, month, day, hour, minute, 0)
            it.time
        }

    private fun running(finishesAt: Date?) = BatchDetailUiState(
        day = 7,
        totalDays = 21,
        finishesAt = finishesAt,
        loaded = true,
    )

    @Test
    fun `a day or more left is counted in whole days`() {
        val state = running(at(2026, Calendar.SEPTEMBER, 30, 8, 0))
        // 13 суток и 13 часов — тринадцать дней, не четырнадцать.
        assertEquals("День 7/21 · осталось 13 дней", dayLine(state, now))
    }

    @Test
    fun `exactly a day left is still a day`() {
        val state = running(at(2026, Calendar.SEPTEMBER, 17, 19, 0))
        assertEquals("День 7/21 · остался 1 день", dayLine(state, now))
    }

    @Test
    fun `less than a day left is counted in hours`() {
        val state = running(at(2026, Calendar.SEPTEMBER, 17, 18, 0))
        assertEquals("День 7/21 · осталось 23 часа", dayLine(state, now))
        assertEquals("День 7/21 · осталось 5 часов", dayLine(running(at(2026, Calendar.SEPTEMBER, 17, 0, 0)), now))
    }

    @Test
    fun `a started hour still counts as a whole one`() {
        // Пять минут до срока — ещё час, а не ноль: округление вверх.
        val state = running(at(2026, Calendar.SEPTEMBER, 16, 19, 5))
        assertEquals("День 7/21 · остался 1 час", dayLine(state, now))
        assertEquals(1L, hoursLeft(state.finishesAt!!, now))
    }

    @Test
    fun `a passed finish moment reads as term over`() {
        val state = running(at(2026, Calendar.SEPTEMBER, 16, 18, 59))
        assertEquals("День 7/21 · срок вышел", dayLine(state, now))
        assertEquals(0L, hoursLeft(state.finishesAt!!, now))
    }

    @Test
    fun `a finished batch has no tail`() {
        val state = running(at(2026, Calendar.SEPTEMBER, 30, 8, 0)).copy(finished = true)
        assertEquals("День 7/21", dayLine(state, now))
    }

    @Test
    fun `an unknown finish moment has no tail either`() {
        assertEquals("День 7/21", dayLine(running(null), now))
        assertEquals("День 7", dayLine(running(null).copy(totalDays = null), now))
    }

    @Test
    fun `nothing is printed before the batch is loaded`() {
        assertEquals("", dayLine(BatchDetailUiState(), now))
    }

    @Test
    fun `days and hours switch at twenty four hours`() {
        assertEquals("срок вышел", timeLeftPhrase(0))
        assertEquals("остался 1 час", timeLeftPhrase(1))
        assertEquals("осталось 23 часа", timeLeftPhrase(23))
        assertEquals("остался 1 день", timeLeftPhrase(24))
        assertEquals("остался 1 день", timeLeftPhrase(47))
        assertEquals("осталось 2 дня", timeLeftPhrase(48))
        assertEquals("остался 21 день", timeLeftPhrase(21 * 24))
    }

    @Test
    fun `the verb agrees with the number`() {
        assertEquals("остался 1 час", hoursLeftPhrase(1))
        assertEquals("осталось 2 часа", hoursLeftPhrase(2))
        assertEquals("осталось 11 часов", hoursLeftPhrase(11))
        assertEquals("остался 21 час", hoursLeftPhrase(21))
        assertEquals("осталось 5 дней", daysLeftPhrase(5))
        assertEquals("осталось 11 дней", daysLeftPhrase(11))
        assertEquals("осталось 14 дней", daysLeftPhrase(14))
    }

    @Test
    fun `hours are declined`() {
        assertEquals("1 час", hoursWord(1))
        assertEquals("2 часа", hoursWord(2))
        assertEquals("5 часов", hoursWord(5))
        assertEquals("11 часов", hoursWord(11))
        assertEquals("21 час", hoursWord(21))
        assertEquals("24 часа", hoursWord(24))
        assertEquals("111 часов", hoursWord(111))
    }
}
