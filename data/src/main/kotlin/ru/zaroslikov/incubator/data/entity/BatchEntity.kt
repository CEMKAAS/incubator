package ru.zaroslikov.incubator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    )],
    // Индекс по внешнему ключу: по нему идёт и выборка, и каскадное удаление. Без него
    // SQLite на каждую удаляемую родительскую строку сканирует эту таблицу целиком.
    indices = [Index("incubatorId")]
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
    //
    // `Breed` — порода, одна на закладку. С пятнадцатой по семнадцатую версию породы
    // лежали строками отдельной таблицы `Batch_breed`, по нескольку на закладку, а
    // колонка стояла пустой (убрать колонку в SQLite значит пересоздать таблицу с пятью
    // дочерними); восемнадцатая вернула одно поле, см. MIGRATION_17_18.
    @ColumnInfo(name = "Breed")
    val breed: String = "",
    @ColumnInfo(name = "Price")
    val price: Int = 0,
    @ColumnInfo(name = "PricePerEgg")
    val pricePerEgg: Boolean = true,
    // Добавлены в седьмой версии схемы, см. MIGRATION_6_7 — итог завершения закладки.
    @ColumnInfo(name = "EndReason")
    val endReason: String = "",
    @ColumnInfo(name = "ChickPrice")
    val chickPrice: Int = 0,
    @ColumnInfo(name = "ChickPricePerHead")
    val chickPricePerHead: Boolean = true,
    // Добавлена в восьмой версии схемы, см. MIGRATION_7_8. Имя `Archive` занято под
    // «инкубация окончена» ещё с первой версии, поэтому «убрана в архив» здесь `Hidden`.
    @ColumnInfo(name = "Hidden")
    val hidden: Boolean = false,
    // Добавлены в десятой версии схемы, см. MIGRATION_9_10.
    @ColumnInfo(name = "Time")
    val time: String = "",
    @ColumnInfo(name = "Egg_rejected")
    val eggRejected: Int = 0,
    // Добавлены в девятнадцатой версии схемы, см. MIGRATION_18_19 — потребление и тариф
    // закладки. Пустые берутся из инкубатора.
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
    // Добавлена в двадцатой версии схемы, см. MIGRATION_19_20: час, когда закладку
    // закончили, — конец счёта за свет.
    @ColumnInfo(name = "TimeEnd")
    val timeEnd: String = "",
)
