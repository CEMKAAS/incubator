package ru.zaroslikov.incubator.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.CandlingEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesDayEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesWithDays
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.UserEntity
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

    /**
     * Ряд может исчезнуть под живой подпиской — инкубатор удаляют, пока его экран открыт.
     * Для не-nullable результата Room генерирует `error("The query result was empty…")`, и
     * запрос перевыполняется на каждое изменение таблицы, так что удаление роняло
     * коллектор. Ответ «строки нет» — обычный ответ, а не сбой.
     */
    @Query("SELECT * from Incubator Where _id=:id")
    fun getIncubator(id: Long): Flow<IncubatorEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncubator(incubator: IncubatorEntity): Long

    @Update
    suspend fun updateIncubator(incubator: IncubatorEntity)

    @Delete
    suspend fun deleteIncubator(incubator: IncubatorEntity)

    /**
     * Сколько устройств уже заведено — из этого числа форма берёт подсказку «Инкубатор N».
     *
     * Счёт, а не список: форме нужно одно число, а `getAllIncubators()` ради него читал бы
     * и переводил в доменные модели все строки на каждое изменение таблицы. Архивные
     * считаются вместе с остальными — они никуда не делись, и подсказать имя, которое уже
     * носит убранное в архив устройство, значит подсказать путаницу.
     */
    @Query("SELECT COUNT(*) FROM Incubator")
    fun countIncubators(): Flow<Int>

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

    /** Nullable по той же причине, что и [getIncubator]: закладку удаляют при открытой шторке. */
    @Query("SELECT * from Batch Where _id=:id")
    fun getBatch(id: Long): Flow<BatchEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Delete
    suspend fun deleteBatch(batch: BatchEntity)

    // `Archive != '0'` — тот же вопрос, что задаёт `Batch.status` в домене: завершено
    // всё, что не «0». Прежнее `Archive = 1` сравнивало текстовую колонку с числом и
    // пропускало закладки, у которых из v1 осталось слово `true`.
    @Query("SELECT * from Batch Where TYPE =:type and Archive != '0'")
    suspend fun getArchivedBatches(type: String): List<BatchEntity>

    /**
     * Кладёт закладку целиком: саму строку, чип ведущего вида, все дни
     * расписания и времена напоминаний — одной транзакцией.
     *
     * Раньше это были 20–32 отдельные вставки из ViewModel, то есть 20–32 коммита на
     * одно нажатие «Заложить яйца» — и, что важнее, ни одного обещания довести дело до
     * конца. Сбой или смерть процесса посередине оставляли закладку с половиной
     * расписания: в списке она уже есть, а `getBatchValues` возвращает 14 дней из 30,
     * притом что и «День N/M», и вкладка «Расписание» считают строку N днём N.
     *
     * `idPT` проставляется здесь же, после получения идентификатора: снаружи его взять
     * неоткуда, а расписание без него не принадлежит никому. Заодно обнуляется `id` —
     * строки новые, а ненулевой идентификатор при `OnConflictStrategy.IGNORE` привёл бы
     * к молча пропущенной вставке (режим, взятый из архивной закладки, приходит сюда с
     * чужими идентификаторами дней).
     */
    @Transaction
    suspend fun insertBatchWithSchedule(
        batch: BatchEntity,
        species: SpeciesEntity,
        days: List<ValueEntity>,
        times: List<TimeEntity>,
    ): Long {
        val id = insertBatch(batch)
        insertSpecies(species.copy(idPT = id))
        insertValues(days.map { it.copy(id = 0, idPT = id) })
        insertTimes(times.map { it.copy(id = 0, idPT = id) })
        return id
    }

    // --- Дни закладки ---

    // Порядок задан явно: и список дней, и вкладка «Расписание» в шторке закладки
    // считают, что строка на месте N — это день N. Без ORDER BY это держалось только
    // на том, что дни вставлялись по порядку и SQLite возвращал их в порядке rowid.
    @Query("SELECT * from Batch_value Where idPT=:id ORDER BY day")
    fun getBatchValues(id: Long): Flow<List<ValueEntity>>

    /**
     * День расписания по его собственному идентификатору.
     *
     * Фильтр был по `idPT` — то есть по закладке, — хотя и параметр, и репозиторий, и
     * домен называют его идентификатором дня: запрос возвращал первый попавшийся день
     * закладки. Вызывающих у метода нет, поэтому это была не ошибка, а заряженная мина.
     */
    @Query("SELECT * from Batch_value Where id=:id")
    fun getValue(id: Long): Flow<ValueEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertValue(value: ValueEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertValues(values: List<ValueEntity>)

    @Update
    suspend fun updateValue(value: ValueEntity)

    @Update
    suspend fun updateValues(values: List<ValueEntity>)

    @Query("SELECT * from Batch_value Where idPT=:id and day=:day")
    fun getBatchValueForDay(id: Long, day: Int): Flow<ValueEntity?>

    @Query("SELECT * from Batch_value Where idPT =:idPT")
    suspend fun getValueArchive(idPT: Long): List<ValueEntity>

    // --- Замеры ---

    @Query("SELECT * from Batch_measurement Where idValue=:idValue ORDER BY time DESC, id DESC")
    fun getMeasurements(idValue: Long): Flow<List<MeasurementEntity>>

    /**
     * Замеры всей закладки разом: вкладка «Расписание» показывает историю любого дня,
     * и тридцать отдельных подписок ради этого держать незачем. Внутри дня порядок тот
     * же, что и у [getMeasurements], — свежий замер первым.
     */
    @Query(
        "SELECT Batch_measurement.* FROM Batch_measurement " +
            "JOIN Batch_value ON Batch_measurement.idValue = Batch_value.id " +
            "WHERE Batch_value.idPT = :batchId " +
            "ORDER BY Batch_value.day, Batch_measurement.time DESC, Batch_measurement.id DESC"
    )
    fun getBatchMeasurements(batchId: Long): Flow<List<MeasurementEntity>>

    /**
     * Сколько замеров записано за всю закладку.
     *
     * Нужно форме новой закладки: она предлагает взять режим из завершённой, и брать
     * «среднее по замерам» имеет смысл только там, где замеры есть. Отдельный запрос,
     * а не длина [getBatchMeasurements], потому что спрашивают это про каждую
     * завершённую закладку вида сразу — тянуть ради «пусто или нет» по сотне строк на
     * каждую незачем.
     */
    @Query(
        "SELECT COUNT(*) FROM Batch_measurement " +
            "JOIN Batch_value ON Batch_measurement.idValue = Batch_value.id " +
            "WHERE Batch_value.idPT = :batchId"
    )
    suspend fun countMeasurements(batchId: Long): Int

    /**
     * Замеры нескольких дней разом — сегодняшних дней всех идущих закладок инкубатора.
     *
     * Шторке замеров инкубатора нужны именно они, и одним запросом, а не подпиской на
     * день каждой закладки: замер по прибору лежит копиями по всем этим дням, и
     * собирать группу из нескольких потоков значило бы склеивать её в UI. Порядок тот же,
     * что у [getMeasurements] — свежий первым; внутри одной группы копии стоят рядом.
     */
    @Query(
        "SELECT * from Batch_measurement Where idValue IN (:valueIds) " +
            "ORDER BY time DESC, id DESC"
    )
    fun getMeasurementsForDays(valueIds: List<Long>): Flow<List<MeasurementEntity>>

    /**
     * Все копии одного замера по инкубатору — по метке, а не по дням целей: закладку могли
     * завершить после записи, и её копия из сегодняшних дней идущих закладок уже выпала,
     * а из группы — нет. Правка и удаление группы обязаны дотянуться и до неё, иначе
     * в завершённой закладке останется старое показание, которого прибор не показывал.
     * Полный проход по таблице без индекса — нарочно: группу правят и удаляют по одной,
     * и таблица замеров на это отвечает за миллисекунды.
     */
    @Query("SELECT * from Batch_measurement Where groupId=:groupId ORDER BY id")
    suspend fun getMeasurementGroup(groupId: String): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurement(measurement: MeasurementEntity)

    /**
     * Копии одного замера по инкубатору — одной транзакцией: процесс, убитый между двумя
     * вставками, оставил бы показание в одной закладке и не оставил в другой, хотя
     * снималось оно с одного термометра. Room оборачивает списочные `@Insert` / `@Update` /
     * `@Delete` в транзакцию сам.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurements(measurements: List<MeasurementEntity>)

    @Update
    suspend fun updateMeasurement(measurement: MeasurementEntity)

    @Update
    suspend fun updateMeasurements(measurements: List<MeasurementEntity>)

    @Delete
    suspend fun deleteMeasurements(measurements: List<MeasurementEntity>)

    @Delete
    suspend fun deleteMeasurement(measurement: MeasurementEntity)

    // --- Овоскопирования ---

    @Query("SELECT * from Batch_candling Where idPT=:batchId ORDER BY day")
    fun getCandlings(batchId: Long): Flow<List<CandlingEntity>>

    @Query("SELECT * from Batch_candling Where idPT=:batchId and day=:day")
    suspend fun getCandling(batchId: Long, day: Int): CandlingEntity?

    /**
     * Овоскопирования всех закладок инкубатора разом.
     *
     * Вкладке «Статистика» отбраковка нужна по инкубатору целиком, а подписываться на
     * каждую закладку отдельно значило бы держать столько потоков, сколько закладок, —
     * ровно та же причина, по которой [getBatchMeasurements] собирает замеры одним
     * запросом вместо тридцати подписок по дням.
     */
    @Query(
        "SELECT Batch_candling.* FROM Batch_candling " +
            "JOIN Batch ON Batch_candling.idPT = Batch._id " +
            "WHERE Batch.incubatorId = :incubatorId " +
            "ORDER BY Batch_candling.idPT, Batch_candling.day"
    )
    fun getCandlingsFor(incubatorId: Long): Flow<List<CandlingEntity>>

    /**
     * Овоскопирования вообще всех закладок — сводной аналитике в профиле.
     *
     * Отдельный запрос, а не [getCandlingsFor] по каждому инкубатору: там столько
     * подписок, сколько устройств, и склеивать их пришлось бы в UI. Здесь `JOIN` не
     * нужен вовсе — фильтровать не по чему.
     */
    @Query("SELECT * from Batch_candling ORDER BY idPT, day")
    fun getAllCandlings(): Flow<List<CandlingEntity>>

    /**
     * `REPLACE`, а не `IGNORE`: пара `idPT` + `day` уникальна, и повторное сохранение
     * итога за тот же день должно его переписать, а не тихо пропасть. Дочерних строк
     * у записи нет, поэтому пересозданный идентификатор ничего за собой не тянет.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandling(candling: CandlingEntity): Long

    @Delete
    suspend fun deleteCandling(candling: CandlingEntity)

    /**
     * Закладка и её овоскопирования из формы правки — одной транзакцией. «Отбраковано
     * яиц» в форме — итог `Egg_rejected` и суммы овоскопирований, правленный в одном
     * диалоге, так что строка закладки и записи овоскопирований верны только вместе:
     * записанные порознь, они на время сдвинули бы «Осталось», а процесс, убитый между
     * ними, оставил бы сдвиг навсегда.
     */
    @Transaction
    suspend fun updateBatchWithCandlings(
        batch: BatchEntity,
        save: List<CandlingEntity>,
        delete: List<CandlingEntity>,
    ) {
        delete.forEach { deleteCandling(it) }
        save.forEach { insertCandling(it) }
        updateBatch(batch)
    }

    // --- Напоминания ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTime(time: TimeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTimes(times: List<TimeEntity>)

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

    // --- Владелец хозяйства ---

    /**
     * Единственная строка таблицы. `null` — её ещё нет: имя не спрашивали.
     */
    @Query("SELECT * from User Where _id = 1")
    fun getUser(): Flow<UserEntity?>

    /**
     * `REPLACE`: ключ фиксирован, поэтому сохранение всегда переписывает ту же строку,
     * заводя её при первом разе. Отдельный `update` тут был бы вторым путём к одному и
     * тому же и различался бы только тем, что не срабатывает на пустой таблице.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    // --- Свои виды птицы ---

    /**
     * Все свои виды с днями, в порядке создания. `@Transaction` обязателен: Room
     * собирает `@Relation` вторым запросом, и без транзакции между ними мог бы влезть
     * вид, сохранённый в этот момент, — вид оказался бы в списке без дней либо с чужими.
     */
    @Transaction
    @Query("SELECT * FROM Custom_species ORDER BY _id")
    fun getCustomSpecies(): Flow<List<CustomSpeciesWithDays>>

    @Query("SELECT * FROM Custom_species WHERE _id = :id")
    suspend fun getCustomSpeciesById(id: Long): CustomSpeciesEntity?

    /**
     * `ABORT`, а не `REPLACE`: имя уникально, и вставка вида с занятым именем — ошибка,
     * о которой должен узнать вызвавший. `REPLACE` в SQLite удаляет прежнюю строку и
     * вставляет новую, а значит унёс бы каскадом дни того вида и выдал бы новый
     * идентификатор — тихая подмена вместо отказа.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomSpecies(species: CustomSpeciesEntity): Long

    @Update
    suspend fun updateCustomSpecies(species: CustomSpeciesEntity)

    @Delete
    suspend fun deleteCustomSpecies(species: CustomSpeciesEntity)

    @Query("DELETE FROM Custom_species_day WHERE speciesId = :speciesId")
    suspend fun deleteCustomSpeciesDays(speciesId: Long)

    @Insert
    suspend fun insertCustomSpeciesDays(days: List<CustomSpeciesDayEntity>)

    /**
     * Закладки ссылаются на вид по имени, а не по идентификатору, — ровно как на
     * встроенный, где никакого идентификатора и нет. Поэтому переименование вида обязано
     * пройти по ним само: иначе закладка осталась бы с именем, которого больше нет, и
     * потеряла бы срок и дни овоскопирования.
     */
    @Query("UPDATE Batch SET Type = :newName WHERE Type = :oldName")
    suspend fun renameBatchType(oldName: String, newName: String)

    /** Тот же переезд для чипов дополнительных видов закладки. */
    @Query("UPDATE Batch_species SET species = :newName WHERE species = :oldName")
    suspend fun renameBatchSpeciesChip(oldName: String, newName: String)

    /**
     * Сколько закладок этого вида ещё идёт. `Archive` — TEXT с первой версии схемы,
     * поэтому сравнение со строкой, а не с нулём.
     */
    @Query("SELECT COUNT(*) FROM Batch WHERE Type = :type AND Archive = '0'")
    suspend fun countActiveBatchesOfType(type: String): Int

    /**
     * Сохраняет вид целиком — строку вида и все её дни — одной транзакцией. Вид без
     * дней или переименованный вид при закладках со старым именем были бы
     * полусостоянием, которое некому починить.
     *
     * Дни удаляются и вставляются заново, а не обновляются построчно: их число меняется
     * вместе со сроком инкубации, и дочерних строк у дня нет — в отличие от
     * `Batch_value`, на который ссылаются замеры и чьи идентификаторы поэтому берегут.
     * Здесь беречь нечего.
     */
    @Transaction
    suspend fun saveCustomSpecies(
        species: CustomSpeciesEntity,
        days: List<CustomSpeciesDayEntity>,
    ): Long {
        val id = if (species.id == 0L) {
            insertCustomSpecies(species)
        } else {
            val old = getCustomSpeciesById(species.id)
            updateCustomSpecies(species)
            if (old != null && old.name != species.name) {
                renameBatchType(old.name, species.name)
                renameBatchSpeciesChip(old.name, species.name)
            }
            species.id
        }
        deleteCustomSpeciesDays(id)
        insertCustomSpeciesDays(days.map { it.copy(id = 0, speciesId = id) })
        return id
    }
}
