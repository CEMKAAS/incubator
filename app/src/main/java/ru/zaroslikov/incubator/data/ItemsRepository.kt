package ru.zaroslikov.incubator.data

import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.ferma.Value


interface ItemsRepository {

    fun getAllIncubator(): Flow<List<IncubatorTable>>
    fun getIncubator(id: Long): Flow<IncubatorTable>
    suspend fun insertIncubatorLong(incubatorTable: IncubatorTable): Long
    suspend fun updateIncubator(incubatorTable: IncubatorTable)
    suspend fun deleteIncubator(incubatorTable: IncubatorTable)
    fun getIncubatorList(id: Long): Flow<List<Value>>
    fun getValue(id: Long): Flow<Value>
    suspend fun insertValue(value: Value)
    suspend fun updateValue(value: Value)
    suspend fun insertTime(time: Time)
    suspend fun updateTime(time: Time)
    suspend fun deleteTime(time: Time)
    suspend fun getTimeList(id: Long): List<Time>
    fun getIncubatorEditDay(id: Long, day:Int): Flow<Value>
    suspend fun getValueArchive(idPT: Long): List<Value>
    suspend fun getIncubatorArchiveList(type: String): List<IncubatorTable>
}
