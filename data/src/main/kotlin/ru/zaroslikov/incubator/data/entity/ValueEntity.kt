package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * План на день инкубации.
 *
 * `temp` и `damp` — REAL с пятой версии схемы, см. MIGRATION_4_5; nullable, потому
 * что редактор дня позволяет очистить поле.
 *
 * `over`, `airingCount` и `airingTime` — INTEGER с девятой (MIGRATION_8_9): до неё
 * перевороты и проветривания были текстом («2-3», «2 раза по 5 минут», «Авто»), и
 * считать по ним было нечего. Тоже nullable, и `null` здесь значит не «пусто», а
 * «нормы нет»: поле очистили либо всё делает автоматика инкубатора. Ноль — это норма
 * «не делать».
 */
@Entity(
    tableName = "Batch_value",
    foreignKeys = [ForeignKey(
        entity = BatchEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )],
    // Индекс по внешнему ключу: по нему идёт и выборка, и каскадное удаление. Без него
    // SQLite на каждую удаляемую родительскую строку сканирует эту таблицу целиком.
    indices = [Index("idPT")]
)
data class ValueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    val day: Int,
    var temp: Double?,
    var damp: Double?,
    var over: Int?,
    var airingCount: Int?,
    var airingTime: Int?,
    var note: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long
)
