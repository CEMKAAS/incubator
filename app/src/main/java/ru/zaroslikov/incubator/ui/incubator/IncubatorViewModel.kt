package ru.zaroslikov.incubator.ui.incubator

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.exportable
import ru.zaroslikov.incubator.domain.model.reopened
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.domain.stats.IncubatorFinance
import ru.zaroslikov.incubator.domain.stats.IncubatorStats
import ru.zaroslikov.incubator.domain.stats.incubatorFinance
import ru.zaroslikov.incubator.domain.stats.incubatorStats
import ru.zaroslikov.incubator.rustore.IS_RUSTORE_BUILD
import ru.zaroslikov.incubator.rustore.ReviewController
import ru.zaroslikov.incubator.transfer.ScheduleTransferController
import ru.zaroslikov.incubator.transfer.ScheduleTransferState
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel

sealed interface IncubatorIntent {
    /**
     * Удаляет закладку — из меню её карточки, после подтверждения в диалоге.
     *
     * Уведомления снимаются по идентификатору закладки — той самой метке, которой
     * WorkManager их пометил. Без этого напоминания удалённой закладки продолжали бы
     * будить по утрам: они живут в WorkManager, а не в базе, и каскад по внешнему ключу
     * до них не достаёт. Всё остальное — дни, замеры, овоскопирования, строки
     * напоминаний — забирает `ON DELETE CASCADE`.
     */
    data class DeleteBatch(val batch: Batch) : IncubatorIntent

    /**
     * Убирает завершённую закладку из списка и возвращает обратно.
     *
     * Ничего не спрашивает: итог у такой закладки уже записан, а спрятать её —
     * решение о том, что показывать на экране, и отменяется оно тем же пунктом меню.
     * Идущую закладку прятать нельзя, и меню такого пункта ей не предлагает: за ней
     * следят каждый день, спрятанная она просто потерялась бы.
     */
    data class SetBatchHidden(val batch: Batch, val hidden: Boolean) : IncubatorIntent

    /**
     * Возвращает завершённую закладку в работу — что при этом стирается, решает
     * [reopened].
     *
     * **Напоминания воскресают вместе с закладкой.** Раньше не воскресали, и причина
     * была та, что времена якобы знает только форма закладки, — она неверна: строки
     * `Batch_time` завершение не удаляло и не удаляет, они лежат в базе всё это время.
     * Снималась при завершении работа в планировщике, а не расписание, поэтому вернуть
     * его есть откуда, и это ровно то же самое, что делает пересборка после импорта.
     * Молчащая закладка, снова помеченная как идущая, была бы неотличима от поломки.
     */
    data class UnarchiveBatch(val batch: Batch) : IncubatorIntent

    /** Пишет файл расписания в место, выбранное в системном окне. Только [Batch.exportable]. */
    data class ExportBatch(val batch: Batch, val target: Uri) : IncubatorIntent

    /** Готовит файл расписания к отправке в другое приложение. */
    data class ShareBatch(val batch: Batch) : IncubatorIntent

    /** Итог переноса прочитан экраном — гасим его, чтобы диалог не открылся заново. */
    data object ClearScheduleTransfer : IncubatorIntent

    /**
     * Карточку итога закрыли. После вывода с птенцами — единственная просьба оценить
     * приложение (`ReviewController`); прерванная закладка и пустой вывод — худшая минута
     * для такой просьбы. Только в сборке для RuStore, не чаще раза на версию.
     */
    data class CelebrationClosed(val withChicks: Boolean) : IncubatorIntent
}

/**
 * Одноразовых событий у экрана нет: всё, что он делает, меняет базу, а список на неё
 * подписан. Тип объявлен, потому что его требует [MviViewModel].
 */
sealed interface IncubatorEffect

/**
 * Один инкубатор: его закладки, статистика и финансы.
 *
 * Состояние целиком — функция от базы, поэтому `combine` со `stateIn`, а не коллекторы
 * в `init`: подписка живёт, пока на экран смотрят, и гаснет через пять секунд после
 * ухода — правило из корневого `CLAUDE.md`, иначе Room пересчитывал бы хозяйство ради
 * экрана, на который никто не смотрит.
 */
class IncubatorViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository,
    private val scheduleTransfer: ScheduleTransferController,
    private val review: ReviewController,
) : MviViewModel<IncubatorUiState, IncubatorIntent, IncubatorEffect>() {

    private val incubatorId: Long = checkNotNull(savedStateHandle[IncubatorDestination.itemIdArg])

    override val state: StateFlow<IncubatorUiState> =
        combine(
            itemsRepository.getIncubator(incubatorId),
            itemsRepository.getBatchesFor(incubatorId),
            // Овоскопирования нужны только вкладке «Статистика» — ради отбраковки,
            // которая складывается из графы закладки и итогов её овоскопирований.
            itemsRepository.getCandlingsFor(incubatorId),
            // Свои виды — ради срока: «День N/M», дата вывода и момент, когда срок
            // вышел, для своего вида лежат в базе, и знает о них только каталог.
            itemsRepository.getCustomSpecies(),
        ) { incubator, batches, candlings, customSpecies ->
            // Инкубатор удалён, пока экран открыт, — обычное состояние «показывать нечего».
            if (incubator == null) {
                return@combine IncubatorUiState(incubatorId = incubatorId, loading = false)
            }
            val active = batches.filter { it.arhive == "0" }
            val finished = batches.filter { it.arhive != "0" }
            // Убранные в архив закладки в статистику не входят — ни в шапку, ни в
            // «Статистику», ни в «Финансы»: архив — это то, что человек отложил из
            // подсчётов. Спрятать можно только завершённую, так что идущие все здесь.
            val counted = batches.filter { !it.hidden }
            IncubatorUiState(
                incubatorId = incubatorId,
                incubator = incubator,
                readOnly = incubator.hidden,
                // Активные закладки сверху, спрятанные — отдельным списком.
                batches = active + finished.filter { !it.hidden },
                hiddenBatches = finished.filter { it.hidden },
                activeCount = active.size,
                eggsInWork = active.sumOf { it.eggAll },
                hatched = counted.sumOf { it.eggAllEND },
                stats = incubatorStats(counted, candlings, catalog = SpeciesCatalog(customSpecies)),
                // Овоскопирования финансам не нужны: отбраковка на них — это яйца, а
                // деньгами закладка считается по своим двум ценам и цене инкубатора.
                finance = incubatorFinance(
                    incubator.price,
                    counted,
                    // Свет — без архивных: убранная закладка отложена из подсчётов и
                    // общих часов с соседками не делит (см. batchElectricity).
                    batchElectricity(counted, mapOf(incubator.id to incubator)),
                ),
                catalog = SpeciesCatalog(customSpecies),
                loading = false,
            )
        }
            // Арифметика `:domain` на каждый ответ базы — не на главном потоке, куда
            // `stateIn(viewModelScope)` иначе её посадил бы.
            .flowOn(Dispatchers.Default)
            // Состояние переноса — вторым `combine`, после `flowOn`: этот шаг только
            // копирует поле, и уносить его с главного потока не за что.
            .combine(scheduleTransfer.state) { base, transfer ->
                base.copy(scheduleTransfer = transfer)
            }
            // Идентификатор есть и в начальном значении: шторки, восстановленные после
            // поворота, открываются раньше, чем база ответит.
            .stateIn(viewModelScope, MviSharing.WhileVisible, IncubatorUiState(incubatorId = incubatorId))

    override fun onIntent(intent: IncubatorIntent) {
        when (intent) {
            is IncubatorIntent.DeleteBatch -> deleteBatch(intent.batch)
            is IncubatorIntent.SetBatchHidden -> setBatchHidden(intent.batch, intent.hidden)
            is IncubatorIntent.UnarchiveBatch -> unarchiveBatch(intent.batch)
            is IncubatorIntent.ExportBatch -> exportBatch(intent.batch, intent.target)
            is IncubatorIntent.ShareBatch -> shareBatch(intent.batch)
            IncubatorIntent.ClearScheduleTransfer -> scheduleTransfer.clear()
            is IncubatorIntent.CelebrationClosed ->
                if (intent.withChicks && IS_RUSTORE_BUILD) review.offerAfterHatch(BuildConfig.VERSION_CODE)
        }
    }

    private fun deleteBatch(batch: Batch) {
        if (state.value.readOnly) return
        viewModelScope.launch {
            itemsRepository.deleteBatch(batch)
            // После удаления, а не до: расписание считается по базе, и пересчёт до
            // удаления вернул бы в него закладку, которой уже не будет.
            workRepository.refreshReminders()
        }
    }

    private fun setBatchHidden(batch: Batch, hidden: Boolean) {
        if (state.value.readOnly) return
        if (batch.arhive == "0") return
        viewModelScope.launch { itemsRepository.updateBatch(batch.copy(hidden = hidden)) }
    }

    private fun unarchiveBatch(batch: Batch) {
        if (state.value.readOnly) return
        viewModelScope.launch {
            val reopened = batch.reopened()
            itemsRepository.updateBatch(reopened)
            workRepository.refreshReminders()
        }
    }

    private fun exportBatch(batch: Batch, target: Uri) {
        if (batch.exportable) scheduleTransfer.saveToFile(batch, target)
    }

    private fun shareBatch(batch: Batch) {
        if (batch.exportable) scheduleTransfer.share(batch)
    }
}

@Immutable
data class IncubatorUiState(
    /** Из аргумента маршрута — известен до ответа базы, в отличие от [incubator]. */
    val incubatorId: Long = 0L,
    val incubator: Incubator? = null,
    /**
     * Инкубатор убран в архив — экран показывает его только на просмотр.
     *
     * Закладки в архивном инкубаторе нельзя ни добавить, ни изменить, ни удалить: устройство
     * выведено из работы, и всё, что с ним осталось, — это записи о том, как оно работало.
     * Ограничение снимается там же, где ставится, — «Вернуть в список» на главном экране,
     * — поэтому запрет и не спрашивает подтверждений: он отменяется одним нажатием.
     *
     * Считается из [Incubator.hidden], а не хранится вторым полем: иначе появилось бы
     * состояние, в котором «в архиве» и «только просмотр» могут разойтись.
     */
    val readOnly: Boolean = false,
    /** Что видно в списке: активные, следом завершённые, кроме убранных в архив. */
    val batches: List<Batch> = emptyList(),
    /**
     * Убранные в архив — отдельным списком, а не флагом внутри [batches]: показываются
     * только по кнопке «Архив» и в показатели экрана не входят (см. корневой `CLAUDE.md`).
     */
    val hiddenBatches: List<Batch> = emptyList(),
    val activeCount: Int = 0,
    val eggsInWork: Int = 0,
    val hatched: Int = 0,
    /**
     * Всё, что показывает вкладка «Статистика»: итоги сверху, разрезы по видам и
     * породам, история выводов. Считается в `:domain` — см. [incubatorStats].
     */
    val stats: IncubatorStats = IncubatorStats(),
    /**
     * Всё, что показывает вкладка «Финансы»: баланс, показатели и разбор по закладкам.
     * Считается в `:domain` — см. [incubatorFinance].
     */
    val finance: IncubatorFinance = IncubatorFinance(),
    /**
     * Виды птицы — встроенные и свои. Через него экран спрашивает срок закладки: до
     * своих видов хватало функции по имени, теперь ответ зависит от того, что лежит в
     * базе. Пока база не ответила — [SpeciesCatalog.EMPTY], то есть одни встроенные.
     */
    val catalog: SpeciesCatalog = SpeciesCatalog.EMPTY,
    /**
     * Экспорт расписания закладки в файл — из меню её карточки. Значение приходит прямо
     * из контроллера, как перенос базы в настройках: пересобранное здесь, оно умирало бы
     * вместе с экраном, а запись файла — нет.
     */
    val scheduleTransfer: ScheduleTransferState = ScheduleTransferState.Idle,
    /**
     * База ещё не ответила — не то же самое, что «закладок нет».
     *
     * Пустой `batches` до первого ответа неотличим от пустого инкубатора, и экран
     * успевал сообщить «Закладок пока нет» устройству, в котором их десять. Начальное
     * значение потока — именно это состояние, поэтому по умолчанию `true`.
     */
    val loading: Boolean = true,
)
