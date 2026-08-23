package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(
    tableName = "Batch_species",
    foreignKeys = [ForeignKey(
        entity = BatchEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("idPT"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class SpeciesEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    var species: String,
    @ColumnInfo(name = "idPT")
    var idPT: Long
)
