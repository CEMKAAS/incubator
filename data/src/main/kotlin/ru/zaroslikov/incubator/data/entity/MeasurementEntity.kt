package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Таблица появилась в четвёртой версии схемы (MIGRATION_3_4); в пятой `temp` и `damp`
 * стали REAL (MIGRATION_4_5).
 *
 * Ссылается на день расписания ([ValueEntity]), а не на закладку с календарной датой:
 * дату начала закладки правят, дату на телефоне тоже, и замер, привязанный к числу,
 * после этого оказался бы не в том дне инкубации. Строки `Batch_value` при правке
 * закладки не пересоздаются, поэтому их `id` живёт столько же, сколько сама закладка.
 *
 * Каскад доходит сюда через две ступени: `Batch` → `Batch_value` → `Batch_measurement`.
 *
 * `over`, `airingCount` и `airingTime` стали INTEGER в девятой (MIGRATION_8_9), и
 * проветривание там же разделилось надвое: сколько раз и по сколько минут. Раньше
 * `over` был отметкой «перевернул», а `airing` хранил минуты одного проветривания —
 * записать два проветривания за раз было нечем.
 */
@Entity(
    tableName = "Batch_measurement",
    foreignKeys = [ForeignKey(
        entity = ValueEntity::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("idValue"),
        onDelete = ForeignKey.CASCADE
    )],
    // Индекс по внешнему ключу: по нему идёт и выборка, и каскадное удаление. Без него
    // SQLite на каждую удаляемую родительскую строку сканирует эту таблицу целиком.
    indices = [Index("idValue")]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,
    @ColumnInfo(name = "idValue")
    var idValue: Long,
    var time: String,
    var temp: Double?,
    var damp: Double?,
    var over: Int?,
    var airingCount: Int?,
    var airingTime: Int?,
    var note: String,
    /**
     * Метка замера по инкубатору (v17): одна на все его копии по идущим закладкам,
     * `NULL` у замера, внесённого внутри закладки. Индекса нет нарочно: выборка идёт по
     * `idValue`, а группировка — уже в памяти, по замерам одного дня.
     */
    var groupId: String? = null,
)
