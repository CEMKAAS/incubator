package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.ui.daysBetween
import java.util.Date

/**
 * Номер дня инкубации на дату [on]: целые сутки от [start] плюс один, не меньше первого
 * и не больше срока [total], если срок известен.
 *
 * Одна функция на всех, кто спрашивает «какой сегодня день»: шторка закладки
 * (`BatchDetailViewModel.load`) и шторка замеров инкубатора, которая кладёт показание в
 * строку *сегодняшнего* дня каждой идущей закладки. Расходиться им нельзя ни на день —
 * замер, записанный из инкубатора, должен лечь в ту же строку, которую закладка
 * покажет как «Замеры за сегодня», иначе он окажется во вчера или в завтра.
 *
 * Границ дня час закладки не двигает (`Batch.time`, см. корневой `CLAUDE.md`): день —
 * это строка расписания, и считается он датами. Дата, которую не удалось разобрать
 * (`start == null`), даёт первый день, а не нулевой: так вели себя и прежние расчёты.
 */
internal fun incubationDay(start: Date?, total: Int?, on: Date): Int {
    val elapsed = if (start == null) 0 else daysBetween(start, on)
    val day = (elapsed + 1).coerceAtLeast(1)
    return if (total != null) day.coerceAtMost(total) else day
}
