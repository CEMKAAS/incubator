package ru.zaroslikov.incubator.ui.batch

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
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.repository.WorkRepository

class BatchViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
    private val waterRepository: WorkRepository
) : ViewModel() {

    val itemId: Long = checkNotNull(savedStateHandle[BatchDestination.itemIdArg])

    val incubatorUiState: StateFlow<BatchValuesUiState> =
        itemsRepository.getBatchValues(itemId).map { BatchValuesUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = BatchValuesUiState()
            )

    var itemUiState by mutableStateOf(BatchUiState())
        private set

    init {
        reload()
    }

    /**
     * Перечитывает закладку из базы.
     *
     * Состояние здесь — обычный `mutableStateOf`, а не поток: экран правит его на месте,
     * когда пользователь завершает инкубацию. Поэтому после правки в шторке его нужно
     * перечитать вручную — иначе в шапке останется прежнее название.
     */
    fun reload() {
        viewModelScope.launch {
            itemUiState = itemsRepository.getBatch(itemId)
                .filterNotNull()
                .first()
                .toBatchUiState()
        }
    }

    fun updateUiState(itemDetails: BatchUiState) {
        itemUiState =
            itemDetails
    }

    suspend fun saveBatch() {
        waterRepository.cancelAllNotifications(itemUiState.title)
        itemsRepository.updateBatch(itemUiState.toBatch())
    }

//    suspend fun unarchiveIncubator() {
//        itemsRepository.updateBatch(itemUiState.toBatch())
//    }

    suspend fun deleteBatch() {
        waterRepository.cancelAllNotifications(itemUiState.title)
        itemsRepository.deleteBatch(itemUiState.toBatch())
    }

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }

}

data class BatchValuesUiState(val itemList: List<Value> = listOf())






