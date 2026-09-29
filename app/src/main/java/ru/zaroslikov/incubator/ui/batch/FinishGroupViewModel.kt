package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.analytics.reportIncubationOutcomes
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.HatchOutcome
import ru.zaroslikov.incubator.domain.model.finishedOnTime
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.domain.stats.hatchSummaryOf
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.today
import ru.zaroslikov.incubator.ui.todayText

/**
 * Завершение партии — нескольких закладок, заложенных одним нажатием на разные породы.
 *
 * Отдельная ViewModel, а не [BatchDetailViewModel] в цикле: та держит одну закладку со
 * всем её расписанием и замерами, а здесь нужно другое — несколько закладок и по каждой
 * лишь остаток яиц. Что записывается в каждую, решает тот же [finishedOnTime] из
 * `:domain`, так что итог породы, внесённый здесь, ничем не отличается от внесённого из
 * её собственной шторки.
 */
class FinishGroupViewModel(
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository,
) : StatefulMviViewModel<FinishGroupState, FinishGroupIntent, FinishGroupEffect>(FinishGroupState()) {

    private var loadJob: Job? = null

    /**
     * Запись уже идёт — второе нажатие «Завершить» в это окно писало бы партию дважды и
     * оставляло второй `Finished` следующему открытию диалога. Та же защита и по той же
     * причине, что `BatchDetailViewModel.finishing`; сбрасывается в [load].
     */
    private var finishing = false

    override fun onIntent(intent: FinishGroupIntent) {
        when (intent) {
            is FinishGroupIntent.Load -> load(intent.incubatorId, intent.batchIds)
            is FinishGroupIntent.Finish -> finish(intent.outcomes)
        }
    }

    private fun load(incubatorId: Long, batchIds: List<Long>) {
        reduce { FinishGroupState() }
        loadJob?.cancel()
        finishing = false
        if (batchIds.isEmpty()) return
        // На потоке, а не одним чтением: пока диалог открыт, одну из пород могут
        // завершить из другого места — тогда её строка уходит, а не записывается дважды.
        loadJob = viewModelScope.launch {
            combine(
                itemsRepository.getBatchesFor(incubatorId),
                itemsRepository.getCandlingsFor(incubatorId),
                // Каталог — ради срока вида в сводке поздравления: у своего вида он
                // лежит в базе, и по одному имени его не узнать.
                itemsRepository.getCustomSpecies(),
            ) { batches, candlings, customSpecies ->
                val byId = batches.associateBy { it.id }
                val items = batchIds
                    .mapNotNull { byId[it] }
                    .filter { it.status == BatchStatus.Active }
                    .map { batch ->
                        FinishGroupItem(
                            batch = batch,
                            // Тот же двойной учёт, что `rejectedTotal` в шторке закладки:
                            // овоскопирования плюс отбраковка, введённая руками.
                            rejected = candlings.filter { it.idPT == batch.id }
                                .sumOf { it.rejected } + batch.eggRejected,
                        )
                    }
                items to SpeciesCatalog(customSpecies)
            }.collect { (items, catalog) ->
                reduce { copy(loaded = true, incubatorId = incubatorId, items = items, catalog = catalog) }
            }
        }
    }

    /**
     * Записывает итог тем закладкам партии, по которым его внесли; остальные остаются в
     * инкубации — вывод по породе может идти дольше, и итог по ней внесут позже.
     */
    private fun finish(outcomes: Map<Long, HatchOutcome>) {
        val state = current
        if (!state.loaded || outcomes.isEmpty() || finishing) return
        val dateEnd = todayText()
        val finishedItems = state.items
            .mapNotNull { item ->
                outcomes[item.batch.id]?.let { item.batch.finishedOnTime(it, dateEnd) to item.rejected }
            }
        val finished = finishedItems.map { it.first }
        if (finished.isEmpty()) return
        finishing = true
        loadJob?.cancel()
        viewModelScope.launch {
            // Сперва запись, потом напоминания — тот же порядок и по той же причине, что
            // в `BatchDetailViewModel.archive`.
            finished.forEach { itemsRepository.updateBatch(it) }
            workRepository.refreshReminders()
            finished.forEach { batch ->
                // Имя события и параметры — те же, что у диалога одной закладки: партия
                // из двух пород — это два завершения, и считаться они должны как два.
                Analytics.report(
                    Events.FINISH_ON_TIME,
                    mapOf(
                        "Имя" to batch.title,
                        "Тип" to batch.type,
                        "Кол-во" to batch.eggAll,
                        "Кол-во пос" to batch.eggAllEND,
                        "День" to incubationDay(parseDate(batch.data), null, today()),
                        "Партия" to state.items.size,
                    ),
                )
            }
            // До эффекта: по нему закрывается диалог, а с ним уходит и эта область корутин.
            itemsRepository.reportIncubationOutcomes(state.incubatorId, finished)
            // Сводка по каждой завершённой породе — и по той, где вывелся ноль: партия
            // одна, и поздравление показывает её целиком, а салют или нет — решает сумма.
            val summaries = finishedItems.map { (batch, rejected) ->
                hatchSummaryOf(batch, rejected, state.catalog.incubationDays(batch.type))
            }
            sendEffect(FinishGroupEffect.Finished(summaries))
        }
    }
}

/** Одна порода партии и сколько её яиц дожило до вывода. */
@Immutable
data class FinishGroupItem(
    val batch: Batch,
    val rejected: Int,
) {
    val remaining: Int get() = (batch.eggAll - rejected).coerceAtLeast(0)
}

@Immutable
data class FinishGroupState(
    val loaded: Boolean = false,
    val incubatorId: Long = 0,
    val items: List<FinishGroupItem> = emptyList(),
    /** Виды птицы — ради срока в сводке поздравления; до ответа базы встроенные. */
    val catalog: SpeciesCatalog = SpeciesCatalog.EMPTY,
)

sealed interface FinishGroupIntent {
    data class Load(val incubatorId: Long, val batchIds: List<Long>) : FinishGroupIntent

    /** Итог по закладкам партии; закладки, которых в карте нет, остаются в инкубации. */
    data class Finish(val outcomes: Map<Long, HatchOutcome>) : FinishGroupIntent
}

sealed interface FinishGroupEffect {
    /**
     * Записано; [summaries] — итог каждой завершённой породы, для поздравления. Салют
     * или нет, решает сумма птенцов по ним — на экране, где оно и показывается.
     */
    data class Finished(val summaries: List<HatchSummary>) : FinishGroupEffect
}
