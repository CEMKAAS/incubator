package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value

/**
 * Счёт «Аналитики за день»: график, двенадцать плиток и текст-вывод.
 *
 * Тест держится в `:app` рядом с самим `BatchAnalytics.kt`; андроидного там ничего нет,
 * и `gradlew :app:test` гоняет его на JVM без эмулятора — как и `ValueFormatTest`.
 */
class BatchAnalyticsTest {

    private fun plan(temp: Double? = 37.8, damp: Double? = 55.0) = Value(
        id = 1, day = 15, temp = temp, damp = damp,
        over = 6, airingCount = 2, airingTime = 5, note = "", idPT = 1,
    )

    private fun measurement(
        id: Long,
        time: String,
        temp: Double? = null,
        damp: Double? = null,
        over: Int? = null,
        airingCount: Int? = null,
        airingTime: Int? = null,
        note: String = "",
    ) = Measurement(id, 1, time, temp, damp, over, airingCount, airingTime, note)

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
                measurement(
                    1, "09:00", temp = 37.8,
                    over = 1, airingCount = 1, airingTime = 15, note = "Открыл",
                ),
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
    fun `область проветривания — всё время, что инкубатор был открыт`() {
        // Два проветривания по пять минут: закрашивается десять, а не пять.
        val analytics = analyticsOf(
            listOf(measurement(1, "09:00", airingCount = 2, airingTime = 5)),
            plan(),
        )
        assertEquals(ChartEventKind.Airing, analytics.events.single().kind)
        assertEquals(10, analytics.events.single().durationMinutes)
        assertEquals(2, analytics.airings)
        assertEquals(10, analytics.airingMinutes)
    }

    @Test
    fun `проветривание без числа минут остаётся событием нулевой длительности`() {
        val analytics = analyticsOf(listOf(measurement(1, "09:00", airingCount = 1)), plan())
        assertEquals(ChartEventKind.Airing, analytics.events.single().kind)
        assertEquals(0, analytics.events.single().durationMinutes)
        assertEquals(1, analytics.airings)
        // Проветривание было, а сколько оно длилось — замер не сохранил.
        assertEquals(0, analytics.airingMinutes)
    }

    @Test
    fun `нулевой счётчик события не создаёт`() {
        // Ноль переворотов — это записанное «не переворачивал», а не переворот.
        val analytics = analyticsOf(
            listOf(measurement(1, "09:00", temp = 37.5, over = 0, airingCount = 0)),
            plan(),
        )
        assertTrue(analytics.events.isEmpty())
        assertEquals(0, analytics.turns)
        assertEquals(0, analytics.airings)
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
    fun `влажность считается тем же набором, что и температура`() {
        val analytics = analyticsOf(
            listOf(
                measurement(3, "18:00", damp = 60.0),
                measurement(2, "12:00", damp = 50.0),
                measurement(1, "08:00", damp = 55.0),
            ),
            plan(),
        )
        assertEquals(55.0, analytics.dampAvg!!, 1e-9)
        assertEquals(50.0, analytics.dampMin!!, 1e-9)
        assertEquals(60.0, analytics.dampMax!!, 1e-9)
        assertEquals(10.0, analytics.dampSpread!!, 1e-9)
        // Температуры в этих замерах не было — крайние по ней остаются несчитанными.
        assertNull(analytics.tempMin)
        assertNull(analytics.tempSpread)
    }

    @Test
    fun `перевороты и проветривания складываются по замерам`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", over = 1, airingCount = 1, airingTime = 10),
                measurement(2, "12:00", over = 1),
                // За один заход перевернули трижды — замер это и записывает.
                measurement(3, "18:00", over = 3, airingCount = 1, airingTime = 5),
            ),
            plan(),
        )
        assertEquals(5, analytics.turns)
        assertEquals(2, analytics.airings)
        assertEquals(15, analytics.airingMinutes)
    }

    @Test
    fun `минуты за день — разы на длительность, а не сумма длительностей`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", airingCount = 2, airingTime = 15),
                measurement(2, "18:00", airingCount = 1, airingTime = 20),
            ),
            plan(),
        )
        assertEquals(3, analytics.airings)
        assertEquals(50, analytics.airingMinutes)
    }

    @Test
    fun `счётчики дня считаются и без графика — те же числа, что в аналитике`() {
        val measurements = listOf(
            measurement(1, "08:00", over = 1, airingCount = 1, airingTime = 10),
            measurement(2, "нет времени", over = 1, airingCount = 1, airingTime = 5),
        )
        val counts = actionCountsOf(measurements)
        assertEquals(2, counts.turns)
        assertEquals(2, counts.airings)
        assertEquals(15, counts.airingMinutes)
        // Аналитика считает их тем же кодом — расхождению взяться неоткуда.
        val analytics = analyticsOf(measurements, plan())
        assertEquals(counts.turns, analytics.turns)
        assertEquals(counts.airings, analytics.airings)
        assertEquals(counts.airingMinutes, analytics.airingMinutes)
    }

    @Test
    fun `незаполненный счётчик замера в сумму не идёт`() {
        val counts = actionCountsOf(
            listOf(
                measurement(1, "08:00", temp = 37.5),
                measurement(2, "12:00", over = 2),
            )
        )
        assertEquals(2, counts.turns)
        assertEquals(0, counts.airings)
        assertEquals(0, counts.airingMinutes)
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

    // --- Режим по среднему факту ---

    private fun day(
        id: Long,
        day: Int,
        temp: Double? = 37.8,
        damp: Double? = 55.0,
        over: Int? = 6,
        airingCount: Int? = 2,
        airingTime: Int? = 5,
    ) = Value(id, day, temp, damp, over, airingCount, airingTime, "план", 1)

    private fun fact(
        id: Long,
        dayId: Long,
        time: String,
        temp: Double? = null,
        damp: Double? = null,
        over: Int? = null,
        airingCount: Int? = null,
        airingTime: Int? = null,
    ) = Measurement(id, dayId, time, temp, damp, over, airingCount, airingTime, "")

    @Test
    fun `день без замеров остаётся планом`() {
        val plan = day(id = 7, day = 3)
        assertEquals(plan, averagedDayOf(plan, emptyList()))
    }

    @Test
    fun `температура округляется до десятой, влажность до целого`() {
        val averaged = averagedDayOf(
            day(id = 7, day = 3),
            listOf(
                fact(1, 7, "08:00", temp = 37.4, damp = 54.0),
                fact(2, 7, "14:00", temp = 37.5, damp = 55.0),
                fact(3, 7, "20:00", temp = 37.7, damp = 58.0),
            ),
        )
        // 37.533… и 55.666…: у среднего арифметического знаков всегда больше, чем смысла.
        assertEquals(37.5, averaged.temp!!, 1e-9)
        assertEquals(56.0, averaged.damp!!, 1e-9)
    }

    @Test
    fun `перевороты складываются, а минуты проветривания усредняются на один раз`() {
        val averaged = averagedDayOf(
            day(id = 7, day = 3),
            listOf(
                fact(1, 7, "08:00", over = 2, airingCount = 1, airingTime = 10),
                fact(2, 7, "20:00", over = 3, airingCount = 1, airingTime = 20),
            ),
        )
        assertEquals(5, averaged.over)
        // Два проветривания на тридцать минут — по пятнадцать каждое: airingTime это
        // длительность одного, а не всех.
        assertEquals(2, averaged.airingCount)
        assertEquals(15, averaged.airingTime)
    }

    @Test
    fun `незаписанный столбец остаётся планом, а не нулём`() {
        // В замерах есть только температура: ноль переворотов означал бы норму
        // «не переворачивать», которой никто не задавал.
        val averaged = averagedDayOf(
            day(id = 7, day = 3, over = 6, airingCount = 2, airingTime = 5),
            listOf(fact(1, 7, "08:00", temp = 37.0)),
        )
        assertEquals(6, averaged.over)
        assertEquals(2, averaged.airingCount)
        assertEquals(5, averaged.airingTime)
        assertEquals(55.0, averaged.damp!!, 1e-9)
        assertEquals(37.0, averaged.temp!!, 1e-9)
    }

    @Test
    fun `записанный ноль переворотов планом не подменяется`() {
        val averaged = averagedDayOf(
            day(id = 7, day = 3, over = 6),
            listOf(fact(1, 7, "08:00", over = 0)),
        )
        assertEquals(0, averaged.over)
    }

    @Test
    fun `замеры раскладываются по дням, порядок дней сохраняется`() {
        val plan = listOf(day(id = 7, day = 1), day(id = 8, day = 2), day(id = 9, day = 3))
        val averaged = averagedScheduleOf(
            plan,
            listOf(
                fact(2, 9, "08:00", temp = 36.0),
                fact(1, 7, "08:00", temp = 38.0),
            ),
        )
        assertEquals(listOf(1, 2, 3), averaged.map { it.day })
        assertEquals(38.0, averaged[0].temp!!, 1e-9)
        // Второй день замеров не видел — остался планом.
        assertEquals(37.8, averaged[1].temp!!, 1e-9)
        assertEquals(36.0, averaged[2].temp!!, 1e-9)
    }

    @Test
    fun `план не мутируется усреднением`() {
        val plan = day(id = 7, day = 3)
        averagedDayOf(plan, listOf(fact(1, 7, "08:00", temp = 30.0)))
        assertEquals(37.8, plan.temp!!, 1e-9)
    }

    // --- Итог по замерам за всю закладку ---

    /** Три дня плана с разной целью по температуре — на них и проверяется отклонение. */
    private val threeDays = listOf(
        day(id = 1, day = 1, temp = 37.0, damp = 50.0),
        day(id = 2, day = 2, temp = 38.0, damp = 60.0),
        day(id = 3, day = 3, temp = 39.0, damp = 70.0),
    )

    private fun totalsOf(vararg facts: Measurement, daysRun: Int = 3) =
        batchTotals(threeDays, facts.groupBy { it.idValue }, daysRun)

    @Test
    fun `без замеров итог пуст, но знаменатель охвата известен`() {
        val totals = batchTotals(threeDays, emptyMap(), daysRun = 3)
        assertTrue(totals.isEmpty)
        assertEquals(0, totals.daysMeasured)
        assertEquals(3, totals.daysRun)
        assertNull(totals.tempAvg)
        assertNull(totals.tempOffPlan)
    }

    @Test
    fun `охват считает дни с замерами, а не сами замеры`() {
        val totals = totalsOf(
            fact(1, 1, "08:00", temp = 37.0),
            fact(2, 1, "20:00", temp = 37.0),
            fact(3, 3, "08:00", temp = 39.0),
        )
        assertEquals(3, totals.measurements)
        assertEquals(2, totals.daysMeasured)
        assertEquals(3, totals.daysRun)
    }

    @Test
    fun `знаменатель охвата — дни, которые закладка прошла, а не длина расписания`() {
        // Прервали на втором дне: третий не наступил и в «из скольких» не входит.
        val totals = batchTotals(
            threeDays,
            mapOf(1L to listOf(fact(1, 1, "08:00", temp = 37.0))),
            daysRun = 2,
        )
        assertEquals(2, totals.daysRun)
        assertEquals(1, totals.daysMeasured)
    }

    @Test
    fun `замеры дней после остановки в итог не попадают`() {
        // Строка третьего дня в расписании есть, замер к ней как-то привязан — но
        // закладка до него не дошла, и в сводке его быть не должно.
        val totals = batchTotals(
            threeDays,
            mapOf(
                1L to listOf(fact(1, 1, "08:00", temp = 37.0)),
                3L to listOf(fact(2, 3, "08:00", temp = 39.0)),
            ),
            daysRun = 2,
        )
        assertEquals(1, totals.measurements)
        assertEquals(37.0, totals.tempAvg!!, 1e-9)
    }

    @Test
    fun `отклонение считается от плана своего дня, а не от общей цели`() {
        // Каждый замер ровно на полградуса выше своего дня, хотя сами цели разные.
        val totals = totalsOf(
            fact(1, 1, "08:00", temp = 37.5),
            fact(2, 2, "08:00", temp = 38.5),
            fact(3, 3, "08:00", temp = 39.5),
        )
        assertEquals(38.5, totals.tempAvg!!, 1e-9)
        assertEquals(0.5, totals.tempOffPlan!!, 1e-9)
    }

    @Test
    fun `день без цели в отклонение не входит`() {
        val plan = listOf(
            day(id = 1, day = 1, temp = 37.0),
            day(id = 2, day = 2, temp = null),
        )
        val totals = batchTotals(
            plan,
            mapOf(
                1L to listOf(fact(1, 1, "08:00", temp = 38.0)),
                // Этому замеру не с чем сравниваться: вычесть не из чего.
                2L to listOf(fact(2, 2, "08:00", temp = 30.0)),
            ),
            daysRun = 2,
        )
        assertEquals(34.0, totals.tempAvg!!, 1e-9)
        assertEquals(1.0, totals.tempOffPlan!!, 1e-9)
    }

    @Test
    fun `отклонение — среднее по замерам, а не по дням`() {
        // Первый день измеряли трижды и держали ровно, второй — раз и на градус выше.
        // Среднее по дням дало бы 0.5; среднее по замерам — 0.25.
        val totals = totalsOf(
            fact(1, 1, "08:00", temp = 37.0),
            fact(2, 1, "12:00", temp = 37.0),
            fact(3, 1, "20:00", temp = 37.0),
            fact(4, 2, "08:00", temp = 39.0),
        )
        assertEquals(0.25, totals.tempOffPlan!!, 1e-9)
    }

    @Test
    fun `крайние и разброс берутся по всей закладке`() {
        val totals = totalsOf(
            fact(1, 1, "08:00", temp = 36.4, damp = 48.0),
            fact(2, 2, "08:00", temp = 38.9, damp = 71.0),
        )
        assertEquals(36.4, totals.tempMin!!, 1e-9)
        assertEquals(38.9, totals.tempMax!!, 1e-9)
        assertEquals(2.5, totals.tempSpread!!, 1e-9)
        assertEquals(23.0, totals.dampSpread!!, 1e-9)
    }

    @Test
    fun `счётчики складываются по всем дням`() {
        val totals = totalsOf(
            fact(1, 1, "08:00", over = 4, airingCount = 1, airingTime = 5),
            fact(2, 2, "08:00", over = 6, airingCount = 2, airingTime = 10),
        )
        assertEquals(10, totals.turns)
        assertEquals(3, totals.airings)
        // Минуты — сумма произведений: длительность хранится на одно проветривание.
        assertEquals(25, totals.airingMinutes)
    }

    @Test
    fun `вывод называет отклонение по обеим величинам`() {
        val totals = totalsOf(fact(1, 2, "08:00", temp = 38.6, damp = 55.0))
        val text = totalsInsightParts(totals).joinToString("") { it.text }
        assertEquals(
            "За всю инкубацию температуру держали выше плана на 0.6°, " +
                "влажность — ниже плана на 5%.",
            text,
        )
    }

    @Test
    fun `отклонение в пределах допуска называется планом`() {
        // 0.1° и 2% — те же пороги, по которым сводка дня говорит «в цели».
        val totals = totalsOf(fact(1, 2, "08:00", temp = 38.1, damp = 58.0))
        val text = totalsInsightParts(totals).joinToString("") { it.text }
        assertEquals(
            "За всю инкубацию температуру держали по плану, влажность — по плану.",
            text,
        )
    }

    @Test
    fun `вывод об одной величине не поминает вторую`() {
        val totals = totalsOf(fact(1, 2, "08:00", damp = 66.0))
        val text = totalsInsightParts(totals).joinToString("") { it.text }
        assertEquals("За всю инкубацию влажность держали выше плана на 6%.", text)
    }

    @Test
    fun `без замеров и без целей выводу сказать нечего`() {
        assertTrue(totalsInsightParts(batchTotals(threeDays, emptyMap(), 3)).isEmpty())

        val noTargets = batchTotals(
            listOf(day(id = 1, day = 1, temp = null, damp = null)),
            mapOf(1L to listOf(fact(1, 1, "08:00", temp = 37.0))),
            daysRun = 1,
        )
        assertTrue(totalsInsightParts(noTargets).isEmpty())
    }

    // --- Заметки, планы закладок, подписи времени ---

    @Test
    fun `событие заметки несёт её текст, обрезанный по краям`() {
        val analytics = analyticsOf(
            listOf(measurement(1, "08:00", temp = 37.8, note = "  Долил воды  ")),
            plan(),
        )
        val note = analytics.notes.single()
        assertEquals("08:00", note.time)
        assertEquals("Долил воды", note.note)
        // Переворот и проветривание текста заметки не носят.
        val turn = analyticsOf(listOf(measurement(1, "08:00", temp = 37.8, over = 1)), plan())
            .events.single()
        assertEquals("", turn.note)
    }

    @Test
    fun `планы закладок проходят на график, пустые отсеиваются`() {
        val lines = listOf(
            ChartPlanLine("Курицы", 37.8, 55.0),
            ChartPlanLine("Утки", null, null),
            ChartPlanLine("Гуси", 37.5, null),
        )
        val withPoints = analyticsOf(listOf(measurement(1, "08:00", temp = 37.8)), plan(), lines)
        assertEquals(listOf("Курицы", "Гуси"), withPoints.planLines.map { it.label })
        // Без единого замера графика нет — и линий плана тоже: обещать нечего.
        assertTrue(analyticsOf(emptyList(), plan(), lines).planLines.isEmpty())
        // Ось есть только у измеренной величины: в день одной влажности план по
        // температуре рисовать не на чем, и в легенду он не попадает.
        val dampOnly = analyticsOf(listOf(measurement(1, "08:00", damp = 55.0)), plan(), lines)
        assertEquals(listOf("Курицы"), dampOnly.planLines.map { it.label })
        // По умолчанию линий нет — аналитика одной закладки.
        assertTrue(analyticsOf(listOf(measurement(1, "08:00", temp = 37.8)), plan()).planLines.isEmpty())
    }

    @Test
    fun `подписи времени прореживаются по ширине, первая и последняя остаются`() {
        // Три точки, широкий график: все три подписи.
        assertEquals(listOf(0, 1, 2), timeLabelIndices(listOf(0f, 150f, 300f), minGap = 50f))
        // Две точки почти в одной минуте: вторая уступает место последней.
        assertEquals(listOf(0, 2), timeLabelIndices(listOf(0f, 20f, 300f), minGap = 50f))
        // Последняя слишком близко к предыдущей выбранной — та вытесняется.
        assertEquals(listOf(0, 1, 3), timeLabelIndices(listOf(0f, 100f, 200f, 220f), minGap = 50f))
        // Все точки в одной минуте: одна подпись, не две наложенные.
        assertEquals(listOf(0), timeLabelIndices(listOf(10f, 12f, 14f), minGap = 50f))
        assertEquals(listOf(0), timeLabelIndices(listOf(10f), minGap = 50f))
        assertTrue(timeLabelIndices(emptyList(), minGap = 50f).isEmpty())
    }

    @Test
    fun `засечки с дроблением шага делят интервал и сохраняют края`() {
        val axis = ChartAxis(lo = 37.0, step = 0.5)
        assertEquals(axis.ticks, axis.ticks(1))
        assertEquals(
            listOf(37.0, 37.25, 37.5, 37.75, 38.0, 38.25, 38.5, 38.75, 39.0),
            axis.ticks(2),
        )
        assertEquals(17, axis.ticks(4).size)
        assertEquals(axis.hi, axis.ticks(4).last(), 1e-9)
        // Положения не округляются до сотых: при шаге 0.1 и делении на 8 промежутки
        // остаются равными (37.0125, 37.025, …), а не пляшут между 0.01 и 0.02.
        val fine = ChartAxis(lo = 37.0, step = 0.1).ticks(8)
        val gaps = fine.zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(0.0125, it, 1e-9) }
        // Подпись получает только засечка, которая печатается точно: температура — в двух
        // знаках (37.25 да, 37.125 нет), влажность — в одном (60.5 да, 60.25 нет).
        assertTrue(axis.printable(37.25, decimals = 2))
        assertFalse(axis.printable(37.125, decimals = 2))
        assertTrue(axis.printable(37.05, decimals = 2))
        assertFalse(axis.printable(37.0125, decimals = 2))
        assertTrue(axis.printable(60.5, decimals = 1))
        assertFalse(axis.printable(60.25, decimals = 1))
    }

    @Test
    fun `описание графика словами — замеры, пределы, заметки и планы`() {
        val analytics = analyticsOf(
            listOf(
                measurement(1, "08:00", temp = 37.6, damp = 58.0, note = "Долил воды"),
                measurement(2, "12:00", temp = 38.1, damp = 55.0),
            ),
            plan(),
            listOf(ChartPlanLine("Курицы", 37.8, 55.0)),
        )
        assertEquals(
            "График показаний за день, 2 замера, температура от 37.6 до 38.1°, " +
                "влажность от 55 до 58%, 1 заметка, план закладок: Курицы",
            chartDescription(analytics, ru.zaroslikov.incubator.settings.TemperatureUnit.CELSIUS),
        )
    }

    @Test
    fun `заметки в одной точке собираются под одну иконку`() {
        val a = ChartEvent(ChartEventKind.Note, 480, 0, "08:00", "а")
        val b = ChartEvent(ChartEventKind.Note, 481, 0, "08:01", "б")
        val c = ChartEvent(ChartEventKind.Note, 720, 0, "12:00", "в")
        val groups = noteGroups(listOf(a, b, c), xs = listOf(100f, 104f, 300f), minGap = 13f)
        assertEquals(2, groups.size)
        assertEquals(listOf(a, b), groups[0].notes)
        assertEquals(100f, groups[0].x)
        assertEquals(listOf(c), groups[1].notes)
        // Порядок входа не важен — группы идут слева направо.
        val reversed = noteGroups(listOf(c, a), xs = listOf(300f, 100f), minGap = 13f)
        assertEquals(listOf(100f, 300f), reversed.map { it.x })
    }
}
