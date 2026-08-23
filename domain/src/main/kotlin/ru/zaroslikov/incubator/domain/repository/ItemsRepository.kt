package ru.zaroslikov.incubator.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value


interface ItemsRepository {

    // --- Инкубаторы (устройства) ---
    fun getAllIncubators(): Flow<List<Incubator>>
    fun getIncubator(id: Long): Flow<Incubator>
    suspend fun insertIncubator(incubator: Incubator): Long
    suspend fun updateIncubator(incubator: Incubator)
    suspend fun deleteIncubator(incubator: Incubator)

    /** Ранее введённые бренды и модели — подсказки в форме инкубатора. */
    fun getUsedBrands(): Flow<List<String>>
    fun getUsedModels(): Flow<List<String>>

    // --- Закладки ---
    fun getAllBatches(): Flow<List<Batch>>
    fun getBatchesFor(incubatorId: Long): Flow<List<Batch>>
    fun getBatch(id: Long): Flow<Batch>
    suspend fun insertBatch(batch: Batch): Long
    suspend fun updateBatch(batch: Batch)
    suspend fun deleteBatch(batch: Batch)
    suspend fun getArchivedBatches(type: String): List<Batch>

    // --- Дни закладки ---
    fun getBatchValues(idPT: Long): Flow<List<Value>>
    fun getValue(id: Long): Flow<Value>
    suspend fun insertValue(value: Value)
    suspend fun updateValue(value: Value)
    fun getBatchValueForDay(idPT: Long, day: Int): Flow<Value>
    suspend fun getValueArchive(idPT: Long): List<Value>

    // --- Замеры закладки ---

    /** Замеры одного дня расписания ([Value.id]); новые сверху. */
    fun getMeasurements(idValue: Long): Flow<List<Measurement>>
    suspend fun insertMeasurement(measurement: Measurement)
    suspend fun updateMeasurement(measurement: Measurement)
    suspend fun deleteMeasurement(measurement: Measurement)

    // --- Напоминания ---
    suspend fun insertTime(time: Time)
    suspend fun updateTime(time: Time)
    suspend fun deleteTime(time: Time)
    suspend fun getTimeList(id: Long): List<Time>

    // --- Виды птицы в закладке ---
    fun getAllSpecies(): Flow<List<Species>>
    suspend fun getSpeciesList(idPT: Long): List<Species>
    suspend fun insertSpecies(species: Species)
    suspend fun deleteSpecies(species: Species)
}
