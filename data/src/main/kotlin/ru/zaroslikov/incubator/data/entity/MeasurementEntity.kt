package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Таблица появилась в четвёртой версии схемы (MIGRATION_3_4); в пятой `temp` и `damp`
 * стали REAL (MIGRATION_4_5).
 *
 * Ссылается на день расписания ([ValueEntity]), а не на закладку с календарной датой:
 * дату начала закладки правят, дату на телефоне тоже, и замер, привязанный к числу,
 * после этого оказался бы не в том дне инкубации. Строки `Batch_value` при правке
 * закладки не пересоздаются, поэтому их `id` живёт столько же, сколько сама закладка.
 *
 * Каскад доходит сюда через две ступени: `Batch` → `Batch_value` → `Batch_measurement`.
 */
@Entity(
    tableName = "Batch_measurement",
    foreignKeys = [ForeignKey(
        entity = ValueEntity::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("idValue"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    @ColumnInfo(name = "idValue")
    var idValue: Long,
    var time: String,
    var temp: Double?,
    var damp: Double?,
    var over: String,
    var airing: String,
    var note: String,
)
