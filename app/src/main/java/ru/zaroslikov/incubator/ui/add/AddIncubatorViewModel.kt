package ru.zaroslikov.incubator.ui.add

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.data.ItemsRepository
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.ferma.Value
import ru.zaroslikov.incubator.data.work.WorkRepository
import ru.zaroslikov.incubator.ui.add.setIdPT
import ru.zaroslikov.incubator.ui.add.setIdPTTime
import ru.zaroslikov.incubator.ui.incubator.IncubatorUiState
import ru.zaroslikov.incubator.ui.incubator.toIncubatorTable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

class AddIncubatorViewModel(
    private val itemsRepository: ItemsRepository,
    private val waterRepository: WorkRepository
) : ViewModel() {

    private val format = SimpleDateFormat("dd.MM.yyyy")
    private val calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    private val formattedDate: String = format.format(calendar.timeInMillis)

    var incubatorUiState by mutableStateOf(
        IncubatorUiState(
            id = 0,
            title = "",
            type = "Курицы",
            data = formattedDate,
            eggAll = "0",
            eggAllEND = "0",
            airing = false,
            over = false,
            arhive = "0",
            dateEnd = ""
        )
    )
        private set


    fun updateUiState(itemDetails: IncubatorUiState) {
        incubatorUiState =
            itemDetails
    }

    fun saveIncubator(valueList: MutableList<Value>, timeList: MutableList<Time>) {
        viewModelScope.launch {

            val idPT = itemsRepository.insertIncubatorLong(incubatorUiState.toIncubatorTable())

            setIdPT(valueList, idPT).forEach {
                itemsRepository.insertValue(it)
            }

            setIdPTTime(timeList, idPT).forEach {
                itemsRepository.insertTime(it)
            }

            waterRepository.scheduleReminder(timeList, incubatorUiState.title)
        }
    }



    private val valueArchive = mutableStateOf<List<Value>>(emptyList())
    val valueArchiveList: State<List<Value>> = valueArchive

    fun getValueArchiveList(idPT: Long) {
        viewModelScope.launch {
            valueArchive.value = itemsRepository.getValueArchive(idPT)
        }
    }

    private val incubatorArchive = mutableStateOf<List<IncubatorTable>>(emptyList())
    val incubatorArchiveList: State<List<IncubatorTable>> = incubatorArchive

    fun getIncubatorArchiveList(type: String) {
        viewModelScope.launch {
            incubatorArchive.value = itemsRepository.getIncubatorArchiveList(type)
        }
    }
}
