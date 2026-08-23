package ru.zaroslikov.incubator.domain.model

/**
 * План на один день инкубации: какими должны быть температура, влажность,
 * перевороты и проветривания.
 *
 * [temp] и [damp] — числа, а не строки: их сравнивают с фактическими замерами
 * ([Measurement]) и считают отклонение, а текстом это делать нечем. `null` означает
 * «не задано» — пользователь может очистить поле в редакторе дня.
 *
 * [over] и [airing], наоборот, остаются строками: там живут «2-3», «Авто»,
 * «2 раза по 5 минут» — не числа.
 */
data class Value(
    var id: Long = 0,
    val day: Int,
    var temp: Double?,
    var damp: Double?,
    var over: String,
    var airing: String,
    var note: String,
    var idPT: Long
)
