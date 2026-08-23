package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.incubation.setAutoIncubator
import ru.zaroslikov.incubator.domain.incubation.setIdPT
import ru.zaroslikov.incubator.domain.incubation.setIdPTTime
import ru.zaroslikov.incubator.domain.incubation.setIncubator
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Форма закладки — макет
 * [12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555).
 *
 * Одна форма и на создание, и на правку, как у инкубатора: отдельного макета для
 * правки нет, а поля совпадают ровно. Что именно открыли, решает [load]; [isEditing]
 * переключает заголовок, кнопку и показ действий «завершить» / «удалить».
 *
 * Ни инкубатор, ни закладка не приходят из SavedStateHandle: форма живёт в нижней
 * шторке, у которой нет своего маршрута.
 */
class AddBatchViewModel(
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository
) : ViewModel() {

    private val format = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())

    private fun today(): String =
        format.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis)

    var uiState by mutableStateOf(BatchUiState())
        private set

    var isEditing by mutableStateOf(false)
        private set

    private val reminders = mutableStateListOf<Time>()
    val reminderList: List<Time> get() = reminders

    /** Напоминания, какими они были при открытии: их нужно удалить перед перезаписью. */
    private val loadedReminders = mutableListOf<Time>()

    /** Название на момент открытия — по нему сняты уже поставленные уведомления. */
    private var loadedTitle: String = ""

    /**
     * Завершённые закладки того же вида — из них можно взять уже выверенный режим
     * вместо расписания по умолчанию. Пусто — предлагать нечего, диалог не появится.
     * При правке не используется: расписание у закладки уже есть.
     */
    var archivedBatches by mutableStateOf<List<Batch>>(emptyList())
        private set

    val isValid: Boolean
        get() = uiState.title.isNotBlank() && (uiState.eggAll.toIntOrNull() ?: 0) > 0

    /**
     * Заполняет форму заново — шторка пересоздаёт её при каждом открытии, прошлый
     * ввод не тянется следом.
     *
     * @param incubatorId инкубатор, внутри которого создаётся закладка.
     * @param batchId ноль — создание, иначе правка существующей закладки.
     */
    fun load(incubatorId: Long, batchId: Long = 0) {
        isEditing = batchId != 0L
        reminders.clear()
        loadedReminders.clear()
        archivedBatches = emptyList()

        if (isEditing) {
            uiState = BatchUiState(incubatorId = incubatorId)
            viewModelScope.launch {
                val batch = itemsRepository.getBatch(batchId).filterNotNull().first()
                uiState = batch.toBatchUiState()
                loadedTitle = batch.title
                val times = itemsRepository.getTimeList(batchId)
                loadedReminders.addAll(times)
                reminders.addAll(times.map { it.copy() })
            }
            return
        }

        uiState = BatchUiState(
            type = DEFAULT_SPECIES,
            data = today(),
            eggAll = "",
            eggAllEND = "0",
            arhive = "0",
            incubatorId = incubatorId,
        )
        loadedTitle = ""
        reminders.add(Time(id = 0, time = "08:00", idPT = 0))

        viewModelScope.launch {
            // Автоматика закладки — это то, что умеет само устройство. В макете
            // отдельных переключателей нет, поэтому флаги наследуются от инкубатора.
            val incubator = itemsRepository.getIncubator(incubatorId).first()
            uiState = uiState.copy(airing = incubator.autoAiring, over = incubator.autoTurn)
        }
        refreshArchive(DEFAULT_SPECIES)
    }

    fun update(state: BatchUiState) {
        val speciesChanged = state.type != uiState.type
        uiState = state
        // При правке расписание уже сгенерировано, и подменять его нечем и незачем.
        if (speciesChanged && !isEditing) refreshArchive(state.type)
    }

    private fun refreshArchive(type: String) {
        viewModelScope.launch {
            archivedBatches = itemsRepository.getArchivedBatches(type)
        }
    }

    fun addReminder() {
        reminders.add(Time(id = 0, time = "08:00", idPT = 0))
    }

    fun removeReminder(index: Int) {
        if (index in reminders.indices) reminders.removeAt(index)
    }

    fun updateReminder(index: Int, time: String = reminders[index].time, note: String? = null) {
        if (index !in reminders.indices) return
        reminders[index] = reminders[index].copy(
            time = time,
            note = note ?: reminders[index].note,
        )
    }

    /**
     * Создаёт закладку вместе с расписанием по дням, напоминаниями и чипом вида —
     * либо, в режиме правки, обновляет её и перевыставляет напоминания.
     *
     * [archiveSourceId] — закладка из архива, чьи значения температуры, влажности,
     * поворота и проветривания берутся вместо расписания по умолчанию. Только при создании.
     */
    fun save(archiveSourceId: Long? = null, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val state = uiState
            if (isEditing) {
                itemsRepository.updateBatch(state.toBatch())
                rewriteReminders(state.id, state.title)
                onSaved(state.id)
                return@launch
            }

            val schedule: MutableList<Value> = if (archiveSourceId != null) {
                itemsRepository.getValueArchive(archiveSourceId).toMutableList()
            } else {
                setAutoIncubator(setIncubator(state.type), state.airing, state.over)
            }

            val idPT = itemsRepository.insertBatch(state.toBatch())

            itemsRepository.insertSpecies(Species(species = state.type, idPT = idPT))

            setIdPT(schedule, idPT).forEach { itemsRepository.insertValue(it) }
            setIdPTTime(reminders.toMutableList(), idPT).forEach { itemsRepository.insertTime(it) }

            workRepository.scheduleReminder(reminders.toMutableList(), state.title)

            onSaved(idPT)
        }
    }

    /**
     * Напоминания перезаписываются целиком: строк мало, а сравнивать их построчно
     * пришлось бы по времени и тексту сразу. Снимаем уведомления по **старому**
     * названию — WorkManager помечает их именно им, и после переименования по новому
     * они бы не нашлись.
     */
    private suspend fun rewriteReminders(batchId: Long, title: String) {
        workRepository.cancelAllNotifications(loadedTitle)
        if (loadedTitle != title) workRepository.cancelAllNotifications(title)

        loadedReminders.forEach { itemsRepository.deleteTime(it) }
        loadedReminders.clear()

        val fresh = reminders.map { it.copy(id = 0, idPT = batchId) }.toMutableList()
        fresh.forEach { itemsRepository.insertTime(it) }
        loadedReminders.addAll(fresh)
        loadedTitle = title

        workRepository.scheduleReminder(fresh, title)
    }

    /** «Завершить закладку» — уходит в архив и перестаёт напоминать о себе. */
    fun archive(onDone: () -> Unit) {
        viewModelScope.launch {
            workRepository.cancelAllNotifications(uiState.title)
            itemsRepository.updateBatch(uiState.copy(arhive = "1").toBatch())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            workRepository.cancelAllNotifications(uiState.title)
            itemsRepository.deleteBatch(uiState.toBatch())
            onDone()
        }
    }

    companion object {
        private const val DATE_PATTERN = "dd.MM.yyyy"
        const val DEFAULT_SPECIES = "Курицы"
    }
}
