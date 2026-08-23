package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value

/**
 * Счёт «Аналитики за день»: график, девять плиток и текст-вывод.
 *
 * Тест держится в `:app` рядом с самим `BatchAnalytics.kt`; андроидного там ничего нет,
 * и `gradlew :app:test` гоняет его на JVM без эмулятора — как и `ValueFormatTest`.
 */
class BatchAnalyticsTest {

    private fun plan(temp: Double? = 37.8, damp: Double? = 55.0) = Value(
        id = 1, day = 15, temp = temp, damp = damp,
        over = "4-6", airing = "2 раза по 5 минут", note = "", idPT = 1,
    )

    private fun measurement(
        id: Long,
        time: String,
        temp: Double? = null,
        damp: Double? = null,
        over: String = "",
        airing: String = "",
        note: String = "",
    ) = Measurement(id, 1, time, temp, damp, over, airing, note)

    // --- Точки графика ---

    @Test
    fun `замеры разворачиваются по времени слева направо`() {
        // Из базы они приходят свежими сверху — на графике порядок обратный.
        val analytics = analyticsOf(
            listOf(
                measurement(3, "18:30", temp = 37.0),
                measurement(2, "12:00", temp = 37.5),
                measurement(1, "08:00", temp = 36.0),
            ),
            plan(),
        )
        assertEquals(listOf("08:00", "12:00", "18:30"), analytics.points.map { it.time })
        assertEquals(listOf(480, 720, 1110), analytics.points.map { it.minutes })
    }

    @Test
    fun `замер без показаний в график не попадает, но день из него считается`() {
        val analytics = analyticsOf(
            listOf(
                measurement(2, "10:00", note = "Долил воды"),
                measurement(1, "08:00", temp = 37.8),
            ),
            plan(),
        )
        assertEquals(listOf("08:00"), analytics.points.map { it.time })
        assertEquals(2, analytics.total)
    }

    @Test
    fun `замер с нечитаемым временем не ломает график`() {
        val analytics = analyticsOf(
            listOf(measurement(1, "", temp = 37.0), measurement(2, "09:00", temp = 37.4)),
            plan(),
        )
        assertEquals(listOf("09:00"), analytics.points.map { it.time })
        // …но в средней температуре он всё равно участвует: показание-то записано.
        assertEquals(2, analytics.total)
        assertEquals(37.2, analytics.tempAvg!!, 1e-9)
    }

    // --- События ---

    @Test
    fun `переворот и заметка — события без длительности, проветривание — с длительностью`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "09:00", temp = 37.8, over = "1", airing = "15", note = "Открыл"),
            ),
            plan(),
        )
        val byKind = analytics.events.associateBy { it.kind }
        assertEquals(0, byKind.getValue(ChartEventKind.Turn).durationMinutes)
        assertEquals(0, byKind.getValue(ChartEventKind.Note).durationMinutes)
        assertEquals(15, byKind.getValue(ChartEventKind.Airing).durationMinutes)
        // Все три пришлись на одну минуту — и все три остались отдельными событиями.
        assertEquals(3, analytics.events.size)
        assertTrue(analytics.events.all { it.minutes == 540 })
    }

    @Test
    fun `проветривание без числа минут остаётся событием нулевой длительности`() {
        val analytics = analyticsOf(listOf(measurement(1, "09:00", airing = "Авто")), plan())
        assertEquals(ChartEventKind.Airing, analytics.events.single().kind)
        assertEquals(0, analytics.events.single().durationMinutes)
        assertEquals(1, analytics.airings)
    }

    // --- Плитки ---

    @Test
    fun `средняя, крайние и разброс температуры`() {
        val analytics = analyticsOf(
            listOf(
                measurement(3, "18:00", temp = 37.0),
                measurement(2, "12:00", temp = 36.0),
                measurement(1, "08:00", temp = 36.0),
            ),
            plan(),
        )
        assertEquals(3, analytics.total)
        assertEquals(36.333, analytics.tempAvg!!, 0.001)
        assertEquals(36.0, analytics.tempMin!!, 1e-9)
        assertEquals(37.0, analytics.tempMax!!, 1e-9)
        assertEquals(1.0, analytics.tempSpread!!, 1e-9)
    }

    @Test
    fun `в цель попадают замеры в пределах трёх десятых от плана`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", temp = 37.8),  // ровно цель
                measurement(2, "10:00", temp = 38.1),  // граница ±0.3
                measurement(3, "12:00", temp = 38.2),  // мимо
                measurement(4, "14:00", temp = 36.0),  // мимо
            ),
            plan(temp = 37.8),
        )
        assertEquals(50, analytics.inTargetPercent)
    }

    @Test
    fun `без плана доля попаданий не считается, а не обнуляется`() {
        val analytics = analyticsOf(listOf(measurement(1, "08:00", temp = 37.8)), plan(temp = null))
        assertNull(analytics.inTargetPercent)
    }

    @Test
    fun `перевороты суммируются, проветривания считаются замерами`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", over = "1", airing = "10"),
                measurement(2, "12:00", over = "1"),
                // Замер прежних версий: в поле лежало число переворотов, а не отметка.
                measurement(3, "18:00", over = "3", airing = "5"),
            ),
            plan(),
        )
        assertEquals(5, analytics.turns)
        assertEquals(2, analytics.airings)
    }

    @Test
    fun `пустой день не считает ничего, но цель запоминает`() {
        val analytics = analyticsOf(emptyList(), plan())
        assertEquals(0, analytics.total)
        assertNull(analytics.tempAvg)
        assertEquals(37.8, analytics.tempTarget!!, 1e-9)
        assertTrue(!analytics.hasChart)
    }

    // --- Ось ---

    @Test
    fun `ось округляет края до круглого шага и всегда накрывает данные`() {
        val axis = niceAxis(36.0, 37.0, TEMP_AXIS_STEPS)
        assertEquals(0.25, axis.step, 1e-9)
        assertEquals(36.0, axis.lo, 1e-9)
        assertEquals(37.0, axis.hi, 1e-9)
        assertEquals(AXIS_TICKS, axis.ticks.size)
    }

    @Test
    fun `округление низа вниз не выталкивает максимум за верх оси`() {
        // 37.1…37.9: размаха 0.8 хватило бы на шаг 0.2, но низ округляется до 37.0,
        // и верх встал бы на 37.8 — максимум рисовался бы над сеткой.
        val axis = niceAxis(37.1, 37.9, TEMP_AXIS_STEPS)
        assertTrue("низ $axis", axis.lo <= 37.1)
        assertTrue("верх $axis", axis.hi >= 37.9)
        assertTrue("доля в пределах шкалы", axis.fraction(37.9) <= 1f)
    }

    @Test
    fun `любой набор замеров укладывается в свою ось`() {
        val values = listOf(0.0, 0.05, 1.0, 36.6, 37.05, 99.9, 250.0)
        for (lowIndex in values.indices) {
            for (highIndex in lowIndex until values.size) {
                val low = values[lowIndex]
                val high = values[highIndex]
                listOf(TEMP_AXIS_STEPS, DAMP_AXIS_STEPS).forEach { steps ->
                    val axis = niceAxis(low, high, steps)
                    assertTrue("$low..$high $axis", axis.lo <= low + 1e-9)
                    assertTrue("$low..$high $axis", axis.hi >= high - 1e-9)
                }
            }
        }
    }

    @Test
    fun `единственный замер раздвигает ось вокруг себя`() {
        val axis = niceAxis(37.5, 37.5, TEMP_AXIS_STEPS)
        assertTrue("низ ниже замера", axis.lo < 37.5)
        assertTrue("верх выше замера", axis.hi > 37.5)
    }

    @Test
    fun `широкий разброс переходит на крупный шаг`() {
        val axis = niceAxis(30.0, 90.0, DAMP_AXIS_STEPS)
        assertEquals(20.0, axis.step, 1e-9)
        assertTrue(axis.lo <= 30.0 && axis.hi >= 90.0)
    }

    @Test
    fun `доля по оси — ноль внизу и единица вверху`() {
        val axis = ChartAxis(lo = 36.0, step = 0.5)
        assertEquals(0f, axis.fraction(36.0), 1e-6f)
        assertEquals(1f, axis.fraction(38.0), 1e-6f)
        assertEquals(0.5f, axis.fraction(37.0), 1e-6f)
    }

    // --- Вывод ---

    @Test
    fun `вывод называет сторону отклонения и разброс`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", temp = 36.0),
                measurement(2, "12:00", temp = 37.0),
                measurement(3, "18:00", temp = 36.0),
            ),
            plan(temp = 37.8, damp = null),
        )
        val text = insightParts(analytics).joinToString("") { it.text }
        assertTrue(text, text.startsWith("Средняя температура ниже цели на 1.5°."))
        assertTrue(text, text.contains("Разброс большой"))
    }

    @Test
    fun `ровный день не выдумывает отклонения`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", temp = 37.8),
                measurement(2, "12:00", temp = 37.9),
            ),
            plan(temp = 37.8, damp = null),
        )
        val text = insightParts(analytics).joinToString("") { it.text }
        assertTrue(text, text.contains("в цели"))
        assertTrue(text, text.contains("Разброс небольшой"))
    }

    @Test
    fun `влажность попадает в вывод только при заметном расхождении`() {
        val near = analyticsOf(
            listOf(measurement(1, "08:00", temp = 37.8, damp = 57.0)),
            plan(temp = 37.8, damp = 55.0),
        )
        assertTrue(insightParts(near).none { it.text.contains("Влажность") })

        val far = analyticsOf(
            listOf(measurement(1, "08:00", temp = 37.8, damp = 45.0)),
            plan(temp = 37.8, damp = 55.0),
        )
        val text = insightParts(far).joinToString("") { it.text }
        assertTrue(text, text.contains("Влажность ниже цели на 10%."))
    }

    @Test
    fun `день без замеров и день без температуры получают свои тексты`() {
        val empty = insightParts(analyticsOf(emptyList(), plan())).joinToString("") { it.text }
        assertTrue(empty, empty.contains("Замеров за сегодня ещё нет"))

        val noTemp = analyticsOf(listOf(measurement(1, "08:00", damp = 55.0)), plan())
        val text = insightParts(noTemp).joinToString("") { it.text }
        assertTrue(text, text.contains("Температуру сегодня не записывали"))
    }
}
