package ru.zaroslikov.incubator.data

import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.data.ItemDao
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.ferma.Value

class OfflineItemsRepository(private val itemDao: ItemDao) : ItemsRepository {
    override fun getAllIncubator(): Flow<List<IncubatorTable>> = itemDao.getAllIncubator()
    override fun getIncubator(id: Long): Flow<IncubatorTable> = itemDao.getIncubator(id)
    override suspend fun insertIncubatorLong(incubatorTable: IncubatorTable): Long =
        itemDao.insertIncubatorLong(incubatorTable)
    override suspend fun updateIncubator(incubatorTable: IncubatorTable) =
        itemDao.updateIncubator(incubatorTable)
    override suspend fun deleteIncubator(incubatorTable: IncubatorTable) =
        itemDao.deleteIncubator(incubatorTable)
    override fun getIncubatorList(id: Long): Flow<List<Value>> = itemDao.getIncubatorList(id)
    override fun getValue(id: Long): Flow<Value> = itemDao.getValue(id)
    override suspend fun insertValue(value: Value) = itemDao.insertValue(value)
    override suspend fun updateValue(value: Value) = itemDao.updateValue(value)
    override suspend fun insertTime(time: Time) = itemDao.insertTime(time)
    override suspend fun updateTime(time: Time) = itemDao.updateTime(time)
    override suspend fun deleteTime(time: Time) = itemDao.updateDelete(time)
    override suspend fun getTimeList(id: Long): List<Time> = itemDao.getTimeList(id)
    override fun getIncubatorEditDay(id: Long, day: Int): Flow<Value> =
        itemDao.getIncubatorEditDay(id, day)
    override suspend fun getValueArchive(idPT: Long): List<Value> = itemDao.getValueArchive(idPT)
    override suspend fun getIncubatorArchiveList(type: String): List<IncubatorTable> =
        itemDao.getIncubatorArchiveList(type)

}
