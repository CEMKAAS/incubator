package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.model.Value

class BatchDayViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository
) : ViewModel() {

    val itemId: Long = checkNotNull(savedStateHandle[BatchDayDestination.itemIdArg])
    val day: Int = checkNotNull(savedStateHandle[BatchDayDestination.itemIdArgTwo])

    var incubatorState by mutableStateOf(ValueUiState())
        private set

    init {
        viewModelScope.launch {
            incubatorState = itemsRepository.getBatchValueForDay(itemId, day)
                .filterNotNull()
                .first()
                .toValueUiState()
        }
    }

    fun updateUiState(itemDetails: ValueUiState) {
        incubatorState =
            itemDetails
    }

    suspend fun saveEdit() {
        itemsRepository.updateValue(incubatorState.toValue())
    }

}

data class ValueUiState(
    val id: Long = 0,
    val day: Int = 0,
    val temp: String = "",
    val damp: String = "",
    var over: String = "",
    var airing: String = "",
    var note: String = "",
    var idPT: Long = 0
)

/** Форма держит температуру и влажность строками; числа живут в [Value]. */
fun Value.toValueUiState(): ValueUiState = ValueUiState(
    id, day, temp.toFieldText(), damp.toFieldText(), over, airing, note, idPT
)

fun ValueUiState.toValue(): Value = Value(
    id, day, temp.toMeasureOrNull(), damp.toMeasureOrNull(), over, airing, note, idPT
)
