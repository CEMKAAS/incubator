package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Таблица появилась в шестой версии схемы (MIGRATION_5_6).
 *
 * Ссылается прямо на закладку, а не на день расписания ([ValueEntity]), в отличие от
 * [MeasurementEntity]: выбраковка — событие самой закладки, её итог нужен там, где
 * считают оставшиеся яйца, и тянуть его через строку дня незачем. День инкубации
 * лежит здесь же колонкой.
 *
 * Уникальный индекс по паре `idPT` + `day`: овоскопирование в один день проводят
 * один раз, и вторая запись за тот же день означала бы, что итог посчитали дважды.
 */
@Entity(
    tableName = "Batch_candling",
    foreignKeys = [ForeignKey(
        entity = BatchEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["idPT", "day"], unique = true)]
)
data class CandlingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "idPT")
    val idPT: Long,
    @ColumnInfo(name = "day")
    val day: Int,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "rejected")
    val rejected: Int,
)
