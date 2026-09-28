package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.parseDate

/**
 * Цвета расхождения в таблице расписания новой закладки — чистая арифметика из
 * `ScheduleOverlap.kt`: какие дни пересекаются с соседями и как считается приговор.
 */
class ScheduleOverlapTest {

    private val celsius = TemperatureUnit.CELSIUS

    @Test
    fun `цвет есть только на днях, пока сосед ещё лежит в приборе`() {
        // Курицы заложены 1 сентября на 21 день; индюков закладывают 11 сентября —
        // курицы на одиннадцатом дне. Пересечение — дни 1…11 индюков (11…21 куриц).
        val chickens = neighbour(start = "01.09.2026", days = 21, temp = 37.8, damp = 55.0)
        val rows = schedule(days = 28, temp = "37.8", damp = "55")
        val verdicts = overlapVerdicts(rows, parseDate("11.09.2026"), listOf(chickens), celsius)

        assertEquals(28, verdicts.size)
        assertEquals(List(11) { true } + List(17) { false }, verdicts.map { it != null })
        assertEquals(Severity.Normal, verdicts[0]!!.temp)
        assertEquals(Severity.Normal, verdicts[10]!!.damp)
        assertNull(verdicts[11])
    }

    @Test
    fun `новая закладка раньше соседа получает цвет с того дня, когда сосед появится`() {
        val later = neighbour(start = "05.09.2026", days = 21, temp = 37.8, damp = 55.0)
        val rows = schedule(days = 10, temp = "37.8", damp = "55")
        val verdicts = overlapVerdicts(rows, parseDate("01.09.2026"), listOf(later), celsius)
        assertEquals(List(4) { false } + List(6) { true }, verdicts.map { it != null })
    }

    @Test
    fun `пороги те же, что у плиток замера`() {
        val n = neighbour(start = "01.09.2026", days = 21, temp = 37.8, damp = 55.0)
        val start = parseDate("01.09.2026")
        fun verdict(temp: String, damp: String) =
            overlapVerdicts(listOf(row(1, temp, damp)), start, listOf(n), celsius)[0]!!

        assertEquals(Severity.Normal, verdict("38.0", "58").temp)
        assertEquals(Severity.Normal, verdict("38.0", "58").damp)
        assertEquals(Severity.Minor, verdict("38.3", "60").temp)
        assertEquals(Severity.Minor, verdict("38.3", "60").damp)
        assertEquals(Severity.Major, verdict("38.4", "63").temp)
        assertEquals(Severity.Major, verdict("38.4", "63").damp)
        assertEquals(Severity.Major, verdict("37.2", "47").temp)
        assertEquals(Severity.Major, verdict("37.2", "47").damp)
    }

    @Test
    fun `цель — среднее по соседям, и сосед без нормы её не тянет`() {
        val a = neighbour(start = "01.09.2026", days = 21, temp = 37.6, damp = 50.0)
        val b = neighbour(start = "01.09.2026", days = 21, temp = 38.0, damp = null)
        val verdicts = overlapVerdicts(
            listOf(row(1, "37.8", "50")), parseDate("01.09.2026"), listOf(a, b), celsius,
        )
        val v = verdicts[0]!!
        assertEquals(2, v.neighbours)
        assertEquals(Severity.Normal, v.temp)
        assertEquals(Severity.Normal, v.damp)
    }

    @Test
    fun `пустая клетка и сосед без нормы дают отсутствие приговора, а не норму`() {
        val n = neighbour(start = "01.09.2026", days = 21, temp = null, damp = 55.0)
        val verdicts = overlapVerdicts(
            listOf(row(1, "37.8", "")), parseDate("01.09.2026"), listOf(n), celsius,
        )
        val v = verdicts[0]!!
        assertNull(v.temp)
        assertNull(v.damp)
        assertEquals(1, v.neighbours)
    }

    @Test
    fun `температура строки переводится из градусов экрана`() {
        val n = neighbour(start = "01.09.2026", days = 21, temp = 37.8, damp = 55.0)
        // 100.04 °F — ровно 37.8 °C; 101.3 °F — 38.5 °C, заметное расхождение.
        val ok = overlapVerdicts(
            listOf(row(1, "100.04", "55")), parseDate("01.09.2026"), listOf(n), TemperatureUnit.FAHRENHEIT,
        )[0]!!
        val far = overlapVerdicts(
            listOf(row(1, "101.3", "55")), parseDate("01.09.2026"), listOf(n), TemperatureUnit.FAHRENHEIT,
        )[0]!!
        assertEquals(Severity.Normal, ok.temp)
        assertEquals(Severity.Major, far.temp)
    }

    @Test
    fun `без даты, без соседей и с нечитаемой датой соседа приговоров нет`() {
        val n = neighbour(start = "01.09.2026", days = 21, temp = 37.8, damp = 55.0)
        val rows = schedule(days = 3, temp = "37.8", damp = "55")
        assertEquals(listOf(null, null, null), overlapVerdicts(rows, null, listOf(n), celsius))
        assertEquals(
            listOf(null, null, null),
            overlapVerdicts(rows, parseDate("01.09.2026"), emptyList(), celsius),
        )
        val broken = neighbour(start = "", days = 21, temp = 37.8, damp = 55.0)
        assertEquals(
            listOf(null, null, null),
            overlapVerdicts(rows, parseDate("01.09.2026"), listOf(broken), celsius),
        )
    }

    @Test
    fun `сосед, которого уже вынули, выпадает из цели, а оставшийся считается один`() {
        // Ранний сосед кончается на 21.09, поздний — на 05.10. С 22.09 цель — только поздний.
        val early = neighbour(start = "01.09.2026", days = 21, temp = 37.0, damp = 50.0)
        val late = neighbour(start = "15.09.2026", days = 21, temp = 38.0, damp = 60.0)
        val rows = schedule(days = 3, temp = "38.0", damp = "60")
        val verdicts = overlapVerdicts(rows, parseDate("20.09.2026"), listOf(early, late), celsius)
        assertEquals(listOf(2, 2, 1), verdicts.map { it!!.neighbours })
        // 20–21.09: среднее 37.5 / 55 — ровно полградуса и пять процентов от 38.0 / 60,
        // верхняя граница «допустимо».
        assertEquals(Severity.Minor, verdicts[0]!!.temp)
        assertEquals(Severity.Minor, verdicts[0]!!.damp)
        // 22.09: только поздний — совпадает.
        assertEquals(Severity.Normal, verdicts[2]!!.temp)
        assertEquals(Severity.Normal, verdicts[2]!!.damp)
    }

    @Test
    fun `день с соседом без единой нормы имеет приговор, но не заливку`() {
        val n = neighbour(start = "01.09.2026", days = 21, temp = null, damp = null)
        val v = overlapVerdicts(listOf(row(1, "37.8", "55")), parseDate("01.09.2026"), listOf(n), celsius)[0]!!
        assertEquals(false, v.tinted)
        assertNull(Severity.Normal.let { (null as Severity?).overlapDescription(1) })
    }

    @Test
    fun `описание для скринридера склоняет закладки`() {
        assertEquals("совпадает с 1 закладкой", Severity.Normal.overlapDescription(1))
        assertEquals("допустимое расхождение с 2 закладками", Severity.Minor.overlapDescription(2))
        assertEquals("расходится с 11 закладками", Severity.Major.overlapDescription(11))
        assertEquals("расходится с 21 закладкой", Severity.Major.overlapDescription(21))
    }

    // --- Заготовки ---

    private fun row(day: Int, temp: String, damp: String) =
        ValueUiState(day = day, temp = temp, damp = damp)

    private fun schedule(days: Int, temp: String, damp: String) =
        (1..days).map { row(it, temp, damp) }

    private fun neighbour(start: String, days: Int, temp: Double?, damp: Double?) =
        NeighbourSchedule(
            batch = Batch(
                id = 1,
                title = "Соседи",
                type = "Курицы",
                data = start,
                eggAll = 10,
                eggAllEND = 0,
                airing = "false",
                over = "false",
                arhive = "0",
                dateEnd = "",
                note = "",
                incubatorId = 1,
            ),
            rows = (1..days).map { day ->
                Value(
                    id = day.toLong(),
                    day = day,
                    temp = temp,
                    damp = damp,
                    over = 3,
                    airingCount = 1,
                    airingTime = 10,
                    note = "",
                    idPT = 1,
                )
            },
        )
}
