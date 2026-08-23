package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.incubation.incubationDays
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.ui.incubator.daysBetween
import ru.zaroslikov.incubator.ui.incubator.parseDate
import ru.zaroslikov.incubator.ui.incubator.plusDays
import ru.zaroslikov.incubator.ui.incubator.today
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Состояние шторки закладки — макет
 * [14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893).
 *
 * [plannedToday] и [plannedTomorrow] — строки расписания ([Value]), то есть план;
 * фактические показания лежат отдельно, в [BatchDetailViewModel.measurements].
 *
 * [plannedToday] заодно и есть то, к чему привязываются замеры: без строки расписания
 * на сегодня записывать замер некуда, и форма это учитывает.
 */
data class BatchDetailUiState(
    val batchId: Long = 0,
    val title: String = "",
    val type: String = "",
    val eggAll: Int = 0,
    val eggAllEND: Int = 0,
    val finished: Boolean = false,
    val startDate: Date? = null,
    val hatchDate: Date? = null,
    val day: Int = 0,
    val totalDays: Int? = null,
    val plannedToday: Value? = null,
    val plannedTomorrow: Value? = null,
    val loaded: Boolean = false,
) {
    /** Замер записывается в день расписания; нет строки — нет и дня, к которому привязать. */
    val canRecord: Boolean get() = plannedToday != null
}

/**
 * Поля формы замера. [editingId] отличен от нуля, когда правят уже записанный замер:
 * тогда сохраняется его же время, а не текущее — замер сделали тогда, когда сделали.
 */
data class MeasurementForm(
    val editingId: Long = 0,
    val time: String = "",
    val temp: String = "",
    val damp: String = "",
    val over: String = "",
    val airing: String = "",
    val note: String = "",
) {
    /**
     * Пустой замер записывать незачем, но заметка — такая же его часть, как показания:
     * «долил воды» без цифр это полноценная запись в журнале дня.
     */
    val isValid: Boolean
        get() = temp.isNotBlank() || damp.isNotBlank() || over.isNotBlank() ||
                airing.isNotBlank() || note.isNotBlank()
}

/**
 * Шторка одной закладки: сводка, замеры за сегодня и режим на завтра.
 *
 * Идентификатор приходит не из `SavedStateHandle`, а параметром [load] — у шторки нет
 * маршрута в навигации, ровно как у [AddBatchViewModel].
 *
 * Номер сегодняшнего дня инкубации считается от даты начала закладки, а вот сами замеры
 * хранятся при строке этого дня ([Value.id]). Поэтому исправленная дата начала или
 * переведённые на телефоне часы меняют только то, какой день считается сегодняшним;
 * уже записанные замеры остаются при своих днях.
 */
class BatchDetailViewModel(
    private val itemsRepository: ItemsRepository,
) : ViewModel() {

    var uiState by mutableStateOf(BatchDetailUiState())
        private set

    /** Замеры сегодняшнего дня расписания. Отдельно от [uiState] — иначе поток замеров
     *  и разовая загрузка закладки затирали бы друг друга, кто последний. */
    var measurements by mutableStateOf<List<Measurement>>(emptyList())
        private set

    var form by mutableStateOf(MeasurementForm())
        private set

    /** Одна корутина на всё открытие: сперва читает закладку, потом остаётся висеть
     *  на потоке замеров. Отмена при новом [load] снимает и то и другое разом. */
    private var loadJob: Job? = null

    /** Вызывается из `LaunchedEffect(batchId)`: шторка открывается заново — данные
     *  перечитываются, форма замера сбрасывается, как и в остальных шторках. */
    fun load(batchId: Long) {
        if (batchId == 0L) return
        form = MeasurementForm()
        measurements = emptyList()

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val batch = itemsRepository.getBatch(batchId).filterNotNull().first()
            val plan = itemsRepository.getBatchValues(batchId).first()

            val total = incubationDays(batch.type)
            val start = parseDate(batch.data)
            val elapsed = if (start == null) 0 else daysBetween(start, today())
            val day = (elapsed + 1).coerceAtLeast(1).let {
                if (total != null) it.coerceAtMost(total) else it
            }
            val plannedToday = plan.firstOrNull { it.day == day }

            uiState = BatchDetailUiState(
                batchId = batch.id,
                title = batch.title.ifBlank { batch.type },
                type = batch.type,
                eggAll = batch.eggAll,
                eggAllEND = batch.eggAllEND,
                finished = batch.arhive != "0",
                startDate = start,
                hatchDate = when {
                    batch.arhive != "0" -> parseDate(batch.dateEnd)
                    start != null && total != null -> start.plusDays(total)
                    else -> null
                },
                day = day,
                totalDays = total,
                plannedToday = plannedToday,
                plannedTomorrow = plan.firstOrNull { it.day == day + 1 },
                loaded = true,
            )

            if (plannedToday != null) {
                itemsRepository.getMeasurements(plannedToday.id).collect { measurements = it }
            }
        }
    }

    fun update(form: MeasurementForm) {
        this.form = form
    }

    /** Подставляет записанный замер в форму — кнопка «Изм.» в строке. */
    fun startEdit(measurement: Measurement) {
        form = MeasurementForm(
            editingId = measurement.id,
            time = measurement.time,
            temp = measurement.temp.toFieldText(),
            damp = measurement.damp.toFieldText(),
            over = measurement.over,
            airing = measurement.airing,
            note = measurement.note,
        )
    }

    /**
     * Бросает правку: форма возвращается к пустой, записанный замер остаётся как был.
     *
     * Правка идёт в форме, а не в самой строке, поэтому «вернуть как было» — это просто
     * не сохранять: в базе до нажатия «Сохранить замер» ничего не менялось.
     */
    fun cancelEdit() {
        form = MeasurementForm()
    }

    fun save() {
        val dayRow = uiState.plannedToday ?: return
        val current = form
        if (!current.isValid) return
        viewModelScope.launch {
            val measurement = Measurement(
                id = current.editingId,
                idValue = dayRow.id,
                time = current.time.ifBlank {
                    SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
                },
                temp = current.temp.toMeasureOrNull(),
                damp = current.damp.toMeasureOrNull(),
                over = current.over.trim(),
                airing = current.airing.trim(),
                note = current.note.trim(),
            )
            if (current.editingId == 0L) itemsRepository.insertMeasurement(measurement)
            else itemsRepository.updateMeasurement(measurement)
            form = MeasurementForm()
        }
    }

    fun delete(measurement: Measurement) {
        viewModelScope.launch {
            itemsRepository.deleteMeasurement(measurement)
            if (form.editingId == measurement.id) form = MeasurementForm()
        }
    }
}
