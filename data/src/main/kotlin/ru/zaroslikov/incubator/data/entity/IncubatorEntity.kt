package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Инкубатор как устройство. Закладки ([BatchEntity]) принадлежат ему. */
@Entity(tableName = "Incubator")
data class IncubatorEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "Name")
    val name: String,
    @ColumnInfo(name = "Capacity")
    val capacity: Int,
    @ColumnInfo(name = "Brand")
    val brand: String = "",
    @ColumnInfo(name = "Model")
    val model: String = "",
    @ColumnInfo(name = "Price")
    val price: Int = 0,
    @ColumnInfo(name = "Note")
    val note: String = "",
    @ColumnInfo(name = "AutoTurn")
    val autoTurn: Boolean,
    @ColumnInfo(name = "AutoAiring")
    val autoAiring: Boolean,
    // Добавлена в двенадцатой версии схемы, см. MIGRATION_11_12. Имя то же, что и у
    // такой же колонки закладки: и там, и тут это «убрано в архив», то есть с глаз.
    @ColumnInfo(name = "Hidden")
    val hidden: Boolean = false,
    // Добавлены в девятнадцатой версии схемы, см. MIGRATION_18_19 — потребление и тариф.
    // Те же пять колонок у закладки (BatchEntity).
    @ColumnInfo(name = "PowerWatts")
    val powerWatts: Int? = null,
    @ColumnInfo(name = "TariffDay")
    val tariffDay: Double? = null,
    @ColumnInfo(name = "TariffNight")
    val tariffNight: Double? = null,
    @ColumnInfo(name = "NightStart")
    val nightStart: String = "",
    @ColumnInfo(name = "NightEnd")
    val nightEnd: String = "",
)
