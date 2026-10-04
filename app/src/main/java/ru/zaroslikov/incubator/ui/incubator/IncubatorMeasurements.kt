package ru.zaroslikov.incubator.ui.incubator

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.batch.ChartPlanLine
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Арифметика замера по инкубатору — без Compose и без базы, чтобы
 * `IncubatorMeasurementsTest` гонял её на JVM.
 *
 * Замер по инкубатору — одно показание прибора, записанное по копии в строку
 * сегодняшнего дня каждой идущей закладки и помеченное общим [Measurement.groupId]
 * (схема v17). Здесь — всё, что из этого следует: какие строки сегодня считать целями,
 * как из замеров этих дней собрать группы обратно в одну строку журнала, что считать
 * общей целью для плиток и как разложить одну форму по копиям с учётом автоматики
 * каждой закладки.
 */

/**
 * Идущая закладка инкубатора и строка её сегодняшнего дня.
 *
 * [plan] — `null`, когда строки нет: дата закладки не разобралась или день вышел за
 * расписание. Такой закладке копию положить некуда, и шторка говорит об этом прямо,
 * а не молча пропускает.
 */
internal data class MeasurementTarget(
    val batch: Batch,
    val day: Int,
    val plan: Value?,
    /** Срок вида — для подписи «День N/M»; `null` у вида, срок которого неизвестен. */
    val totalDays: Int? = null,
) {
    /** Копия сюда ляжет: строка дня есть. Без неё цель нельзя и выбрать. */
    val canReceive: Boolean get() = plan != null

    val autoTurn: Boolean get() = batch.over.toBoolean()
    val autoAiring: Boolean get() = batch.airing.toBoolean()
}

/**
 * Цели, которые получат копию: со строкой дня и не снятые галочкой.
 *
 * Выбор хранится как множество **снятых** закладок, а не выбранных, и это не мелочь:
 * по умолчанию выбраны все, и закладка, появившаяся в приборе, пока шторка открыта,
 * должна оказаться выбранной сама, а завершённая — пропасть из выбора, ничего за собой
 * не оставив. Множество выбранных пришлось бы догонять за списком целей при каждом
 * ответе базы; множество снятых просто пересекается с ним.
 */
internal fun selectedTargets(
    targets: List<MeasurementTarget>,
    deselected: Set<Long>,
): List<MeasurementTarget> = targets.filter { it.canReceive && it.batch.id !in deselected }

/**
 * Журнал шторки инкубатора: по одной строке на группу, в порядке ответа базы. Замеры без метки
 * (внесённые в закладке) сюда не попадают — не сказать, чьи они.
 *
 * Строка группы — сшитая, а не «любая» копия: копии различаются тем, что сняла автоматика
 * ([measurementCopies]), и у закладки на автоперевороте переворот пустой — «Изм.» стёр бы число
 * во всех копиях. Показания и время берутся у первой копии, переворот и проветривание — у первой,
 * где они есть. Идентификатор — первой копии: по нему форма и удаление находят группу.
 */
internal fun deviceMeasurements(rows: List<Measurement>): List<Measurement> =
    rows.asSequence()
        .filter { it.groupId != null }
        .groupBy { it.groupId }
        .values
        .map { copies ->
            copies.first().copy(
                over = copies.firstNotNullOfOrNull { it.over },
                airingCount = copies.firstNotNullOfOrNull { it.airingCount },
                airingTime = copies.firstNotNullOfOrNull { it.airingTime },
            )
        }

/**
 * Общая цель для плиток и аналитики: среднее планов дня по выбранным целям — «цель» одной закладки
 * над замером всего прибора была бы ложью о других. Каждое поле усредняется по планам, где оно
 * задано (закладка на автоматике с `null` не тянет норму к нулю); «нормы нет» — когда её нет ни у
 * кого. Температура — до сотых, влажность — до десятых, счётчики — до целого. Без целей — `null`.
 * Строка синтетическая: без дня и закладки, несёт четыре числа туда, где ждут `Value`.
 */
internal fun averagePlan(targets: List<MeasurementTarget>): Value? {
    val plans = targets.mapNotNull { it.plan }
    if (plans.isEmpty()) return null
    fun mean(pick: (Value) -> Double?): Double? =
        plans.mapNotNull(pick).takeIf { it.isNotEmpty() }?.average()
    fun meanInt(pick: (Value) -> Int?): Int? = mean { pick(it)?.toDouble() }?.roundToInt()
    return Value(
        id = 0,
        day = 0,
        temp = mean { it.temp }?.let { round2(it) },
        damp = mean { it.damp }?.let { round1(it) },
        over = meanInt { it.over },
        airingCount = meanInt { it.airingCount },
        airingTime = meanInt { it.airingTime },
        note = "",
        idPT = 0,
    )
}

private fun round1(value: Double): Double = (value * 10.0).roundToLong() / 10.0
private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

/**
 * Планы закладок для графика «Аналитики за день» по инкубатору — по линии на закладку.
 *
 * Берутся те же цели, из которых считается [averagePlan]: снятая галочкой закладка
 * не получает копии, не входит в среднюю цель — и линии её плана на графике тоже нет,
 * иначе показание сравнивалось бы с нормой закладки, к которой оно не относится.
 * Подпись — название закладки, а у безымянной — вид: на карточке в списке она
 * подписана так же. Закладка, чей план не задаёт ни температуры, ни влажности, линии
 * не даёт — [analyticsOf] такие отсеивает.
 */
internal fun planLinesOf(targets: List<MeasurementTarget>): List<ChartPlanLine> =
    targets.mapNotNull { target ->
        val plan = target.plan ?: return@mapNotNull null
        ChartPlanLine(
            label = target.batch.title.ifBlank { target.batch.type },
            temp = plan.temp,
            damp = plan.damp,
        )
    }

/**
 * Копии нового замера — по одной на каждую цель со строкой дня.
 *
 * Автоматика уважается по-закладочно, тем же правилом, что и `BatchDetailViewModel.persist`:
 * под автопереворотом числа переворотов нет, под автопроветриванием нет ни разов, ни
 * минут — закладка на автомате не делала этого руками, и записать ей это как факт
 * нельзя ниоткуда. Один прибор, но две закладки с разной автоматикой получают разные
 * копии одного показания, и это правильно: температура у них общая, а переворот — нет.
 */
internal fun measurementCopies(
    targets: List<MeasurementTarget>,
    template: Measurement,
    groupId: String,
): List<Measurement> = targets.mapNotNull { target ->
    val plan = target.plan ?: return@mapNotNull null
    template.stripped(target).copy(id = 0, idValue = plan.id, groupId = groupId)
}

/**
 * Копии группы, переписанные значениями формы: время, показания и заметка — новые,
 * привязка к дню и метка — прежние, автоматика — своей закладки.
 *
 * Копии приходят по метке из базы (`ItemsRepository.getMeasurementGroup`), и среди них
 * бывает копия закладки, завершённой после записи: среди целей её нет, автоматики её
 * тут не знают, и она берёт значения как есть — правило, которым её записали, к ней уже
 * применили, а показание прибора у неё то же, что у остальных.
 */
internal fun updatedCopies(
    copies: List<Measurement>,
    targets: List<MeasurementTarget>,
    template: Measurement,
): List<Measurement> = copies.map { copy ->
    val target = targets.firstOrNull { it.plan?.id == copy.idValue }
    val values = if (target != null) template.stripped(target) else template
    copy.copy(
        time = values.time,
        temp = values.temp,
        damp = values.damp,
        over = values.over,
        airingCount = values.airingCount,
        airingTime = values.airingTime,
        note = values.note,
    )
}

private fun Measurement.stripped(target: MeasurementTarget): Measurement = copy(
    over = if (target.autoTurn) null else over,
    airingCount = if (target.autoAiring) null else airingCount,
    airingTime = if (target.autoAiring) null else airingTime,
)
