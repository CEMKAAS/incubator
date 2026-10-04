package ru.zaroslikov.incubator.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.zaroslikov.incubator.data.local.ItemDao
import ru.zaroslikov.incubator.data.mapper.toDomain
import ru.zaroslikov.incubator.data.mapper.toEntity
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

class OfflineItemsRepository(private val itemDao: ItemDao) : ItemsRepository {

    // --- Инкубаторы ---

    override fun getAllIncubators(): Flow<List<Incubator>> =
        itemDao.getAllIncubators().map { list -> list.map { it.toDomain() } }

    override fun getIncubator(id: Long): Flow<Incubator?> =
        itemDao.getIncubator(id).map { it?.toDomain() }

    override suspend fun insertIncubator(incubator: Incubator): Long =
        itemDao.insertIncubator(incubator.toEntity())

    override suspend fun updateIncubator(incubator: Incubator) =
        itemDao.updateIncubator(incubator.toEntity())

    override suspend fun deleteIncubator(incubator: Incubator) =
        itemDao.deleteIncubator(incubator.toEntity())

    override fun countIncubators(): Flow<Int> = itemDao.countIncubators()

    override fun getUsedBrands(): Flow<List<String>> = itemDao.getUsedBrands()

    override fun getUsedModels(): Flow<List<String>> = itemDao.getUsedModels()

    // --- Закладки ---

    override fun getAllBatches(): Flow<List<Batch>> =
        itemDao.getAllBatches().map { list -> list.map { it.toDomain() } }

    override fun getBatchesFor(incubatorId: Long): Flow<List<Batch>> =
        itemDao.getBatchesFor(incubatorId).map { list -> list.map { it.toDomain() } }

    override fun getBatch(id: Long): Flow<Batch?> =
        itemDao.getBatch(id).map { it?.toDomain() }

    override suspend fun insertBatch(batch: Batch): Long =
        itemDao.insertBatch(batch.toEntity())

    override suspend fun updateBatch(batch: Batch) =
        itemDao.updateBatch(batch.toEntity())

    override suspend fun updateBatchWithCandlings(
        batch: Batch,
        save: List<Candling>,
        delete: List<Candling>,
    ) = itemDao.updateBatchWithCandlings(
        batch.toEntity(),
        save.map { it.toEntity() },
        delete.map { it.toEntity() },
    )

    override suspend fun deleteBatch(batch: Batch) =
        itemDao.deleteBatch(batch.toEntity())

    override suspend fun getArchivedBatches(type: String): List<Batch> =
        itemDao.getArchivedBatches(type).map { it.toDomain() }

    override suspend fun insertBatchWithSchedule(
        batch: Batch,
        species: Species,
        days: List<Value>,
        times: List<Time>,
    ): Long = itemDao.insertBatchWithSchedule(
        batch.toEntity(),
        species.toEntity(),
        days.map { it.toEntity() },
        times.map { it.toEntity() },
    )

    // --- Дни закладки ---

    override fun getBatchValues(idPT: Long): Flow<List<Value>> =
        itemDao.getBatchValues(idPT).map { list -> list.map { it.toDomain() } }

    override fun getValue(id: Long): Flow<Value?> =
        itemDao.getValue(id).map { it?.toDomain() }

    override suspend fun insertValue(value: Value) = itemDao.insertValue(value.toEntity())

    override suspend fun updateValue(value: Value) = itemDao.updateValue(value.toEntity())

    override suspend fun updateSchedule(days: List<Value>) =
        itemDao.updateValues(days.map { it.toEntity() })

    override fun getBatchValueForDay(idPT: Long, day: Int): Flow<Value?> =
        itemDao.getBatchValueForDay(idPT, day).map { it?.toDomain() }

    override suspend fun getValueArchive(idPT: Long): List<Value> =
        itemDao.getValueArchive(idPT).map { it.toDomain() }

    // --- Замеры ---

    override fun getMeasurements(idValue: Long): Flow<List<Measurement>> =
        itemDao.getMeasurements(idValue).map { list -> list.map { it.toDomain() } }

    override fun getBatchMeasurements(batchId: Long): Flow<List<Measurement>> =
        itemDao.getBatchMeasurements(batchId).map { list -> list.map { it.toDomain() } }

    override suspend fun countMeasurements(batchId: Long): Int =
        itemDao.countMeasurements(batchId)

    override suspend fun insertMeasurement(measurement: Measurement) =
        itemDao.insertMeasurement(measurement.toEntity())

    override suspend fun updateMeasurement(measurement: Measurement) =
        itemDao.updateMeasurement(measurement.toEntity())

    override suspend fun deleteMeasurement(measurement: Measurement) =
        itemDao.deleteMeasurement(measurement.toEntity())

    override fun getMeasurementsForDays(valueIds: List<Long>): Flow<List<Measurement>> =
        itemDao.getMeasurementsForDays(valueIds).map { list -> list.map { it.toDomain() } }

    override suspend fun getMeasurementGroup(groupId: String): List<Measurement> =
        itemDao.getMeasurementGroup(groupId).map { it.toDomain() }

    override suspend fun insertMeasurements(measurements: List<Measurement>) =
        itemDao.insertMeasurements(measurements.map { it.toEntity() })

    override suspend fun updateMeasurements(measurements: List<Measurement>) =
        itemDao.updateMeasurements(measurements.map { it.toEntity() })

    override suspend fun deleteMeasurements(measurements: List<Measurement>) =
        itemDao.deleteMeasurements(measurements.map { it.toEntity() })

    // --- Овоскопирования ---

    override fun getCandlings(batchId: Long): Flow<List<Candling>> =
        itemDao.getCandlings(batchId).map { list -> list.map { it.toDomain() } }

    override fun getCandlingsFor(incubatorId: Long): Flow<List<Candling>> =
        itemDao.getCandlingsFor(incubatorId).map { list -> list.map { it.toDomain() } }

    override fun getAllCandlings(): Flow<List<Candling>> =
        itemDao.getAllCandlings().map { list -> list.map { it.toDomain() } }

    override suspend fun getCandling(batchId: Long, day: Int): Candling? =
        itemDao.getCandling(batchId, day)?.toDomain()

    override suspend fun saveCandling(candling: Candling) {
        itemDao.insertCandling(candling.toEntity())
    }

    override suspend fun deleteCandling(candling: Candling) =
        itemDao.deleteCandling(candling.toEntity())

    // --- Напоминания ---

    override suspend fun insertTime(time: Time) = itemDao.insertTime(time.toEntity())

    override suspend fun updateTime(time: Time) = itemDao.updateTime(time.toEntity())

    override suspend fun deleteTime(time: Time) = itemDao.deleteTime(time.toEntity())

    override suspend fun getTimeList(id: Long): List<Time> =
        itemDao.getTimeList(id).map { it.toDomain() }

    // --- Виды птицы ---

    override fun getAllSpecies(): Flow<List<Species>> =
        itemDao.getAllSpecies().map { list -> list.map { it.toDomain() } }

    override suspend fun getSpeciesList(idPT: Long): List<Species> =
        itemDao.getSpeciesList(idPT).map { it.toDomain() }

    override suspend fun insertSpecies(species: Species) =
        itemDao.insertSpecies(species.toEntity())

    override suspend fun deleteSpecies(species: Species) =
        itemDao.deleteSpecies(species.toEntity())

    // --- Владелец хозяйства ---

    /**
     * Отсутствие строки схлопывается в пустого [User]: экран профиля спрашивает «как вас
     * зовут», а не «есть ли запись», и различать «не спрашивали» от «оставили пустым»
     * ему нечем и незачем.
     */
    override fun getUser(): Flow<User> =
        itemDao.getUser().map { it?.toDomain() ?: User() }

    override suspend fun saveUser(user: User) = itemDao.saveUser(user.toEntity())

    // --- Свои виды птицы ---

    override fun getCustomSpecies(): Flow<List<CustomSpecies>> =
        itemDao.getCustomSpecies().map { list -> list.map { it.toDomain() } }

    override suspend fun saveCustomSpecies(species: CustomSpecies): Long =
        itemDao.saveCustomSpecies(species.toEntity(), species.days.map { it.toEntity() })

    override suspend fun deleteCustomSpecies(species: CustomSpecies) =
        itemDao.deleteCustomSpecies(species.toEntity())

    override suspend fun countActiveBatchesOfType(type: String): Int =
        itemDao.countActiveBatchesOfType(type)
}
