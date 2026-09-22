package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.incubator.plural
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Счёт для «Аналитики за день» — макет
 * [21:9088](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=21-9088).
 *
 * Ровно арифметика, без Compose: график и плитки только рисуют то, что здесь посчитано,
 * а `BatchAnalyticsTest` в `app/src/test` гоняет это на JVM без эмулятора — как и
 * [ValueFormat].
 *
 * Считается всегда по замерам одного дня расписания ([Value]), тем же, что показывает
 * шторка закладки. День определяет, какие замеры сюда попали; дальше время внутри дня —
 * это `HH:mm` из самого замера, а не «когда открыли экран».
 */

/** Сколько засечек на оси — как в макете: пять линий сетки, четыре промежутка. */
internal const val AXIS_TICKS = 5

/**
 * Что случилось в замере, помимо самих показаний.
 *
 * [Turn] и [Note] рисуются одинаково — вертикальной чертой через весь график: и то и
 * другое произошло в одну минуту, длительности у них нет. [Airing] — единственное
 * событие с длительностью, поэтому оно закрашивает временную область, а не черту.
 */
internal enum class ChartEventKind { Turn, Airing, Note }

/** Точка графика: минута от полуночи плюс показания. Хотя бы одно из двух не `null`. */
internal data class ChartPoint(
    val minutes: Int,
    val time: String,
    val temp: Double?,
    val damp: Double?,
)

/**
 * [durationMinutes] отлично от нуля только у [ChartEventKind.Airing]; [note] непуст только у
 * [ChartEventKind.Note] — это сам текст заметки, который показывает иконка под её чертой.
 */
internal data class ChartEvent(
    val kind: ChartEventKind,
    val minutes: Int,
    val durationMinutes: Int,
    val time: String,
    val note: String = "",
)

/**
 * План одной закладки на день — горизонтальная линия на графике замеров по инкубатору.
 *
 * Появляется только у аналитики, открытой из шторки инкубатора: там термометр один на
 * несколько закладок, и вопрос «к чьей норме ближе показание» задаётся про каждую из
 * них. Аналитика одной закладки такой линии не носит — у неё цель одна и стоит плиткой.
 * [label] — название закладки (или вид, если закладку не назвали); хотя бы одно из
 * [temp] / [damp] не `null`, иначе рисовать нечего.
 */
internal data class ChartPlanLine(
    val label: String,
    val temp: Double?,
    val damp: Double?,
)

/**
 * Сводка дня: точки и события для графика плюс двенадцать чисел под ним.
 *
 * Плитки идут тремя рядами по четыре: счётчики за день, затем температура и влажность
 * одним и тем же набором «среднее — разброс — крайние». Поэтому у влажности здесь есть
 * ровно то же, что у температуры: макет спрашивает их симметрично.
 *
 * [airings] — сколько раз за день проветривали, [airingMinutes] — сколько всего минут:
 * замер хранит разы и длительность одного проветривания порознь, и обе цифры стоят в
 * макете соседними плитками.
 */
internal data class DayAnalytics(
    val points: List<ChartPoint> = emptyList(),
    val events: List<ChartEvent> = emptyList(),
    val total: Int = 0,
    val tempAvg: Double? = null,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val dampAvg: Double? = null,
    val dampMin: Double? = null,
    val dampMax: Double? = null,
    val turns: Int = 0,
    val airings: Int = 0,
    val airingMinutes: Int = 0,
    val tempTarget: Double? = null,
    val dampTarget: Double? = null,
    /** Планы закладок инкубатора — линии на графике; пусто у аналитики одной закладки. */
    val planLines: List<ChartPlanLine> = emptyList(),
) {
    val tempSpread: Double?
        get() = if (tempMin != null && tempMax != null) tempMax - tempMin else null

    val dampSpread: Double?
        get() = if (dampMin != null && dampMax != null) dampMax - dampMin else null

    val hasTemp: Boolean get() = points.any { it.temp != null }
    val hasDamp: Boolean get() = points.any { it.damp != null }

    /** Заметки за день — по ним под графиком стоят иконки. */
    val notes: List<ChartEvent> get() = events.filter { it.kind == ChartEventKind.Note }

    /** График рисовать не из чего: ни одного показания за день. */
    val hasChart: Boolean get() = hasTemp || hasDamp
}

/**
 * Собирает сводку дня из замеров и плана на этот день.
 *
 * Замеры приходят из базы по убыванию времени (свежий сверху) — здесь они
 * переворачиваются: график читается слева направо.
 *
 * Замер без времени в формате `HH:mm` в график не попадает, но в счётчики попадает:
 * «сколько раз перевернули» от разбора времени не зависит.
 */
internal fun analyticsOf(
    measurements: List<Measurement>,
    plan: Value?,
    planLines: List<ChartPlanLine> = emptyList(),
): DayAnalytics {
    if (measurements.isEmpty()) {
        // Без замеров нет графика, а без графика линия плана — обещание без картинки.
        return DayAnalytics(tempTarget = plan?.temp, dampTarget = plan?.damp)
    }

    val ordered = measurements.sortedWith(
        compareBy({ parseClock(it.time) ?: Int.MAX_VALUE }, { it.id })
    )

    val points = ordered.mapNotNull { measurement ->
        val minutes = parseClock(measurement.time) ?: return@mapNotNull null
        if (measurement.temp == null && measurement.damp == null) return@mapNotNull null
        ChartPoint(
            minutes = minutes,
            time = measurement.time,
            temp = measurement.temp,
            damp = measurement.damp,
        )
    }

    // Линия плана ложится на ось своей величины, а ось есть только у величины, которую
    // за день замеряли: план по температуре в день, когда писали одну влажность, рисовать
    // не на чем. Такие отсеиваются здесь — чтобы ни график, ни легенда не обещали
    // закладку, линии которой на графике нет.
    val measuredTemp = points.any { it.temp != null }
    val measuredDamp = points.any { it.damp != null }
    val lines = planLines.filter {
        (it.temp != null && measuredTemp) || (it.damp != null && measuredDamp)
    }

    val events = buildList {
        ordered.forEach { measurement ->
            val minutes = parseClock(measurement.time) ?: return@forEach
            if ((measurement.airingCount ?: 0) > 0) {
                add(
                    ChartEvent(
                        kind = ChartEventKind.Airing,
                        minutes = minutes,
                        // Ширину закрашенной области задаёт всё время, что инкубатор
                        // был открыт: замер хранит длительность одного проветривания,
                        // а их в нём может быть записано несколько.
                        durationMinutes = measurement.airingTotalMinutes,
                        time = measurement.time,
                    )
                )
            }
            if ((measurement.over ?: 0) > 0) {
                add(ChartEvent(ChartEventKind.Turn, minutes, 0, measurement.time))
            }
            if (measurement.note.isNotBlank()) {
                add(
                    ChartEvent(
                        kind = ChartEventKind.Note,
                        minutes = minutes,
                        durationMinutes = 0,
                        time = measurement.time,
                        note = measurement.note.trim(),
                    )
                )
            }
        }
    }

    val temps = ordered.mapNotNull { it.temp }
    val damps = ordered.mapNotNull { it.damp }
    val target = plan?.temp
    val counts = actionCountsOf(ordered)

    return DayAnalytics(
        points = points,
        events = events,
        total = ordered.size,
        tempAvg = temps.averageOrNull(),
        tempMin = temps.minOrNull(),
        tempMax = temps.maxOrNull(),
        dampAvg = damps.averageOrNull(),
        dampMin = damps.minOrNull(),
        dampMax = damps.maxOrNull(),
        turns = counts.turns,
        airings = counts.airings,
        airingMinutes = counts.airingMinutes,
        tempTarget = target,
        dampTarget = plan?.damp,
        planLines = lines,
    )
}

/**
 * Какие из подписей времени поместятся: индексы точек, чьи подписи не налезут друг на
 * друга при ширине подписи [minGap] (в пикселях, с зазором).
 *
 * Жадно слева направо: первая подпись всегда, следующая — если её `x` отступил от
 * предыдущей выбранной хотя бы на [minGap]. Последняя точка важнее произвольной
 * средней — если ей не хватило места, она вытесняет предыдущую выбранную (но не первую).
 * Одно правило и для карточки, и для графика на весь экран: в карточке на 300 dp это
 * даёт те же две-три подписи, что и макет, а при увеличении подписей становится больше
 * ровно настолько, насколько раздвинулись точки.
 */
internal fun timeLabelIndices(xs: List<Float>, minGap: Float): List<Int> {
    if (xs.isEmpty()) return emptyList()
    val chosen = mutableListOf(0)
    for (i in 1 until xs.size) {
        if (xs[i] - xs[chosen.last()] >= minGap) chosen += i
    }
    val last = xs.lastIndex
    if (chosen.last() != last) {
        if (chosen.size > 1) chosen.removeAt(chosen.lastIndex)
        if (xs[last] - xs[chosen.last()] >= minGap) chosen += last
    }
    return chosen
}

/**
 * Описание графика словами — для тех, кто его не видит (TalkBack): сколько замеров,
 * в каких пределах температура и влажность, сколько заметок и планов закладок. Без
 * измерения текста и без Compose, поэтому здесь; `BatchAnalyticsTest` проверяет фразы.
 */
internal fun chartDescription(analytics: DayAnalytics, unit: TemperatureUnit): String {
    val parts = mutableListOf("График показаний за день")
    parts += plural(analytics.total, "замер", "замера", "замеров")
    if (analytics.tempMin != null && analytics.tempMax != null) {
        parts += "температура от ${analytics.tempMin.formatTemp(unit)} до ${analytics.tempMax.formatTemp(unit)}°"
    }
    if (analytics.dampMin != null && analytics.dampMax != null) {
        parts += "влажность от ${analytics.dampMin.formatDamp()} до ${analytics.dampMax.formatDamp()}%"
    }
    val notes = analytics.notes.size
    if (notes > 0) parts += plural(notes, "заметка", "заметки", "заметок")
    if (analytics.planLines.isNotEmpty()) {
        parts += "план закладок: " + analytics.planLines.joinToString { it.label }
    }
    return parts.joinToString(", ")
}

/** Иконка заметок под графиком: её `x` и заметки, которые под ней стоят. */
internal data class NoteGroup(val x: Float, val notes: List<ChartEvent>)

/**
 * Раскладывает заметки по иконкам: заметки, чьи черты стоят ближе [minGap] пикселей друг к
 * другу — записанные в одну минуту или в соседние при сжатом масштабе, — получают одну
 * иконку на месте первой и открываются одним списком. [xs] — `x` каждой заметки, в том же
 * порядке, что и [notes]; порядок по времени соблюдается внутри группы.
 */
internal fun noteGroups(notes: List<ChartEvent>, xs: List<Float>, minGap: Float): List<NoteGroup> {
    require(notes.size == xs.size) { "xs must match notes" }
    val groups = mutableListOf<NoteGroup>()
    notes.indices.sortedBy { xs[it] }.forEach { index ->
        val last = groups.lastOrNull()
        if (last != null && xs[index] - last.x < minGap) {
            groups[groups.lastIndex] = last.copy(notes = last.notes + notes[index])
        } else {
            groups += NoteGroup(xs[index], listOf(notes[index]))
        }
    }
    return groups
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

// --- Счётчики действий за день -------------------------------------------------------------

/**
 * Сколько за день перевернули и проветрили.
 *
 * Считается отдельно от [DayAnalytics], потому что нужно в двух местах: в шторке
 * аналитики — плитками, и в карточке «Замеры за сегодня» — строкой под показаниями.
 * Определение «что считать переворотом» при этом одно на оба.
 *
 * [airings] — сколько раз за день проветрили, [airingMinutes] — сколько всего минут:
 * замер хранит длительность **одного** проветривания, поэтому минуты за день это
 * сумма произведений, а не сумма длительностей.
 */
internal data class ActionCounts(
    val turns: Int = 0,
    val airings: Int = 0,
    val airingMinutes: Int = 0,
)

/**
 * Незаполненное поле замера — `null`, и в сумму оно даёт ноль: на счётчике дня «не
 * записали» и «ноль раз» выглядят одинаково. В плане ([Value.over],
 * [Value.airingCount]) `null` значит совсем другое — «нормы нет», — и там его не
 * складывают, а показывают отдельно.
 */
internal fun actionCountsOf(measurements: List<Measurement>): ActionCounts = ActionCounts(
    turns = measurements.sumOf { it.over ?: 0 },
    airings = measurements.sumOf { it.airingCount ?: 0 },
    airingMinutes = measurements.sumOf { it.airingTotalMinutes },
)

// --- Итог по замерам за всю закладку -------------------------------------------------------

/**
 * Те же двенадцать чисел, что и у [DayAnalytics], но за всю инкубацию — карточка «Итог
 * по замерам» на «Обзоре» завершённой закладки.
 *
 * Считается по дням расписания ([Value.id] ↔ [Measurement.idValue]), как и
 * [averagedScheduleOf]: замер находит свой день по строке плана, а не по календарю.
 *
 * [daysRun] — сколько дней закладка на самом деле шла, и это знаменатель охвата.
 * Не длина расписания: у прерванной на пятый день партии оно всё равно на двадцать
 * восемь дней, и «замеры в 3 днях из 28» читалось бы как небрежность там, где двадцать
 * три дня просто не наступили.
 *
 * [tempOffPlan] / [dampOffPlan] — среднее отклонение факта от плана **того дня, в
 * который замер записан**, а не от одной общей цели: режим меняется от дня к дню, и
 * разница со средним по расписанию не значила бы ничего. Дни, где в плане цели нет,
 * в отклонение не входят вовсе — вычитать не из чего.
 */
internal data class BatchTotals(
    val measurements: Int = 0,
    val daysMeasured: Int = 0,
    val daysRun: Int = 0,
    val tempAvg: Double? = null,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val tempOffPlan: Double? = null,
    val dampAvg: Double? = null,
    val dampMin: Double? = null,
    val dampMax: Double? = null,
    val dampOffPlan: Double? = null,
    val turns: Int = 0,
    val airings: Int = 0,
    val airingMinutes: Int = 0,
) {
    val tempSpread: Double?
        get() = if (tempMin != null && tempMax != null) tempMax - tempMin else null

    val dampSpread: Double?
        get() = if (dampMin != null && dampMax != null) dampMax - dampMin else null

    /** Ни одного замера за всю закладку: сводке нечего показывать. */
    val isEmpty: Boolean get() = measurements == 0
}

/**
 * Собирает итог по всем замерам закладки.
 *
 * @param plan все дни расписания закладки.
 * @param measurementsByDay её замеры, разложенные по [Measurement.idValue], — ровно то,
 *   что держит `BatchDetailViewModel`.
 * @param daysRun сколько дней закладка шла; ноль и меньше означают «неизвестно», и
 *   тогда охват считается по всему расписанию.
 */
internal fun batchTotals(
    plan: List<Value>,
    measurementsByDay: Map<Long, List<Measurement>>,
    daysRun: Int,
): BatchTotals {
    val ran = if (daysRun > 0) plan.filter { it.day <= daysRun } else plan
    val all = ran.flatMap { measurementsByDay[it.id].orEmpty() }
    if (all.isEmpty()) return BatchTotals(daysRun = ran.size)

    val temps = all.mapNotNull { it.temp }
    val damps = all.mapNotNull { it.damp }
    val counts = actionCountsOf(all)

    return BatchTotals(
        measurements = all.size,
        daysMeasured = ran.count { measurementsByDay[it.id].orEmpty().isNotEmpty() },
        daysRun = ran.size,
        tempAvg = temps.averageOrNull(),
        tempMin = temps.minOrNull(),
        tempMax = temps.maxOrNull(),
        tempOffPlan = offPlanAverage(ran, measurementsByDay, Value::temp, Measurement::temp),
        dampAvg = damps.averageOrNull(),
        dampMin = damps.minOrNull(),
        dampMax = damps.maxOrNull(),
        dampOffPlan = offPlanAverage(ran, measurementsByDay, Value::damp, Measurement::damp),
        turns = counts.turns,
        airings = counts.airings,
        airingMinutes = counts.airingMinutes,
    )
}

/**
 * Среднее «факт минус план» по всем замерам, у которых есть и то и другое.
 *
 * Среднее по замерам, а не по дням: день с десятью замерами и правда весит больше дня
 * с одним — в нём режим измеряли чаще, и о нём известно больше.
 */
private fun offPlanAverage(
    days: List<Value>,
    byDay: Map<Long, List<Measurement>>,
    target: (Value) -> Double?,
    reading: (Measurement) -> Double?,
): Double? = days
    .flatMap { day ->
        val goal = target(day) ?: return@flatMap emptyList<Double>()
        byDay[day.id].orEmpty().mapNotNull { reading(it)?.minus(goal) }
    }
    .averageOrNull()

/**
 * Строка под плитками итога: «За всю инкубацию температуру держали **по плану**,
 * влажность — **ниже** плана на `4%`.»
 *
 * Пороги те же, что у сводки дня ([AVG_TOLERANCE_TEMP], [AVG_TOLERANCE_DAMP]): «в цели»
 * за день и «по плану» за всю закладку — один и тот же вопрос в разном масштабе, и два
 * разных ответа о том, что считать отклонением, сбивали бы с толку.
 *
 * Пусто, когда сравнивать не с чем: нет замеров или ни у одного их дня не задана цель.
 */
internal fun totalsInsightParts(
    totals: BatchTotals,
    unit: TemperatureUnit = TemperatureUnit.CELSIUS,
): List<InsightPart> {
    val temp = totals.tempOffPlan
    val damp = totals.dampOffPlan
    if (totals.isEmpty || (temp == null && damp == null)) return emptyList()

    val parts = mutableListOf<InsightPart>()
    parts += plain("За всю инкубацию ")
    if (temp != null) {
        parts += plain("температуру держали ")
        parts += offPlanTail(temp, AVG_TOLERANCE_TEMP) { "${it.formatTempDelta(unit)}°" }
    }
    when {
        temp != null && damp != null -> parts += plain(", влажность — ")
        damp != null -> parts += plain("влажность держали ")
    }
    if (damp != null) {
        parts += offPlanTail(damp, AVG_TOLERANCE_DAMP) { "${it.roundToInt()}%" }
    }
    parts += plain(".")
    return parts
}

/** «по плану» или «выше плана на 0.3°» — хвост одной величины в [totalsInsightParts]. */
private fun offPlanTail(
    delta: Double,
    tolerance: Double,
    format: (Double) -> String,
): List<InsightPart> {
    if (abs(delta) <= tolerance) return listOf(strong("по плану"))
    return listOf(
        strong(if (delta > 0) "выше" else "ниже"),
        plain(" плана на "),
        figure(format(abs(delta))),
    )
}

/** «08:00» → 480. Мусор и пустая строка — `null`. */
internal fun parseClock(time: String): Int? {
    val parts = time.trim().split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

// --- Ось графика ---------------------------------------------------------------------------

/**
 * Ось со «круглыми» засечками: [lo], [lo] + [step], … — всего [AXIS_TICKS] штук.
 *
 * Верх и низ не совпадают с крайними замерами: подписи вроде «36.37» на оси не читаются,
 * поэтому шаг берётся из готового набора, а нижняя засечка округляется вниз до него.
 */
internal data class ChartAxis(val lo: Double, val step: Double) {
    val hi: Double get() = lo + step * (AXIS_TICKS - 1)
    val ticks: List<Double> get() = (0 until AXIS_TICKS).map { round2(lo + step * it) }

    /**
     * Засечки с дроблением шага: при увеличении графика в [subdivisions] раз пять линий
     * сетки разъезжаются на весь экран, и между ними нечего читать — шаг делится на
     * столько же частей. Крайние засечки те же, что у [ticks]; при `1` это и есть [ticks].
     *
     * Положения **не округляются до сотых**, как [ticks]: дробная засечка вроде 37.025
     * должна стоять ровно посередине, а округлённая до 37.03 сдвигала бы линию сетки и
     * делала промежутки неравными. Округляется только шум двоичной арифметики; сколько
     * знаков печатать, решает подпись — и засечка, которую нельзя напечатать точно,
     * остаётся линией без подписи ([printable]).
     */
    fun ticks(subdivisions: Int): List<Double> {
        val parts = subdivisions.coerceAtLeast(1)
        return (0..(AXIS_TICKS - 1) * parts).map { round6(lo + step * it / parts) }
    }

    /**
     * Можно ли подписать засечку [value] в [decimals] знаках **точно** (два у температуры,
     * один у влажности). Сетка при увеличении дробится как угодно — линии стоят на точных
     * местах, — а подпись получает только та засечка, чьё число печатается без округления:
     * «37.13» рядом с линией на 37.125 говорила бы не то, где линия.
     */
    fun printable(value: Double, decimals: Int): Boolean {
        val scaled = value * Math.pow(10.0, decimals.toDouble())
        return abs(scaled - Math.rint(scaled)) < 1e-6
    }

    /** Доля от низа оси: 0 — нижняя засечка, 1 — верхняя. */
    fun fraction(value: Double): Float = ((value - lo) / (hi - lo)).toFloat()
}

/** Шаги оси температуры: полградуса в макете, мельче — когда замеры кучнее. */
internal val TEMP_AXIS_STEPS = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0)

/** Шаги оси влажности — целые проценты: десятых там не показывают. */
internal val DAMP_AXIS_STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0)

/**
 * Подбирает ось так, чтобы [min] и [max] в неё попали целиком.
 *
 * Шаг проверяется вместе с уже округлённым низом, а не по одному только размаху: низ
 * округляется *вниз*, и это съедает часть окна. Замеры 37.1…37.9 при шаге 0.2 дают
 * низ 37.0 и верх 37.8 — максимум оказался бы нарисован над верхней линией сетки.
 *
 * Один-единственный замер даёт нулевой разброс — ось тогда раздвигается вокруг него,
 * иначе точка легла бы ровно на нижнюю линию сетки и читалась бы как край шкалы.
 */
internal fun niceAxis(min: Double, max: Double, steps: List<Double>): ChartAxis {
    val smallest = steps.first()
    var low = min
    var high = max
    if (high - low < 1e-9) {
        low -= smallest * 2
        high += smallest * 2
    }

    fun fits(step: Double): ChartAxis? {
        val lo = floor(low / step) * step
        val hi = lo + step * (AXIS_TICKS - 1)
        return if (hi >= high - 1e-9) ChartAxis(round2(lo), round2(step)) else null
    }

    steps.forEach { step -> fits(step)?.let { return it } }
    // Размах шире всего набора — идём вверх кратно последнему шагу, пока не накроем.
    var step = steps.last() * ceil((high - low) / (AXIS_TICKS - 1) / steps.last())
    while (true) {
        fits(step)?.let { return it }
        step += steps.last()
    }
}

/** До сотых — точность ввода и хранения; общий с `ScheduleOverlap.kt`. */
internal fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

/** До миллионных — только чтобы снять шум двоичной арифметики, а не для показа. */
private fun round6(value: Double): Double = (value * 1_000_000.0).roundToLong() / 1_000_000.0

// --- Текст-вывод под плитками --------------------------------------------------------------

/** Как выделен кусок вывода: обычный текст, полужирный или моноширинная цифра. */
internal enum class InsightEmphasis { Plain, Strong, Figure }

internal data class InsightPart(val text: String, val emphasis: InsightEmphasis)

private fun plain(text: String) = InsightPart(text, InsightEmphasis.Plain)
private fun strong(text: String) = InsightPart(text, InsightEmphasis.Strong)
private fun figure(text: String) = InsightPart(text, InsightEmphasis.Figure)

/** Разброс температуры за день: пороги свои, макет рисует только «большой». */
private const val SPREAD_TIGHT = 0.3
private const val SPREAD_MODERATE = 0.8

/** Отклонение средней температуры, ниже которого говорить не о чем. */
private const val AVG_TOLERANCE_TEMP = 0.2

/** То же для влажности: макет нигде не обещает точность до процента. */
private const val AVG_TOLERANCE_DAMP = 3.0

/**
 * Текст под плитками: «Средняя температура **ниже** цели на **1.5°**. Разброс большой —
 * стоит стабилизировать режим.»
 *
 * Возвращается кусками, а не готовым `AnnotatedString`, чтобы разбор оставался
 * проверяемым тестом, а начертания подставлял тот, кто рисует.
 */
internal fun insightParts(
    analytics: DayAnalytics,
    unit: TemperatureUnit = TemperatureUnit.CELSIUS,
): List<InsightPart> {
    if (analytics.total == 0) {
        return listOf(plain("Замеров за сегодня ещё нет — запишите показания, и сводка появится здесь."))
    }
    val avg = analytics.tempAvg
    if (avg == null) {
        return listOf(plain("Температуру сегодня не записывали — сводка по ней появится с первым замером."))
    }

    val parts = mutableListOf<InsightPart>()
    val target = analytics.tempTarget
    if (target == null) {
        parts += plain("Цель на этот день не задана — сравнивать среднюю не с чем. ")
    } else {
        val delta = avg - target
        parts += plain("Средняя температура ")
        if (abs(delta) <= AVG_TOLERANCE_TEMP) {
            parts += strong("в цели")
            parts += plain(". ")
        } else {
            parts += strong(if (delta > 0) "выше" else "ниже")
            parts += plain(" цели на ")
            parts += figure("${abs(delta).formatTempDelta(unit)}°")
            parts += plain(". ")
        }
    }

    val spread = analytics.tempSpread
    if (spread != null) {
        parts += plain("Разброс ")
        when {
            spread <= SPREAD_TIGHT -> {
                parts += strong("небольшой")
                parts += plain(" — режим держится ровно.")
            }

            spread <= SPREAD_MODERATE -> {
                parts += strong("умеренный")
                parts += plain(" — за режимом стоит следить.")
            }

            else -> {
                parts += strong("большой")
                parts += plain(" — стоит стабилизировать режим.")
            }
        }
    }

    val dampAvg = analytics.dampAvg
    val dampTarget = analytics.dampTarget
    if (dampAvg != null && dampTarget != null) {
        val delta = dampAvg - dampTarget
        if (abs(delta) > AVG_TOLERANCE_DAMP) {
            parts += plain(" Влажность ")
            parts += strong(if (delta > 0) "выше" else "ниже")
            parts += plain(" цели на ")
            parts += figure("${abs(delta).roundToInt()}%")
            parts += plain(".")
        }
    }
    return parts
}

/**
 * Средние и крайние показываются с одним знаком, как в макете («36.3°», «1.0°»):
 * вторую цифру после точки среднее арифметическое насчитывает всегда, а значить она
 * ничего не значит.
 */
internal fun roundTo1(value: Double): Double = (value * 10.0).roundToLong() / 10.0

// --- Режим по среднему факту ------------------------------------------------------------------

/**
 * Пересобирает режим завершённой закладки по её же замерам: вместо того, что на день
 * планировалось, — то, что на нём в среднем и вышло.
 *
 * Нужно форме новой закладки: у того, кто уже выводил эту птицу, выверенный режим лежит
 * не в плане, который он один раз сгенерировал и, может, ни разу не поправил, а в
 * показаниях, которые он снимал изо дня в день. План той закладки и её факт — два
 * разных ответа на вопрос «как вести следующую», и выбор между ними за пользователем.
 *
 * Считается по дням расписания ([Value.id] ↔ [Measurement.idValue]), а не по датам:
 * привязка замера к календарю здесь ни при чём, см. [Measurement].
 *
 * @param plan дни закладки-источника; порядок сохраняется.
 * @param measurements все её замеры, любым порядком — раскладываются по дням сами.
 */
internal fun averagedScheduleOf(plan: List<Value>, measurements: List<Measurement>): List<Value> {
    val byDay = measurements.groupBy { it.idValue }
    return plan.map { day -> averagedDayOf(day, byDay[day.id].orEmpty()) }
}

/**
 * Один день: столбец берётся из замеров, только если в замерах он есть.
 *
 * Иначе на его месте остаётся план того же дня. День, за который ничего не записали,
 * так не проваливается в пустоту, а в столбцах это ещё важнее: незаполненное поле
 * замера — `null`, и [actionCountsOf] считает его нулём, но ноль в плане значит
 * «не переворачивать» — норму, которой никто не задавал. Поэтому счётчики берутся из
 * факта лишь тогда, когда хоть один замер за день их действительно записал.
 *
 * Температура округляется до десятой, влажность — до целого: у среднего
 * арифметического знаков всегда больше, чем в нём смысла, и ровно столько же их
 * показывают плитки аналитики и поля ввода.
 *
 * [Value.airingTime] — минуты **одного** проветривания, поэтому здесь не сумма, а
 * средняя длительность: все минуты за день, делённые на число проветриваний.
 */
internal fun averagedDayOf(plan: Value, measurements: List<Measurement>): Value {
    if (measurements.isEmpty()) return plan

    val temps = measurements.mapNotNull { it.temp }
    val damps = measurements.mapNotNull { it.damp }
    val turnRecorded = measurements.any { it.over != null }
    val airingRecorded = measurements.any { it.airingCount != null || it.airingTime != null }
    val counts = actionCountsOf(measurements)

    return plan.copy(
        temp = if (temps.isEmpty()) plan.temp else roundTo1(temps.average()),
        damp = if (damps.isEmpty()) plan.damp else damps.average().roundToInt().toDouble(),
        over = if (turnRecorded) counts.turns else plan.over,
        airingCount = if (airingRecorded) counts.airings else plan.airingCount,
        airingTime = when {
            !airingRecorded -> plan.airingTime
            counts.airings == 0 -> 0
            else -> (counts.airingMinutes.toDouble() / counts.airings).roundToInt()
        },
    )
}
