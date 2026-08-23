package ru.zaroslikov.incubator.ui.start


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.zaroslikov.incubator.domain.incubation.incubationDays
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class StartScreenViewModel(
    private val fermaRepository: ItemsRepository,
) : ViewModel() {

    val getAllProjectAct: StateFlow<StartUiState> =
        combine(
            fermaRepository.getAllIncubators(),
            fermaRepository.getAllBatches(),
            fermaRepository.getAllSpecies()
        ) { incubators, batches, species ->
            val speciesByBatch = species
                .groupBy { it.idPT }
                .mapValues { (_, rows) -> rows.map { it.species } }

            val cards = incubators.map { incubator ->
                val own = batches.filter { it.incubatorId == incubator.id }
                val active = own.filter { it.arhive == "0" }
                IncubatorCardUi(
                    incubator = incubator,
                    // Чипы собираются по всем активным закладкам инкубатора: в одном
                    // устройстве может лежать несколько видов сразу.
                    species = active
                        .flatMap { speciesByBatch[it.id] ?: listOf(it.type) }
                        .distinct(),
                    eggs = active.sumOf { it.eggAll },
                    activeBatches = active.size,
                    nearestHatchMillis = active.mapNotNull(::hatchMillis).minOrNull(),
                )
            }

            val allActive = batches.filter { it.arhive == "0" }
            StartUiState(
                cards = cards,
                activeCount = allActive.size,
                eggsInWork = allActive.sumOf { it.eggAll },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
            initialValue = StartUiState()
        )

    /** Дата вывода закладки: дата закладки плюс срок инкубации ведущего вида. */
    private fun hatchMillis(batch: Batch): Long? {
        val days = incubationDays(batch.type) ?: return null
        return try {
            val start = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).parse(batch.data)
                ?: return null
            Calendar.getInstance().apply {
                time = start
                add(Calendar.DAY_OF_YEAR, days)
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}

/** Инкубатор вместе с агрегатами по его закладкам — всё, что рисует карточка. */
data class IncubatorCardUi(
    val incubator: Incubator,
    val species: List<String> = emptyList(),
    val eggs: Int = 0,
    val activeBatches: Int = 0,
    val nearestHatchMillis: Long? = null,
)

data class StartUiState(
    val cards: List<IncubatorCardUi> = emptyList(),
    val activeCount: Int = 0,
    val eggsInWork: Int = 0,
)
