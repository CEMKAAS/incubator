package ru.zaroslikov.incubator.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Вид вместе с его днями — то, чем вид только и бывает полезен: срок инкубации это
 * число дней, а дни овоскопирования — отметки в них. Отдавать наружу вид без дней
 * значило бы заставить каждого спрашивающего сделать второй запрос.
 */
data class CustomSpeciesWithDays(
    @Embedded val species: CustomSpeciesEntity,
    @Relation(parentColumn = "_id", entityColumn = "speciesId")
    val days: List<CustomSpeciesDayEntity>,
)
