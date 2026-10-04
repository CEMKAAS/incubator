package ru.zaroslikov.incubator.domain.incubation

import ru.zaroslikov.incubator.domain.model.Value

/**
 * Отдаёт перевороты и проветривания автоматике инкубатора: их норма стирается в `null`.
 *
 * До девятой версии схемы в эти поля писалось слово «Авто» — колонки были текстовые.
 * Теперь они числовые, и «нормы нет» выражается отсутствием числа. Ноль сюда не
 * годится: он значит «не переворачивать вовсе», и счётчик дня показал бы «план
 * выполнен» вместо «на автомате». Само же «эта закладка шла на автоматике» хранится
 * в `Batch.over` / `Batch.airing` — по ним интерфейс и подписывает счётчики.
 */
fun setAutoIncubator(
    list: MutableList<Value>,
    airing: Boolean,
    over: Boolean
): MutableList<Value> {

    if (airing) list.forEach {
        it.airingCount = null
        it.airingTime = null
    }
    if (over) list.forEach { it.over = null }

    return list
}
