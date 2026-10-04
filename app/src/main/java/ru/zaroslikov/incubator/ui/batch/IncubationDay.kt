package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.incubator.atTimeOf
import ru.zaroslikov.incubator.ui.parseDate
import java.util.Date

/**
 * Номер дня инкубации на момент [on]: целые сутки от [start] плюс один, не меньше первого
 * и не больше срока [total], если срок известен.
 *
 * Одна функция на всех, кто спрашивает «какой сегодня день»: шторка закладки
 * (`BatchDetailViewModel.load`), карточка на экране инкубатора (`batchProgress`) и шторка
 * замеров инкубатора. Расходиться им нельзя ни на день — замер, записанный из инкубатора,
 * должен лечь в строку, которую закладка покажет как «Замеры за сегодня».
 *
 * Для идущей закладки [start] — [batchStartMoment], дата **и час** закладки, а [on] —
 * текущий момент: день инкубации начинается в час закладки (заложили в 10:00 — 21-й день
 * начнётся в 10:00, в 08:00 того числа идёт ещё 20-й), так же, как считается срок вывода
 * (`batchFinishMoment`). Дата, которую не удалось разобрать (`start == null`), даёт первый
 * день, а не нулевой.
 */
internal fun incubationDay(start: Date?, total: Int?, on: Date): Int {
    val elapsed = if (start == null) 0 else daysBetween(start, on)
    val day = (elapsed + 1).coerceAtLeast(1)
    return if (total != null) day.coerceAtMost(total) else day
}

/**
 * Момент закладки — дата из [Batch.data] в час из [Batch.time]. От него начинается каждый
 * день инкубации и отсчитывается срок вывода. Пустое время (закладки до schema v10)
 * оставляет полночь — прежнее поведение.
 */
internal fun batchStartMoment(batch: Batch): Date? =
    parseDate(batch.data)?.atTimeOf(batch.time)
