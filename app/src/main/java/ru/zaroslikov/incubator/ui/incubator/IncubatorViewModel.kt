package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.Value
import ru.zaroslikov.incubator.data.work.WorkRepository

class IncubatorViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
    private val waterRepository: WorkRepository
) : ViewModel() {

    val itemId: Long = checkNotNull(savedStateHandle[IncubatorScreenDestination.itemIdArg])

    val incubatorUiState: StateFlow<IncubatorListUiState> =
        itemsRepository.getIncubatorList(itemId).map { IncubatorListUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = IncubatorListUiState()
            )

    var itemUiState by mutableStateOf(IncubatorUiState())
        private set

    init {
        viewModelScope.launch {
            itemUiState = itemsRepository.getIncubator(itemId)
                .filterNotNull()
                .first()
                .toIncubatorUiState()
        }
    }

    fun updateUiState(itemDetails: IncubatorUiState) {
        itemUiState =
            itemDetails
    }

    suspend fun saveIncubator() {
        waterRepository.cancelAllNotifications(itemUiState.title)
        itemsRepository.updateIncubator(itemUiState.toIncubatorTable())
    }

//    suspend fun unarchiveIncubator() {
//        itemsRepository.updateIncubator(itemUiState.toIncubatorTable())
//    }

    suspend fun deleteIncubator() {
        waterRepository.cancelAllNotifications(itemUiState.title)
        itemsRepository.deleteIncubator(itemUiState.toIncubatorTable())
    }

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }

}

data class IncubatorListUiState(val itemList: List<Value> = listOf())






