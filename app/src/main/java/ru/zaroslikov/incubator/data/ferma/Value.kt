package ru.zaroslikov.incubator.data.ferma

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.zaroslikov.incubator.data.ferma.IncubatorTable


@Entity(
    tableName = "Incubator_value",
    foreignKeys = [ForeignKey(
        entity = IncubatorTable::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Value(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    val day: Int,
    var temp: String,
    var damp: String,
    var over: String,
    var airing: String,
    var note: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long
)
