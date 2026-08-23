package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Batch

/**
 * Состояние формы закладки. Числа и флаги живут здесь строками и `Boolean`,
 * а в [Batch] уезжают в том виде, в каком их хранит база.
 */
data class BatchUiState(
    val id: Long = 0,
    val title: String = "",
    val type: String = "",
    val data: String = "",
    val eggAll: String = "",
    val eggAllEND: String = "",
    val airing: Boolean = false,
    val over: Boolean = false,
    val arhive: String = "",
    val dateEnd: String = "",
    val note: String = "",
    val incubatorId: Long = 0,
    val breed: String = "",
    /** Введённая стоимость; пустая строка — «не указана». */
    val price: String = "",
    val pricePerEgg: Boolean = true,
)

fun BatchUiState.toBatch(): Batch = Batch(
    id,
    title,
    type,
    data,
    eggAll.toIntOrNull() ?: 0,
    eggAllEND.toIntOrNull() ?: 0,
    airing.toString(),
    over.toString(),
    arhive,
    dateEnd,
    note,
    incubatorId,
    breed,
    price.toIntOrNull() ?: 0,
    pricePerEgg
)

fun Batch.toBatchUiState(): BatchUiState = BatchUiState(
    id,
    title,
    type,
    data,
    eggAll.toString(),
    eggAllEND.toString(),
    airing.toBoolean(),
    over.toBoolean(),
    arhive,
    dateEnd,
    note,
    incubatorId,
    breed,
    if (price == 0) "" else price.toString(),
    pricePerEgg
)
