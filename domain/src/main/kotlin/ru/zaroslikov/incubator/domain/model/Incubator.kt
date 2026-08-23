package ru.zaroslikov.incubator.domain.model

/**
 * Инкубатор — физическое устройство. Все закладки ([Batch]) создаются внутри него.
 *
 * [name] и [capacity] обязательны при создании, как и флаги автоматики.
 * [capacity] равна нулю только у инкубатора, синтезированного при миграции с первой
 * версии базы: там этих данных попросту не было, и интерфейс просит их заполнить.
 * [price] — рубли, ноль означает «не указана».
 */
data class Incubator(
    val id: Long = 0,
    val name: String,
    val capacity: Int,
    val brand: String = "",
    val model: String = "",
    val price: Int = 0,
    val note: String = "",
    val autoTurn: Boolean,
    val autoAiring: Boolean,
)
