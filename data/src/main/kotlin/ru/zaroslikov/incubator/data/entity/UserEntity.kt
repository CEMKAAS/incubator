package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Владелец хозяйства — единственная строка на всю базу.
 *
 * Ключ не автоинкрементный и всегда равен [SINGLE_ROW_ID]: профиль в приложении один,
 * своего сервера и учётных записей у приложения нет — вход через VK ID лишь заполняет
 * эту же строку. Фиксированный ключ делает сохранение обычной вставкой с заменой — не
 * нужно ни искать прежнюю строку, ни бояться, что их накопится две.
 *
 * Строки может не быть вовсе: профиль необязателен, и до первого сохранения таблица
 * пуста. Пустая таблица и пустой профиль значат одно и то же, поэтому репозиторий
 * отдаёт наружу `User()`, а не `null`.
 *
 * Колонки `Farm`, `City`, `Avatar`, `VkUserId` — схема 19 (MIGRATION_18_19). У
 * текстовых `defaultValue` совпадает с `DEFAULT ''` миграции: Room сверяет его со
 * схемой открытой базы.
 */
@Entity(tableName = "User")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: Long = SINGLE_ROW_ID,
    @ColumnInfo(name = "Name")
    val name: String = "",
    @ColumnInfo(name = "Farm", defaultValue = "''")
    val farm: String = "",
    @ColumnInfo(name = "City", defaultValue = "''")
    val city: String = "",
    /** JPEG аватара, не больше 256 точек по стороне; `null` — фото нет. */
    @ColumnInfo(name = "Avatar", typeAffinity = ColumnInfo.BLOB)
    val avatar: ByteArray? = null,
    /** Идентификатор пользователя VK; `null` — VK ID не привязан. */
    @ColumnInfo(name = "VkUserId")
    val vkUserId: Long? = null,
) {
    // ByteArray в data class сравнивается по ссылке — см. то же в доменном User.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserEntity) return false
        return id == other.id &&
            name == other.name &&
            farm == other.farm &&
            city == other.city &&
            vkUserId == other.vkUserId &&
            avatar.contentEquals(other.avatar)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + farm.hashCode()
        result = 31 * result + city.hashCode()
        result = 31 * result + (vkUserId?.hashCode() ?: 0)
        result = 31 * result + (avatar?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val SINGLE_ROW_ID = 1L
    }
}
