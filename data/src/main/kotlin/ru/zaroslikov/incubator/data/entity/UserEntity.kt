package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Владелец хозяйства — единственная строка на всю базу.
 *
 * Ключ не автоинкрементный и всегда равен [SINGLE_ROW_ID]: пользователь в приложении
 * один, аккаунтов нет и не будет, пока приложение остаётся offline-only. Фиксированный
 * ключ делает сохранение обычной вставкой с заменой — не нужно ни искать прежнюю
 * строку, ни бояться, что их накопится две.
 *
 * Строки может не быть вовсе: имя — необязательное поле, и до первого сохранения
 * таблица пуста. Пустая таблица и пустое имя значат одно и то же, поэтому репозиторий
 * отдаёт наружу `User("")`, а не `null`.
 */
@Entity(tableName = "User")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: Long = SINGLE_ROW_ID,
    @ColumnInfo(name = "Name")
    val name: String = "",
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
    }
}
