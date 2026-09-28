package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * День своего вида: тот же план дня, что и в [ValueEntity], плюс отметка
 * овоскопирования.
 *
 * Четыре величины плана названы и типизированы как в `Batch_value` намеренно — это
 * одна и та же величина на двух этапах жизни: здесь она образец вида, там запечённая
 * строка расписания закладки. Смысл `null` тот же: «нормы нет» (поле очистили либо всё
 * делает автоматика), ноль — норма «не делать».
 *
 * `candling` — флаг здесь, а не отдельная таблица дней овоскопирования: у встроенного
 * вида режим по дням и дни овоскопирования тоже задаются вместе, и разнесённые по двум
 * таблицам они рано или поздно разъехались бы.
 *
 * Внешний ключ смотрит прямо в вид: день сам по себе ничего не значит, и удаление вида
 * уносит его дни каскадом. Уникальный индекс по паре `speciesId` + `day` — как у
 * `Batch_candling` по `idPT` + `day`: день инкубации у вида один, и вторая строка за
 * тот же день означала бы два разных режима на один день.
 */
@Entity(
    tableName = "Custom_species_day",
    foreignKeys = [ForeignKey(
        entity = CustomSpeciesEntity::class,
        parentColumns = arrayOf("_id"),
        childColumns = arrayOf("speciesId"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["speciesId", "day"], unique = true)]
)
data class CustomSpeciesDayEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "speciesId")
    val speciesId: Long,
    val day: Int,
    val temp: Double?,
    val damp: Double?,
    val over: Int?,
    val airingCount: Int?,
    val airingTime: Int?,
    val candling: Boolean = false,
)
