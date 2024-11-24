package ru.zaroslikov.incubator.data.ferma

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.zaroslikov.incubator.data.ferma.IncubatorTable


@Entity(
    tableName = "Incubator_time",
    foreignKeys = [ForeignKey(
        entity = IncubatorTable::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Time(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    var time: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long
)
