package ru.zaroslikov.incubator.domain.stats

import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.clockMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Сколько стоило электричество закладок — то, что «Финансы» прибавляют к расходу.
 *
 * Счёт идёт по времени, а не по дням: закладка работала от своего момента закладки до
 * завершения (или до «сейчас», если ещё идёт), и каждый час этого промежутка стоит
 * `мощность × цену того тарифа, который в этот час действовал`. Двухзонный тариф
 * поэтому считается по настоящим часам ночи в часовом поясе телефона — с переходом
 * через полночь и с переводом часов, если он случится.
 *
 * **Один инкубатор — один счётчик.** Если в устройстве одновременно идут две закладки,
 * розетка от этого не крутит вдвое быстрее, и сумма их счетов не может быть больше того,
 * что взял инкубатор. Поэтому общие часы делятся между закладками, которые в них шли,
 * поровну: каждая платит свою долю по своей мощности и своему тарифу. Пока закладка
 * в устройстве одна, она платит всё. Делить поровну, а не по яйцам, — сознательно
 * просто: греется воздух камеры, а не каждое яйцо, и объяснить «пополам» можно в одну
 * строку, а «пропорционально яйцам» — нет.
 *
 * Закладки без мощности или без тарифа в счёте не участвуют вовсе — ни своей суммой, ни
 * долей в общих часах: что они взяли из розетки, неизвестно, и отнимать за них долю у
 * соседки значило бы занизить её счёт ради числа, которого нет.
 */

/**
 * Промежуток, в который закладка [batchId] грелась в инкубаторе [deviceId].
 *
 * [start] и [end] — миллисекунды эпохи. [power] — уже сведённые настройки
 * ([PowerSettings.over] поверх инкубатора); посчитать можно только [PowerSettings.countable].
 */
data class PoweredRun(
    val batchId: Long,
    val deviceId: Long,
    val start: Long,
    val end: Long,
    val power: PowerSettings,
)

/**
 * Электричество одной закладки: [kwh] её доля киловатт-часов, [rubles] — их цена с
 * копейками. [shared] — часть времени она делила инкубатор с другой закладкой, и счёт
 * за эти часы поделён (экран говорит об этом подписью).
 *
 * [nightKwh] / [nightRubles] — ночная часть [kwh] / [rubles], та, что пришлась на часы
 * ночного тарифа; [twoTariffs] — хоть один час считался по двухзонному тарифу, и тогда
 * справка закладки показывает сумму по каждому тарифу. Дневная часть — разность, а не
 * отдельное поле: третье число, которое обязано сходиться с двумя другими, однажды
 * разошлось бы с ними.
 */
data class ElectricityCost(
    val kwh: Double,
    val rubles: Double,
    val shared: Boolean = false,
    val nightKwh: Double = 0.0,
    val nightRubles: Double = 0.0,
    val twoTariffs: Boolean = false,
    /**
     * Сколько часов закладки пришлось на ночной тариф — настоящих часов, **не** долей:
     * общие часы делят рубли и киловатты, а время у каждой закладки своё целиком.
     */
    val nightHours: Double = 0.0,
    /** Все часы закладки, за которые шёл счёт, — тоже целиком, без деления с соседками. */
    val hours: Double = 0.0,
) {
    /** Рубли целыми — так их складывают «Финансы», где копеек нет. */
    val roundedRubles: Int get() = rubles.roundToInt()

    val dayKwh: Double get() = kwh - nightKwh
    val dayRubles: Double get() = rubles - nightRubles
    val dayHours: Double get() = hours - nightHours
}

/**
 * Считает электричество закладок по их промежуткам.
 *
 * Промежутки разных инкубаторов друг другу не мешают, внутри одного — делят общие часы
 * (см. выше). Закладка, которой посчитать нечего, в ответ не попадает: у неё «не
 * указано», а не «ноль рублей».
 */
fun electricityCosts(runs: List<PoweredRun>, zone: ZoneId): Map<Long, ElectricityCost> {
    val result = mutableMapOf<Long, ElectricityCost>()
    runs.filter { it.power.countable && it.end > it.start }
        .groupBy { it.deviceId }
        .values
        .forEach { device ->
            val bounds = device.flatMap { listOf(it.start, it.end) }.distinct().sorted()
            bounds.zipWithNext { from, to ->
                val inside = device.filter { it.start <= from && it.end >= to }
                if (inside.isEmpty()) return@zipWithNext
                val share = 1.0 / inside.size
                inside.forEach { run ->
                    val segment = segmentCost(run.power, from, to, zone)
                    val previous = result[run.batchId] ?: ElectricityCost(0.0, 0.0)
                    result[run.batchId] = ElectricityCost(
                        kwh = previous.kwh + segment.kwh * share,
                        rubles = previous.rubles + segment.rubles * share,
                        shared = previous.shared || inside.size > 1,
                        nightKwh = previous.nightKwh + segment.nightKwh * share,
                        nightRubles = previous.nightRubles + segment.nightRubles * share,
                        twoTariffs = previous.twoTariffs || run.power.twoTariffs,
                        nightHours = previous.nightHours + segment.nightHours,
                        hours = previous.hours + segment.hours,
                    )
                }
            }
        }
    // Закладка без единой посчитанной минуты (например, оба конца в один миг) — тоже ответ:
    // её мощность и тариф известны, и ноль тут честный.
    runs.filter { it.power.countable && it.batchId !in result }
        .forEach { result[it.batchId] = ElectricityCost(0.0, 0.0, twoTariffs = it.power.twoTariffs) }
    return result
}

/** Киловатт-часы и рубли промежутка, и сколько из них пришлось на ночной тариф. */
internal data class SegmentCost(
    val kwh: Double,
    val rubles: Double,
    val nightKwh: Double = 0.0,
    val nightRubles: Double = 0.0,
    val nightHours: Double = 0.0,
    val hours: Double = 0.0,
)

/** Киловатт-часы и рубли за промежуток `[from, to)` при настройках [power]. */
internal fun segmentCost(power: PowerSettings, from: Long, to: Long, zone: ZoneId): SegmentCost {
    val kw = (power.watts ?: 0) / 1000.0
    val hours = (to - from) / MILLIS_PER_HOUR
    val dayPrice = power.dayPrice ?: 0.0
    if (!power.twoTariffs) return SegmentCost(kw * hours, kw * hours * dayPrice, hours = hours)

    val nightHours = nightMillis(
        from = from,
        to = to,
        nightStart = clockMinutes(power.nightStart)!!,
        nightEnd = clockMinutes(power.nightEnd)!!,
        zone = zone,
    ) / MILLIS_PER_HOUR
    val dayHours = hours - nightHours
    val nightPrice = power.nightPrice ?: dayPrice
    return SegmentCost(
        kwh = kw * hours,
        rubles = kw * (dayHours * dayPrice + nightHours * nightPrice),
        nightKwh = kw * nightHours,
        nightRubles = kw * nightHours * nightPrice,
        nightHours = nightHours,
        hours = hours,
    )
}

/**
 * Сколько миллисекунд промежутка `[from, to)` пришлось на ночной тариф.
 *
 * Ночь — окно от [nightStart] до [nightEnd] (минуты от полуночи) по местному времени;
 * если начало позже конца, окно переходит через полночь. Окна перебираются по местным
 * датам начиная с дня **до** [from]: ночь, начавшаяся накануне в 23:00, покрывает и
 * первые часы этого дня.
 */
internal fun nightMillis(from: Long, to: Long, nightStart: Int, nightEnd: Int, zone: ZoneId): Long {
    if (to <= from || nightStart == nightEnd) return 0
    val wraps = nightStart > nightEnd
    var date = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
    val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
    var total = 0L
    while (!date.isAfter(last)) {
        val windowStart = moment(date, nightStart, zone)
        val windowEnd = moment(if (wraps) date.plusDays(1) else date, nightEnd, zone)
        total += max(0L, min(to, windowEnd) - max(from, windowStart))
        date = date.plusDays(1)
    }
    return total
}

private fun moment(date: LocalDate, minutes: Int, zone: ZoneId): Long =
    date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant().toEpochMilli()

private const val MILLIS_PER_HOUR = 3_600_000.0
