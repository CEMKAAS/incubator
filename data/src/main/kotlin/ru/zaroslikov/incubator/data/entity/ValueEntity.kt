package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


/**
 * План на день инкубации.
 *
 * `temp` и `damp` — REAL с пятой версии схемы, см. MIGRATION_4_5; nullable, потому
 * что редактор дня позволяет очистить поле.
 */
@Entity(
    tableName = "Batch_value",
    foreignKeys = [ForeignKey(
        entity = BatchEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class ValueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    val day: Int,
    var temp: Double?,
    var damp: Double?,
    var over: String,
    var airing: String,
    var note: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long
)
