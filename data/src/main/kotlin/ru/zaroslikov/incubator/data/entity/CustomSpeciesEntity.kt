package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Свой вид птицы — таблица появилась в тринадцатой версии схемы (MIGRATION_12_13).
 *
 * Хранит только имя: срок инкубации и режим по дням лежат в [CustomSpeciesDayEntity],
 * по строке на день, потому что дней у вида столько, сколько человек описал, и в
 * колонки их не уложить.
 *
 * Уникальный индекс по `Name` — не аккуратность, а условие целостности. Закладка
 * ссылается на вид по имени (`Batch.Type`, чип `Batch_species`), ровно как на
 * встроенный, и два вида с одним именем означали бы, что по закладке нельзя сказать,
 * чей режим её породил.
 */
@Entity(
    tableName = "Custom_species",
    indices = [Index(value = ["Name"], unique = true)]
)
data class CustomSpeciesEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "Name")
    val name: String,
)
