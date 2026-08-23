package ru.zaroslikov.incubator.domain.incubation

import ru.zaroslikov.incubator.domain.model.Value

/**
 * Режим инкубации по умолчанию для вида птицы: по одной строке на каждый день.
 * Значения ориентировочные, пользователь правит их в интерфейсе.
 */
fun setIncubator(typeIncubator: String): MutableList<Value> {
    val incubator: MutableList<Value> = mutableListOf()
    when (typeIncubator) {
        "Курицы" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.0,
                    damp = 70.0,
                    over = "нет",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.0,
                    damp = 70.0,
                    over = "нет",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.0,
                    damp = 70.0,
                    over = "нет",
                    airing = "2 раза по 5 мин",
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
                    over = "3-4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.8,
                    damp = 65.0,
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.8,
                    damp = 65.0,
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.6,
                    damp = 70.0,
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.6,
                    damp = 70.0,
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.6,
                    damp = 70.0,
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.6,
                    damp = 70.0,
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.6,
                    damp = 70.0,
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.6,
                    damp = 70.0,
                    over = "10",
                    airing = "2 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.3,
                    damp = 75.0,
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.3,
                    damp = 75.0,
                    over = "нет",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 29,
                    temp = 37.3,
                    damp = 75.0,
                    over = "нет",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 30,
                    temp = 37.3,
                    damp = 75.0,
                    over = "нет",
                    airing = "3 раза по 45 мин",
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
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 55.0,
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.7,
                    damp = 55.0,
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 65.0,
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 65.0,
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.5,
                    damp = 65.0,
                    over = "нет",
                    airing = "нет",
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
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 38.0,
                    damp = 60.0,
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.7,
                    damp = 45.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.7,
                    damp = 65.0,
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.5,
                    damp = 65.0,
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.5,
                    damp = 65.0,
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.5,
                    damp = 65.0,
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.5,
                    damp = 65.0,
                    over = "нет",
                    airing = "нет",
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
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 38.0,
                    damp = 75.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 38.0,
                    damp = 75.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 38.0,
                    damp = 75.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 38.0,
                    damp = 75.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 38.0,
                    damp = 75.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.8,
                    damp = 60.0,
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = 37.8,
                    damp = 60.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = 37.5,
                    damp = 90.0,
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = 37.5,
                    damp = 90.0,
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = 37.5,
                    damp = 90.0,
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = 37.5,
                    damp = 90.0,
                    over = "нет",
                    airing = "нет",
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
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = 37.9,
                    damp = 66.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = 37.5,
                    damp = 60.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = 37.3,
                    damp = 47.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = 37.3,
                    damp = 47.0,
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = 37.0,
                    damp = 70.0,
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }
    }
}
