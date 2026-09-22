package ru.zaroslikov.incubator.domain.incubation

import ru.zaroslikov.incubator.domain.model.Value

/**
 * Режим инкубации по умолчанию для вида птицы: по одной строке на каждый день.
 * Значения ориентировочные, пользователь правит их в интерфейсе.
 *
 * До девятой версии схемы перевороты и проветривания жили здесь текстом — «2-3»,
 * «нет», «2 раза по 5 минут». Теперь это числа: [Value.over] — сколько раз за день
 * переворачивать, [Value.airingCount] и [Value.airingTime] — сколько раз проветривать
 * и по сколько минут одно проветривание. Из диапазонов взята **верхняя** граница
 * («2-3» → 3, «4-6» → 6): план считается выполненным, когда сделано столько, сколько
 * рекомендация просит максимум. «нет» стало нулём, а не `null`: «не проветривать» —
 * это тоже норма, а `null` здесь не встречается вовсе и означает автоматику
 * инкубатора (см. [setAutoIncubator]).
 */
internal fun setIncubator(typeIncubator: String): MutableList<Value> {
    val incubator: MutableList<Value> = mutableListOf()
    when (typeIncubator) {
        "Курицы" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.0,
                    damp = 70.0,
                    over = 0,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.0,
                    damp = 70.0,
                    over = 0,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.0,
                    damp = 70.0,
                    over = 0,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Гуси" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 38.0,
                    damp = 65.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.8,
                    damp = 65.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.8,
                    damp = 65.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.6,
                    damp = 70.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.6,
                    damp = 70.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.6,
                    damp = 70.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.6,
                    damp = 70.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.6,
                    damp = 70.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.6,
                    damp = 70.0,
                    over = 10,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.3,
                    damp = 75.0,
                    over = 10,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.3,
                    damp = 75.0,
                    over = 0,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 29,
                    temp = 37.3,
                    damp = 75.0,
                    over = 0,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 30,
                    temp = 37.3,
                    damp = 75.0,
                    over = 0,
                    airingCount = 3,
                    airingTime = 45,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }


        "Перепела" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 38.0,
                    damp = 55.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 55.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.7,
                    damp = 55.0,
                    over = 6,
                    airingCount = 1,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 65.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 65.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.5,
                    damp = 65.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Индюки" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 38.0,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.7,
                    damp = 45.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.7,
                    damp = 65.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.5,
                    damp = 65.0,
                    over = 4,
                    airingCount = 4,
                    airingTime = 10,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.5,
                    damp = 65.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.5,
                    damp = 65.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.5,
                    damp = 65.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Утки" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 38.0,
                    damp = 75.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.8,
                    damp = 60.0,
                    over = 4,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.8,
                    damp = 60.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.5,
                    damp = 90.0,
                    over = 6,
                    airingCount = 2,
                    airingTime = 15,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.5,
                    damp = 90.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.5,
                    damp = 90.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.5,
                    damp = 90.0,
                    over = 0,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        else -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.9,
                    damp = 66.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 0,
                    airingTime = 0,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 60.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 47.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 47.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 20,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.0,
                    damp = 70.0,
                    over = 3,
                    airingCount = 2,
                    airingTime = 5,
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }
    }
}
