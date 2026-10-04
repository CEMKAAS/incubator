package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import java.util.Date
import ru.zaroslikov.incubator.ui.incubator.batchElectricity

/**
 * Завершение партии — нескольких закладок, заложенных одним нажатием на разные породы.
 *
 * Отдельная ViewModel, а не [BatchDetailViewModel] в цикле: здесь несколько закладок и по
 * каждой лишь остаток яиц. Что записывается в каждую, решает тот же [finishedOnTime] из `:domain`.
 */
class FinishGroupViewModel(
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository,
) : StatefulMviViewModel<FinishGroupState, FinishGroupIntent, FinishGroupEffect>(FinishGroupState()) {

    private var loadJob: Job? = null

    /** Защита от второго нажатия, пока запись идёт (как `BatchDetailViewModel.finishing`); сбрасывается в [load]. */
    private var finishing = false

    override fun onIntent(intent: FinishGroupIntent) {
        when (intent) {
            is FinishGroupIntent.Load -> load(intent.incubatorId, intent.batchIds)
            is FinishGroupIntent.Finish -> finish(intent.outcomes, intent.moment)
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
                            // Ручная отбраковка сюда не входит: при завершении она
                            // пересчитывается остатком (`finishedOnTime`).
                            candling = candlings.filter { it.idPT == batch.id }
                                .sumOf { it.rejected },
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
    private fun finish(outcomes: Map<Long, HatchOutcome>, moment: FinishMoment) {
        val state = current
        if (!state.loaded || outcomes.isEmpty() || finishing) return
        val finished = state.items
            .mapNotNull { item ->
                outcomes[item.batch.id]?.let {
                    item.batch.finishedOnTime(it, moment.date, item.candling, moment.time)
                }
            }
        if (finished.isEmpty()) return
        finishing = true
        loadJob?.cancel()
        viewModelScope.launch {
            // Сперва запись, потом напоминания — тот же порядок и по той же причине, что
            // в `BatchDetailViewModel.archive`.
            finished.forEach { itemsRepository.updateBatch(it) }
            workRepository.refreshReminders()
            finished.forEach { batch ->
                // Как у диалога одной закладки: партия из двух пород — два завершения.
                Analytics.report(
                    Events.FINISH_ON_TIME,
                    mapOf(
                        "Имя" to batch.title,
                        "Тип" to batch.type,
                        "Кол-во" to batch.eggAll,
                        "Кол-во пос" to batch.eggAllEND,
                        "День" to incubationDay(batchStartMoment(batch), null, Date()),
                        "Партия" to state.items.size,
                    ),
                )
            }
            // До эффекта: по нему закрывается диалог, а с ним уходит и эта область корутин.
            itemsRepository.reportIncubationOutcomes(state.incubatorId, finished)
            // Сводка по каждой завершённой породе, и по нулевой тоже; салют решает сумма.
            // Не вылупившееся — отбраковка (так пишет `finishedOnTime`).
            // Свет — как в «Финансах»: без убранных в архив.
            val incubator = itemsRepository.getIncubator(state.incubatorId).first()
            val electricity = batchElectricity(
                itemsRepository.getBatchesFor(state.incubatorId).first().filter { !it.hidden },
                incubator?.let { mapOf(it.id to it) }.orEmpty(),
            )
            val summaries = finished.map { batch ->
                hatchSummaryOf(
                    batch,
                    batch.eggAll - batch.eggAllEND,
                    state.catalog.incubationDays(batch.type),
                    electricity[batch.id],
                )
            }
            sendEffect(FinishGroupEffect.Finished(summaries))
        }
    }
}

/** Одна порода партии и сколько её яиц убрано на овоскопированиях. */
@Immutable
data class FinishGroupItem(
    val batch: Batch,
    val candling: Int,
)

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
    data class Finish(
        val outcomes: Map<Long, HatchOutcome>,
        /** Когда выключили инкубатор или вынули птенцов — один на партию. */
        val moment: FinishMoment,
    ) : FinishGroupIntent
}

sealed interface FinishGroupEffect {
    /**
     * Записано; [summaries] — итог каждой завершённой породы, для поздравления. Салют
     * или нет, решает сумма птенцов по ним — на экране, где оно и показывается.
     */
    data class Finished(val summaries: List<HatchSummary>) : FinishGroupEffect
}
