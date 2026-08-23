package ru.zaroslikov.incubator.ui.incubator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.repository.ItemsRepository

class IncubatorViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
) : ViewModel() {

    val incubatorId: Long = checkNotNull(savedStateHandle[IncubatorDestination.itemIdArg])

    val uiState: StateFlow<IncubatorUiState> =
        combine(
            itemsRepository.getIncubator(incubatorId),
            itemsRepository.getBatchesFor(incubatorId),
            itemsRepository.getAllSpecies()
        ) { incubator, batches, species ->
            val speciesByBatch = species
                .groupBy { it.idPT }
                .mapValues { (_, rows) -> rows.map { it.species } }
            val active = batches.filter { it.arhive == "0" }
            val finished = batches.filter { it.arhive != "0" }
            val finishedEggs = finished.sumOf { it.eggAll }
            IncubatorUiState(
                incubator = incubator,
                // Активные закладки сверху: в макете завершённая утка стоит первой лишь
                // потому, что там так лёг список, а полезнее видеть текущие.
                batches = active + finished,
                speciesByBatch = speciesByBatch,
                activeCount = active.size,
                finishedCount = finished.size,
                eggsInWork = active.sumOf { it.eggAll },
                totalEggs = batches.sumOf { it.eggAll },
                hatched = batches.sumOf { it.eggAllEND },
                // Средний вывод считаем только по завершённым закладкам: у активных
                // eggAllEND ещё ноль и он занизил бы процент.
                hatchRate = if (finishedEggs > 0) {
                    finished.sumOf { it.eggAllEND } * 100 / finishedEggs
                } else {
                    null
                },
                eggsBySpecies = batches
                    .groupBy { it.type }
                    .map { (type, rows) -> type to rows.sumOf { it.eggAll } }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
            initialValue = IncubatorUiState()
        )

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}

data class IncubatorUiState(
    val incubator: Incubator? = null,
    val batches: List<Batch> = emptyList(),
    val speciesByBatch: Map<Long, List<String>> = emptyMap(),
    val activeCount: Int = 0,
    val finishedCount: Int = 0,
    val eggsInWork: Int = 0,
    val totalEggs: Int = 0,
    val hatched: Int = 0,
    /** Процент вывода по завершённым закладкам; null — завершённых ещё нет. */
    val hatchRate: Int? = null,
    /** Вид птицы → заложено яиц, по убыванию. Порядок задаёт цвет столбца диаграммы. */
    val eggsBySpecies: List<Pair<String, Int>> = emptyList(),
)
