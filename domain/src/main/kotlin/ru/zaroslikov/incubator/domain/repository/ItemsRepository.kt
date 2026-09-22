package ru.zaroslikov.incubator.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.model.Value


interface ItemsRepository {

    // --- Инкубаторы (устройства) ---
    fun getAllIncubators(): Flow<List<Incubator>>
    /**
     * Инкубатор по идентификатору. `null` — строки нет: её могли удалить, пока экран
     * открыт, и подписка переживает удаление, а не падает на нём.
     */
    fun getIncubator(id: Long): Flow<Incubator?>
    suspend fun insertIncubator(incubator: Incubator): Long
    suspend fun updateIncubator(incubator: Incubator)
    suspend fun deleteIncubator(incubator: Incubator)

    /** Сколько устройств уже заведено — из этого числа форма берёт подсказку «Инкубатор N». */
    fun countIncubators(): Flow<Int>

    /** Ранее введённые бренды и модели — подсказки в форме инкубатора. */
    fun getUsedBrands(): Flow<List<String>>
    fun getUsedModels(): Flow<List<String>>

    // --- Закладки ---

    fun getAllBatches(): Flow<List<Batch>>
    fun getBatchesFor(incubatorId: Long): Flow<List<Batch>>
    /** Закладка по идентификатору; `null` — по той же причине, что и у [getIncubator]. */
    fun getBatch(id: Long): Flow<Batch?>
    suspend fun insertBatch(batch: Batch): Long
    /** Переписывает строку закладки и все её породы разом. */
    suspend fun updateBatch(batch: Batch)
    suspend fun deleteBatch(batch: Batch)
    suspend fun getArchivedBatches(type: String): List<Batch>

    /**
     * Кладёт новую закладку целиком — строку с породами, чип ведущего вида, расписание
     * по дням и времена напоминаний — одной транзакцией, и возвращает её идентификатор.
     *
     * Отдельными вызовами это было два–три десятка коммитов, каждый из которых мог
     * оказаться последним: закладка с половиной расписания уже видна в списке, а «День
     * N/M» считает по строкам, которых нет.
     */
    suspend fun insertBatchWithSchedule(
        batch: Batch,
        species: Species,
        days: List<Value>,
        times: List<Time>,
    ): Long

    // --- Дни закладки ---
    fun getBatchValues(idPT: Long): Flow<List<Value>>
    fun getValue(id: Long): Flow<Value?>
    suspend fun insertValue(value: Value)
    suspend fun updateValue(value: Value)

    /** Дни закладки одной транзакцией — правка расписания пишет их все разом. */
    suspend fun updateSchedule(days: List<Value>)
    fun getBatchValueForDay(idPT: Long, day: Int): Flow<Value?>
    suspend fun getValueArchive(idPT: Long): List<Value>

    // --- Замеры закладки ---

    /** Замеры одного дня расписания ([Value.id]); новые сверху. */
    fun getMeasurements(idValue: Long): Flow<List<Measurement>>

    /**
     * Замеры всех дней закладки; внутри дня — те же новые сверху.
     * Раскладываются по дням через [Measurement.idValue].
     */
    fun getBatchMeasurements(batchId: Long): Flow<List<Measurement>>

    /**
     * Сколько всего замеров в закладке. Форме новой закладки нужно только «есть или
     * нет»: без замеров предлагать «среднее по факту» не из чего.
     */
    suspend fun countMeasurements(batchId: Long): Int
    suspend fun insertMeasurement(measurement: Measurement)
    suspend fun updateMeasurement(measurement: Measurement)
    suspend fun deleteMeasurement(measurement: Measurement)

    /**
     * Замеры нескольких дней расписания разом — сегодняшних дней всех идущих закладок
     * инкубатора; новые сверху. Нужны шторке замеров инкубатора: замер по прибору лежит
     * копиями по этим дням ([Measurement.groupId]), и группа собирается из одного
     * ответа, а не из подписки на каждую закладку.
     */
    fun getMeasurementsForDays(valueIds: List<Long>): Flow<List<Measurement>>

    /**
     * Все копии одного замера по инкубатору — и в закладках, завершённых после записи:
     * правка и удаление группы идут по метке, а не по сегодняшним дням идущих закладок.
     */
    suspend fun getMeasurementGroup(groupId: String): List<Measurement>

    /**
     * Копии одного замера по инкубатору — одной транзакцией, чтобы показание с одного
     * термометра не осталось в одной закладке и не пропало в другой. Правка и удаление
     * группы идут тем же путём.
     */
    suspend fun insertMeasurements(measurements: List<Measurement>)
    suspend fun updateMeasurements(measurements: List<Measurement>)
    suspend fun deleteMeasurements(measurements: List<Measurement>)

    // --- Овоскопирования ---

    /** Итоги овоскопирований закладки по возрастанию дня инкубации. */
    fun getCandlings(batchId: Long): Flow<List<Candling>>

    /**
     * Овоскопирования всех закладок инкубатора; порядок — по закладке, внутри неё по
     * дню. Нужны вкладке «Статистика»: отбраковку она считает по инкубатору целиком.
     */
    fun getCandlingsFor(incubatorId: Long): Flow<List<Candling>>

    /**
     * Овоскопирования всех закладок всех инкубаторов — сводной аналитике в профиле.
     * Тот же вопрос, что и у [getCandlingsFor], но без разреза по устройству.
     */
    fun getAllCandlings(): Flow<List<Candling>>

    /** Итог за конкретный день; `null` — овоскопирование в этот день ещё не записывали. */
    suspend fun getCandling(batchId: Long, day: Int): Candling?

    /** Записывает итог; сохранение за уже записанный день переписывает прежний. */
    suspend fun saveCandling(candling: Candling)

    suspend fun deleteCandling(candling: Candling)

    // --- Напоминания ---
    suspend fun insertTime(time: Time)
    suspend fun updateTime(time: Time)
    suspend fun deleteTime(time: Time)
    suspend fun getTimeList(id: Long): List<Time>

    // --- Владелец хозяйства ---

    /**
     * Имя владельца. Пустой [User] — имя ещё не вводили; отдельного «нет строки»
     * наружу не выходит: для экрана это одно и то же состояние.
     */
    fun getUser(): Flow<User>
    suspend fun saveUser(user: User)

    // --- Виды птицы в закладке ---
    fun getAllSpecies(): Flow<List<Species>>
    suspend fun getSpeciesList(idPT: Long): List<Species>
    suspend fun insertSpecies(species: Species)
    suspend fun deleteSpecies(species: Species)

    // --- Свои виды птицы ---

    /**
     * Свои виды вместе с днями, в порядке создания. Из них ViewModel собирает
     * [ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog] — единственное место,
     * которое отвечает про вид и встроенный, и свой.
     */
    fun getCustomSpecies(): Flow<List<CustomSpecies>>

    /**
     * Создаёт вид или переписывает его целиком: строка вида и все её дни. Возвращает
     * идентификатор вида.
     *
     * **Переименование проходит по закладкам**: они ссылаются на вид по имени
     * (`Batch.type`, чип `Batch_species`), и вид, названный иначе, оставил бы их без
     * срока и овоскопирований. Всё в одной транзакции — вид без дней или закладки со
     * старым именем при новом виде были бы полусостоянием.
     */
    suspend fun saveCustomSpecies(species: CustomSpecies): Long

    /** Удаляет вид с днями. Закладки, созданные по нему, остаются — с запечённым планом. */
    suspend fun deleteCustomSpecies(species: CustomSpecies)

    /**
     * Сколько идущих закладок ведётся по этому виду. Удалять вид, пока по нему что-то
     * идёт, нельзя: закладка потеряла бы срок и овоскопирования на середине пути.
     */
    suspend fun countActiveBatchesOfType(type: String): Int
}
