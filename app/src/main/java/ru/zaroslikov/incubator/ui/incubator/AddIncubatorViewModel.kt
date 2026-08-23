package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

/**
 * Создание и правка инкубатора — одна форма, живущая в нижней шторке.
 *
 * Шторка не является местом назначения в навигации, поэтому id приходит через [load],
 * а не через SavedStateHandle: она открывается поверх того экрана, что уже на виду.
 *
 * Правка нужна не только для удобства: у инкубатора, синтезированного при миграции с
 * первой версии базы, вместимость нулевая, и заполнить её больше негде.
 */
class AddIncubatorViewModel(
    private val itemsRepository: ItemsRepository,
) : ViewModel() {

    /** Ноль означает «создаём новый». */
    private var incubatorId: Long = 0

    var isEditing by mutableStateOf(false)
        private set

    var uiState by mutableStateOf(IncubatorFormUiState())
        private set

    /** Ранее введённые бренды и модели — выпадают подсказками в соответствующих полях. */
    val usedBrands: StateFlow<List<String>> = itemsRepository.getUsedBrands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TIMEOUT_MILLIS), emptyList())

    val usedModels: StateFlow<List<String>> = itemsRepository.getUsedModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TIMEOUT_MILLIS), emptyList())

    /** Вызывается при каждом открытии шторки — состояние прошлого показа не переживает. */
    fun load(id: Long) {
        incubatorId = id
        isEditing = id != 0L
        uiState = IncubatorFormUiState()
        if (id != 0L) {
            viewModelScope.launch {
                uiState = itemsRepository.getIncubator(id)
                    .filterNotNull()
                    .first()
                    .toUiState()
            }
        }
    }

    fun update(state: IncubatorFormUiState) {
        uiState = state
    }

    /**
     * Обязательное поле только одно — название.
     *
     * Так в макете: звёздочка стоит лишь у названия, а подпись прямо говорит
     * «остальные параметры необязательны». Вместимость поэтому может остаться пустой,
     * и карточка на стартовом экране попросит её заполнить.
     */
    val isValid: Boolean
        get() = uiState.name.isNotBlank()

    fun save(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            if (isEditing) {
                itemsRepository.updateIncubator(uiState.toIncubator(incubatorId))
                onSaved(incubatorId)
            } else {
                onSaved(itemsRepository.insertIncubator(uiState.toIncubator()))
            }
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}

data class IncubatorFormUiState(
    val name: String = "",
    val capacity: String = "",
    val brand: String = "",
    val model: String = "",
    val price: String = "",
    val note: String = "",
    val autoTurn: Boolean = false,
    val autoAiring: Boolean = false,
)

fun IncubatorFormUiState.toIncubator(id: Long = 0): Incubator = Incubator(
    id = id,
    name = name.trim(),
    capacity = capacity.toIntOrNull() ?: 0,
    brand = brand.trim(),
    model = model.trim(),
    price = price.toIntOrNull() ?: 0,
    note = note.trim(),
    autoTurn = autoTurn,
    autoAiring = autoAiring,
)

fun Incubator.toUiState(): IncubatorFormUiState = IncubatorFormUiState(
    name = name,
    // Ноль показываем пустым полем, а не нулём: это «не указано».
    capacity = if (capacity > 0) capacity.toString() else "",
    brand = brand,
    model = model,
    price = if (price > 0) price.toString() else "",
    note = note,
    autoTurn = autoTurn,
    autoAiring = autoAiring,
)
