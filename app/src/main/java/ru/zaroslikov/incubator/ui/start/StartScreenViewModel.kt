package ru.zaroslikov.incubator.ui.start


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.IncubatorTable


class StartScreenViewModel(
    private val fermaRepository: ItemsRepository,
) : ViewModel() {

    val getAllProjectAct: StateFlow<StartUiState> =
        fermaRepository.getAllIncubator().map { StartUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = StartUiState()
            )

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}

data class StartUiState(val projectList: List<IncubatorTable> = listOf())