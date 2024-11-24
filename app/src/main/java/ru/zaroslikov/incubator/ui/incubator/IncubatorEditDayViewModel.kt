package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.Value

class IncubatorEditDayViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository
) : ViewModel() {

    val itemId: Long = checkNotNull(savedStateHandle[IncubatorEditDayScreenDestination.itemIdArg])
    val day: Int = checkNotNull(savedStateHandle[IncubatorEditDayScreenDestination.itemIdArgTwo])

    var incubatorState by mutableStateOf(ValueUiState())
        private set

    init {
        viewModelScope.launch {
            incubatorState = itemsRepository.getIncubatorEditDay(itemId, day)
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

fun Value.toValueUiState(): ValueUiState =  ValueUiState(
    id, day, temp, damp, over, airing, note, idPT
)

fun ValueUiState.toValue(): Value = Value(
    id, day, temp, damp, over, airing, note, idPT
)