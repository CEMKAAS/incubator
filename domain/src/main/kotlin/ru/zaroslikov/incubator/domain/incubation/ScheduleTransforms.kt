package ru.zaroslikov.incubator.domain.incubation

import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value

fun setIdPT(list: MutableList<Value>, idPT: Long): MutableList<Value> {
    list.forEach {
        it.id = 0
        it.idPT = idPT
    }
    return list
}

fun setIdPTTime(list: MutableList<Time>, idPT: Long): MutableList<Time> {
    list.forEach {
        it.id = 0
        it.idPT = idPT
    }
    return list
}

fun setAutoIncubator(
    list: MutableList<Value>,
    airing: Boolean,
    over: Boolean
): MutableList<Value> {

    if (airing) list.forEach { it.airing = "Авто" }
    if (over) list.forEach { it.over = "Авто" }

    return list
}
