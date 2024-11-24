package ru.zaroslikov.incubator.data.ferma

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Incubator")
data class IncubatorTable(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "Name")
    val title: String, // название
    @ColumnInfo(name = "Type")
    val type: String, // тип
    @ColumnInfo(name = "Date")
    val data: String,  // Дата начала проекта
    @ColumnInfo(name = "Egg_all")
    val eggAll: Int, // месяц
    @ColumnInfo(name = "Egg_all_end")
    val eggAllEND: Int,
    @ColumnInfo(name = "Airing")
    val airing: String, //не авто = 0, авто = 1
    @ColumnInfo(name = "Overturn")
    val over: String,  //не авто = 0, авто = 1
    @ColumnInfo(name = "Archive")
    var arhive: String, //не архив =0, Архив = 1
    @ColumnInfo(name = "Date_end")
    val dateEnd: String,
    @ColumnInfo(name = "note")
    val note: String,
)





