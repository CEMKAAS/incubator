package ru.zaroslikov.incubator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Разбор дат — то место, где приложение падало.
 *
 * `convertDateStringToMillis` резала строку через `split(".")` и `toInt()`, и всё, что не
 * «дд.мм.гггг», роняло форму закладки по нажатию на поле даты: пустая строка — на
 * `NumberFormatException`, «12.05» — на выходе за границы списка. Дата в базе бывает и
 * пустой (закладки до десятой версии схемы её не хранили), и чужой — база приезжает
 * файлом с другого телефона.
 *
 * Здесь пришпилены ровно те входы, на которых это случалось, и правило, ради которого
 * разбор сделан строгим.
 */
class DateUtilsTest {

    @Test
    fun `обычная дата разбирается`() {
        val date = parseDate("15.08.2026")
        val calendar = Calendar.getInstance().apply { time = date!! }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, calendar.get(Calendar.MONTH))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `однозначные день и месяц разбираются`() {
        // Разделители явные, ширина полей роли не играет: такие строки в базе есть.
        val calendar = Calendar.getInstance().apply { time = parseDate("5.8.2026")!! }
        assertEquals(5, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, calendar.get(Calendar.MONTH))
    }

    @Test
    fun `пустая строка это не дата`() {
        assertNull(parseDate(""))
        assertNull(parseDate("   "))
    }

    @Test
    fun `обрезанная строка это не дата`() {
        assertNull(parseDate("12.05"))
        assertNull(parseDate("2026"))
    }

    @Test
    fun `мусор это не дата`() {
        assertNull(parseDate("не дата"))
        assertNull(parseDate("abcdefghij"))
    }

    @Test
    fun `несуществующий день не подставляется молча`() {
        // Прежний нестрогий разбор превращал это в 1 февраля 2026 года: закладка получала
        // срок, которого никто не назначал, и никто бы об этом не узнал.
        assertNull(parseDate("32.13.2025"))
    }

    @Test
    fun `запись и чтение сходятся`() {
        val text = "01.01.2026"
        assertEquals(text, formatDate(parseDate(text)!!))
    }

    @Test
    fun `сегодня читается собственным разбором`() {
        assertEquals(today(), parseDate(todayText()))
    }

    @Test
    fun `для пикера отдаётся полночь по UTC`() {
        val millis = dateToPickerMillis("15.08.2026")!!
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        assertEquals(2026, utc.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, utc.get(Calendar.MONTH))
        assertEquals(15, utc.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, utc.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, utc.get(Calendar.MINUTE))
        assertEquals(0, utc.get(Calendar.SECOND))
        assertEquals(0, utc.get(Calendar.MILLISECOND))
    }

    @Test
    fun `неразобранная дата не даёт пикеру числа`() {
        // Вызывающий подставит сегодняшний день; раньше на этом месте было падение.
        assertNull(dateToPickerMillis(""))
        assertNull(dateToPickerMillis("12.05"))
    }

    @Test
    fun `выбранное в пикере возвращается той же датой`() {
        val text = "15.08.2026"
        assertEquals(text, pickerMillisToDate(dateToPickerMillis(text)!!))
    }

    @Test
    fun `день прибавляется через границу месяца`() {
        assertEquals("01.09.2026", formatDate(parseDate("30.08.2026")!!.plusDays(2)))
    }

    @Test
    fun `разница в днях считается по календарю`() {
        val from = parseDate("01.09.2026")!!
        val to = parseDate("21.09.2026")!!
        assertEquals(20, daysBetween(from, to))
    }
}
