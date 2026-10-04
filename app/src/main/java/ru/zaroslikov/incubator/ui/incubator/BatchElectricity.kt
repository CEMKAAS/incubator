package ru.zaroslikov.incubator.ui.incubator

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.stats.ElectricityCost
import ru.zaroslikov.incubator.domain.stats.PoweredRun
import ru.zaroslikov.incubator.domain.stats.electricityCosts
import ru.zaroslikov.incubator.ui.batch.batchStartMoment
import ru.zaroslikov.incubator.ui.parseDate
import java.time.ZoneId
import java.util.Date

/**
 * Электричество закладок — то, что «Финансы» прибавляют к расходу. Здесь только перевод закладок
 * во временные промежутки; арифметика и деление общих часов — в `:domain` ([electricityCosts]).
 *
 * Промежуток начинается в момент закладки ([batchStartMoment], дата **и час**) и кончается:
 * - у идущей — сейчас [now];
 * - у завершённой — в день [Batch.dateEnd] в час [Batch.timeEnd] из диалога завершения; где его
 *   нет (прерванные досрочно, завершённые до появления поля) — в час закладки. Без даты окончания
 *   закладка в счёт не попадает.
 *
 * [batches] — **без** убранных в архив: архивная закладка не делит общие часы, соседки платят за
 * них сами. Настройки закладки — поверх настроек инкубатора ([PowerSettings.over]).
 */
internal fun batchElectricity(
    batches: List<Batch>,
    incubators: Map<Long, Incubator>,
    now: Date = Date(),
    zone: ZoneId = ZoneId.systemDefault(),
): Map<Long, ElectricityCost> {
    val runs = batches.mapNotNull { batch ->
        val (start, end) = batchSpan(batch, now) ?: return@mapNotNull null
        val device = incubators[batch.incubatorId]?.power ?: PowerSettings()
        PoweredRun(
            batchId = batch.id,
            deviceId = batch.incubatorId,
            start = start.time,
            end = end.time,
            power = batch.power.over(device),
        )
    }
    return electricityCosts(runs, zone)
}

/**
 * Промежуток, за который закладке считают свет: от момента закладки до [now] у идущей и
 * до часа выключения у завершённой (см. [batchElectricity]). `null` — начало или конец
 * не разобрать.
 */
internal fun batchSpan(batch: Batch, now: Date = Date()): Pair<Date, Date>? {
    val start = batchStartMoment(batch) ?: return null
    val end = if (batch.status == BatchStatus.Active) now
    else parseDate(batch.dateEnd)?.atTimeOf(batch.timeEnd.ifBlank { batch.time }) ?: return null
    return start to end
}

/**
 * Сколько целых часов закладка проработала — тот же промежуток, по которому считают свет,
 * так что часы и киловатт-часы в справке говорят об одном и том же отрезке.
 */
internal fun batchRunHours(batch: Batch, now: Date = Date()): Int? =
    batchSpan(batch, now)?.let { (start, end) ->
        ((end.time - start.time) / 3_600_000L).toInt().coerceAtLeast(0)
    }

/**
 * Свет одной только что завершённой закладки — для поздравления.
 *
 * Считается так же, как в «Финансах»: соседи по инкубатору — его закладки без
 * убранных в архив — делят с ней общие часы. Сама закладка в счёт входит, даже если
 * её только что убрали в архив тем же сохранением («Убрать в архив» из меню
 * карточки): поздравляют именно её, и сумма должна совпасть с той, что «Финансы»
 * показывали минуту назад.
 *
 * Годится и для идущей закладки — её промежуток кончается «сейчас» ([now]): так считает
 * финансовая справка на «Обзоре» шторки закладки, и сумма там та же, что в «Финансах».
 */
internal fun electricityOfFinished(
    batch: Batch,
    incubator: Incubator?,
    neighbours: List<Batch>,
    now: Date = Date(),
): ElectricityCost? {
    val runs = neighbours.filter { !it.hidden && it.id != batch.id } + batch
    val incubators = incubator?.let { mapOf(it.id to it) }.orEmpty()
    return batchElectricity(runs, incubators, now)[batch.id]
}
