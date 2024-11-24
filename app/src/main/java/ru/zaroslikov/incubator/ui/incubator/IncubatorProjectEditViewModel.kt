package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.work.WorkRepository

class IncubatorProjectEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository
) : ViewModel() {

    val itemId: Long = checkNotNull(savedStateHandle[IncubatorScreenDestination.itemIdArg])

    var projectState by mutableStateOf(IncubatorUiState())
        private set

    val name = projectState.title

    init {
        viewModelScope.launch {
            projectState = itemsRepository.getIncubator(itemId)
                .filterNotNull()
                .first()
                .toIncubatorUiState()

            incubatorTime.addAll(itemsRepository.getTimeList(itemId))
        }
    }

    fun updateUiState(itemDetails: IncubatorUiState) {
        projectState =
            itemDetails
    }

    suspend fun saveItem() {
        itemsRepository.updateIncubator(projectState.toIncubatorTable())
        workRepository.cancelAllNotifications(name)

        incubatorTime.forEach {
            itemsRepository.deleteTime(it)
        }

        incubatorTime.forEach {
            itemsRepository.insertTime(it)
        }

        workRepository.scheduleReminder(incubatorTime.toMutableList(), projectState.title)
    }

    suspend fun archivItem() {
        workRepository.cancelAllNotifications(projectState.title)
        itemsRepository.updateIncubator(projectState.toIncubatorTable())
    }

    suspend fun deleteItem() {
        workRepository.cancelAllNotifications(projectState.title)
        itemsRepository.deleteIncubator(projectState.toIncubatorTable())
    }

    private val incubatorTime = mutableStateListOf<Time>()
    val incubatorTimeList:List<Time> get() = incubatorTime

    fun addItem(time: Time) {
        incubatorTime.add(time)
    }

    fun delItem(id:Int) {
        incubatorTime.removeAt(id)
    }

    fun updateItem(index: Int, name: String) {
        if (index in incubatorTime.indices) {
            incubatorTime[index].time = name
        }
    }
}

fun IncubatorUiState.toIncubatorTable(): IncubatorTable = IncubatorTable(
    id,
    title,
    type,
    data,
    eggAll.toInt(),
    eggAllEND.toInt(),
    airing.toString(),
    over.toString(),
    arhive,
    dateEnd,
    note
)

fun IncubatorTable.toIncubatorUiState(): IncubatorUiState = IncubatorUiState(
    id, title,
    type,
    data,
    eggAll.toString(),
    eggAllEND.toString(),
    airing.toBoolean(),
    over.toBoolean(),
    arhive,
    dateEnd,
    note
)


data class IncubatorUiState(
    val id: Long = 0,
    val title: String = "",
    val type: String = "",
    val data: String = "",
    val eggAll: String = "",
    val eggAllEND: String = "",
    val airing: Boolean = false,
    val over: Boolean = false,
    val arhive: String = "",
    val dateEnd: String = "",
    val note :String = ""
)