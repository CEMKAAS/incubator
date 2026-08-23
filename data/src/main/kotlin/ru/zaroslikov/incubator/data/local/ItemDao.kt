package ru.zaroslikov.incubator.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.ValueEntity

/** Сортировка закладок по дате: `Date` хранится текстом `dd.MM.yyyy`, поэтому его
 *  приходится пересобирать в ISO прямо в SQL. */
private const val BATCH_ORDER =
    "ORDER BY strftime('%Y-%m-%d', substr(Date, 7, 4) || '-' || substr(Date, 4, 2) || '-' || substr(Date, 1, 2))"

@Dao
interface ItemDao {

    // --- Инкубаторы ---

    @Query("SELECT * from Incubator ORDER BY _id")
    fun getAllIncubators(): Flow<List<IncubatorEntity>>

    @Query("SELECT * from Incubator Where _id=:id")
    fun getIncubator(id: Long): Flow<IncubatorEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncubator(incubator: IncubatorEntity): Long

    @Update
    suspend fun updateIncubator(incubator: IncubatorEntity)

    @Delete
    suspend fun deleteIncubator(incubator: IncubatorEntity)

    /** Ранее введённые бренды и модели — подсказки в форме инкубатора. */
    @Query("SELECT DISTINCT Brand FROM Incubator WHERE Brand != '' ORDER BY Brand COLLATE NOCASE")
    fun getUsedBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT Model FROM Incubator WHERE Model != '' ORDER BY Model COLLATE NOCASE")
    fun getUsedModels(): Flow<List<String>>

    // --- Закладки ---

    @Query("SELECT * from Batch $BATCH_ORDER")
    fun getAllBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * from Batch Where incubatorId=:incubatorId $BATCH_ORDER")
    fun getBatchesFor(incubatorId: Long): Flow<List<BatchEntity>>

    @Query("SELECT * from Batch Where _id=:id")
    fun getBatch(id: Long): Flow<BatchEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Delete
    suspend fun deleteBatch(batch: BatchEntity)

    @Query("SELECT * from Batch Where TYPE =:type and Archive = 1")
    suspend fun getArchivedBatches(type: String): List<BatchEntity>

    // --- Дни закладки ---

    @Query("SELECT * from Batch_value Where idPT=:id")
    fun getBatchValues(id: Long): Flow<List<ValueEntity>>

    @Query("SELECT * from Batch_value Where idPT=:id")
    fun getValue(id: Long): Flow<ValueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertValue(value: ValueEntity)

    @Update
    suspend fun updateValue(value: ValueEntity)

    @Query("SELECT * from Batch_value Where idPT=:id and day=:day")
    fun getBatchValueForDay(id: Long, day: Int): Flow<ValueEntity>

    @Query("SELECT * from Batch_value Where idPT =:idPT")
    suspend fun getValueArchive(idPT: Long): List<ValueEntity>

    // --- Замеры ---

    @Query("SELECT * from Batch_measurement Where idValue=:idValue ORDER BY time DESC, id DESC")
    fun getMeasurements(idValue: Long): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurement(measurement: MeasurementEntity)

    @Update
    suspend fun updateMeasurement(measurement: MeasurementEntity)

    @Delete
    suspend fun deleteMeasurement(measurement: MeasurementEntity)

    // --- Напоминания ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTime(time: TimeEntity)

    @Update
    suspend fun updateTime(time: TimeEntity)

    @Delete
    suspend fun deleteTime(time: TimeEntity)

    @Query("SELECT * from Batch_time Where idPT=:id")
    suspend fun getTimeList(id: Long): List<TimeEntity>

    // --- Виды птицы ---

    @Query("SELECT * from Batch_species")
    fun getAllSpecies(): Flow<List<SpeciesEntity>>

    @Query("SELECT * from Batch_species Where idPT=:idPT")
    suspend fun getSpeciesList(idPT: Long): List<SpeciesEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpecies(species: SpeciesEntity)

    @Delete
    suspend fun deleteSpecies(species: SpeciesEntity)
}
