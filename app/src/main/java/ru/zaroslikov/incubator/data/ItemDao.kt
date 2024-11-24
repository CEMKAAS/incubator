package ru.zaroslikov.incubator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.ferma.Value

@Dao
interface ItemDao {

    @Query("SELECT * from Incubator ORDER BY strftime('%Y-%m-%d', substr(Date, 7, 4) || '-' || substr(Date, 4, 2) || '-' || substr(Date, 1, 2))")
    fun getAllIncubator(): Flow<List<IncubatorTable>>

    @Query("SELECT * from Incubator Where _id=:id")
    fun getIncubator(id: Long): Flow<IncubatorTable>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncubatorLong(incubatorTable: IncubatorTable): Long

    @Update
    suspend fun updateIncubator(incubatorTable: IncubatorTable)

    @Delete
    suspend fun deleteIncubator(incubatorTable: IncubatorTable)

    @Query("SELECT * from Incubator_value Where idPT=:id")
    fun getIncubatorList(id: Long): Flow<List<Value>>

    @Query("SELECT * from Incubator_value Where idPT=:id")
    fun getValue(id: Long): Flow<Value>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertValue(value: Value)

    @Update
    suspend fun updateValue(value: Value)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTime(time: Time)

    @Update
    suspend fun updateTime(time: Time)

    @Delete
    suspend fun updateDelete(time: Time)

    @Query("SELECT * from Incubator_time Where idPT=:id")
    suspend fun getTimeList(id: Long): List<Time>

    @Query("SELECT * from Incubator_value Where idPT=:id and day=:day")
    fun getIncubatorEditDay(id: Long, day:Int): Flow<Value>

    @Query("SELECT * from incubator_value Where idPT =:idPT")
    suspend fun getValueArchive(idPT: Long): List<Value>

    @Query("SELECT * from Incubator Where TYPE =:type and Archive = 1")
    suspend fun getIncubatorArchiveList(type: String): List<IncubatorTable>

}
