package ru.zaroslikov.incubator.domain.model

/**
 * Ежедневное напоминание закладки.
 *
 * [note] — текст уведомления. Пустая строка означает «текста нет»: уведомление
 * тогда показывает общий заголовок из work/Constants.kt.
 */
data class Time(
    var id: Long = 0,
    var time: String,
    var idPT: Long,
    var note: String = ""
)
