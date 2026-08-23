package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(
    tableName = "Batch_time",
    foreignKeys = [ForeignKey(
        entity = BatchEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class TimeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    var time: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long,
    // Добавлена в третьей версии схемы, см. MIGRATION_2_3.
    @ColumnInfo(name = "note")
    var note: String = ""
)
