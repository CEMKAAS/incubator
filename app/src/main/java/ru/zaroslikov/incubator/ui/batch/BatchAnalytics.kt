package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Счёт для «Аналитики за день» — макет
 * [16:6632](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=16-6632).
 *
 * Ровно арифметика, без Compose: график и плитки только рисуют то, что здесь посчитано,
 * а `BatchAnalyticsTest` в `app/src/test` гоняет это на JVM без эмулятора — как и
 * [ValueFormat].
 *
 * Считается всегда по замерам одного дня расписания ([Value]), тем же, что показывает
 * шторка закладки. День определяет, какие замеры сюда попали; дальше время внутри дня —
 * это `HH:mm` из самого замера, а не «когда открыли экран».
 */

/** Полоса «в цель» из макета: ±0.3° вокруг плановой температуры. */
internal const val TARGET_BAND_TEMP = 0.3

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

/** [durationMinutes] отлично от нуля только у [ChartEventKind.Airing]. */
internal data class ChartEvent(
    val kind: ChartEventKind,
    val minutes: Int,
    val durationMinutes: Int,
    val time: String,
)

/**
 * Сводка дня: точки и события для графика плюс девять чисел под ним.
 *
 * [inTargetPercent] — доля замеров, чья температура уложилась в ±[TARGET_BAND_TEMP] от
 * плана. `null`, когда плана на день нет: сравнивать не с чем, и ноль здесь соврал бы.
 */
internal data class DayAnalytics(
    val points: List<ChartPoint> = emptyList(),
    val events: List<ChartEvent> = emptyList(),
    val total: Int = 0,
    val tempAvg: Double? = null,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val dampAvg: Double? = null,
    val inTargetPercent: Int? = null,
    val turns: Int = 0,
    val airings: Int = 0,
    val tempTarget: Double? = null,
    val dampTarget: Double? = null,
) {
    val tempSpread: Double?
        get() = if (tempMin != null && tempMax != null) tempMax - tempMin else null

    val hasTemp: Boolean get() = points.any { it.temp != null }
    val hasDamp: Boolean get() = points.any { it.damp != null }

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
internal fun analyticsOf(measurements: List<Measurement>, plan: Value?): DayAnalytics {
    if (measurements.isEmpty()) {
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

    val events = buildList {
        ordered.forEach { measurement ->
            val minutes = parseClock(measurement.time) ?: return@forEach
            if (measurement.airing.isNotBlank()) {
                add(
                    ChartEvent(
                        kind = ChartEventKind.Airing,
                        minutes = minutes,
                        // Поле подписано «ПРОВ., МИН» — это длительность, и она задаёт
                        // ширину закрашенной области.
                        durationMinutes = leadingCount(measurement.airing) ?: 0,
                        time = measurement.time,
                    )
                )
            }
            if (measurement.over.isNotBlank()) {
                add(ChartEvent(ChartEventKind.Turn, minutes, 0, measurement.time))
            }
            if (measurement.note.isNotBlank()) {
                add(ChartEvent(ChartEventKind.Note, minutes, 0, measurement.time))
            }
        }
    }

    val temps = ordered.mapNotNull { it.temp }
    val damps = ordered.mapNotNull { it.damp }
    val target = plan?.temp

    return DayAnalytics(
        points = points,
        events = events,
        total = ordered.size,
        tempAvg = temps.averageOrNull(),
        tempMin = temps.minOrNull(),
        tempMax = temps.maxOrNull(),
        dampAvg = damps.averageOrNull(),
        inTargetPercent = if (target == null || temps.isEmpty()) null else {
            // Допуск на погрешность double: «38.1 при цели 37.8» — ровно граница полосы,
            // и без него попадание зависело бы от последнего бита мантиссы.
            val hit = temps.count { abs(it - target) <= TARGET_BAND_TEMP + 1e-9 }
            (hit * 100.0 / temps.size).roundToInt()
        },
        // Отметка переворота — это «1», но замеры прежних версий хранили там число
        // («перев. 3»), и такой замер стоит трёх, а не одного.
        turns = ordered.sumOf { if (it.over.isBlank()) 0 else (leadingCount(it.over) ?: 1) },
        // Проветривание, наоборот, считается замерами: в поле лежат минуты, а не «сколько раз».
        airings = ordered.count { it.airing.isNotBlank() },
        tempTarget = target,
        dampTarget = plan?.damp,
    )
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

/** «08:00» → 480. Мусор и пустая строка — `null`. */
internal fun parseClock(time: String): Int? {
    val parts = time.trim().split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Ведущее целое из строки счётчика: «15», «15 мин», «2 раза» → 15, 15, 2. */
private fun leadingCount(raw: String): Int? =
    Regex("^\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()

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

private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

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
internal fun insightParts(analytics: DayAnalytics): List<InsightPart> {
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
            parts += figure("${roundTo1(abs(delta)).formatTemp()}°")
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
