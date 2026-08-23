package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Закладка. До второй версии схемы жила в таблице `Incubator` — оттуда и старые
 * имена колонок: их менять нельзя, иначе миграция перестанет сходиться.
 */
@Entity(
    tableName = "Batch",
    foreignKeys = [ForeignKey(
        entity = IncubatorEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("incubatorId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class BatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "Name")
    val title: String,
    @ColumnInfo(name = "Type")
    val type: String,
    @ColumnInfo(name = "Date")
    val data: String,
    @ColumnInfo(name = "Egg_all")
    val eggAll: Int,
    @ColumnInfo(name = "Egg_all_end")
    val eggAllEND: Int,
    @ColumnInfo(name = "Airing")
    val airing: String,
    @ColumnInfo(name = "Overturn")
    val over: String,
    @ColumnInfo(name = "Archive")
    var arhive: String,
    @ColumnInfo(name = "Date_end")
    val dateEnd: String,
    @ColumnInfo(name = "note")
    val note: String,
    @ColumnInfo(name = "incubatorId")
    val incubatorId: Long = 0,
    // Добавлены в третьей версии схемы, см. MIGRATION_2_3.
    @ColumnInfo(name = "Breed")
    val breed: String = "",
    @ColumnInfo(name = "Price")
    val price: Int = 0,
    @ColumnInfo(name = "PricePerEgg")
    val pricePerEgg: Boolean = true,
)
