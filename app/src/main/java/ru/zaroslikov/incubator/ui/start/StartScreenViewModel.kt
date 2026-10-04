package ru.zaroslikov.incubator.ui.start

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.analytics.reportIncubationOutcomes
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.reopened
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.model.stoppedEarly
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.todayText
import java.util.Date

sealed interface StartIntent {
    /**
     * Убрать инкубатор в архив или вернуть обратно.
     *
     * Инкубатор приходит целиком, а не одним id: карточку, о которой спрашивали, экран
     * ищет в состоянии заново — пока висел вопрос, поток мог её пересчитать, — и то, что
     * он нашёл, и есть то, что надо записать.
     */
    data class SetIncubatorHidden(val incubator: Incubator, val hidden: Boolean) : StartIntent

    /** Удалить инкубатор со всем, что в нём лежит; спрашивает об этом экран. */
    data class DeleteIncubator(val incubator: Incubator) : StartIntent
}

/**
 * Эффектов у стартового экрана нет: он ничего не сохраняет и ничего не закрывает —
 * список приходит из базы сам, а оба диалога закрывает та же композиция, что их
 * открыла. Пустой интерфейс объявлен потому, что третий типовой параметр
 * [MviViewModel] обязателен.
 */
sealed interface StartEffect

class StartScreenViewModel(
    private val fermaRepository: ItemsRepository,
    private val workRepository: WorkRepository,
) : MviViewModel<StartUiState, StartIntent, StartEffect>() {

    /**
     * Состояние — функция от базы целиком: своего экран не хранит ничего, поэтому
     * локального потока здесь нет и `combine` со `stateIn` и есть всё состояние.
     */
    override val state: StateFlow<StartUiState> =
        combine(
            fermaRepository.getAllIncubators(),
            fermaRepository.getAllBatches(),
            fermaRepository.getAllSpecies(),
            // Свои виды нужны ради одного: даты вывода на карточке. Срок своего вида
            // лежит в базе, а не в коде, и без каталога закладка по нему была бы
            // «без срока» — карточка молчала бы о выводе, который на самом деле известен.
            fermaRepository.getCustomSpecies(),
        ) { incubators, batches, species, customSpecies ->
            val catalog = SpeciesCatalog(customSpecies)
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
                    batches = own.size,
                    nearestHatchMillis = active.mapNotNull { hatchMillis(it, catalog) }.minOrNull(),
                )
            }

            // Наверху списка — те, где сейчас что-то греется, и чем больше идущих
            // закладок, тем выше. Список отсортирован по `_id`, то есть по порядку
            // добавления, и заведённый первым инкубатор оставался первым, даже когда
            // он год как пуст, а работа идёт в трёх последних — до них приходилось
            // долистывать каждый раз. Сортировка устойчивая, поэтому внутри одного
            // числа закладок порядок добавления сохраняется: одинаковые карточки не
            // перетасовываются между ответами базы. Архивные считаются тем же ключом,
            // и у них он ноль по построению — архив прерывает все идущие закладки.
            val sorted = cards.sortedByDescending { it.activeBatches }
            StartUiState(
                cards = sorted.filter { !it.incubator.hidden },
                archivedCards = sorted.filter { it.incubator.hidden },
                loading = false,
            )
        }
            // Сборка состояния уходит с главного потока. `stateIn(viewModelScope)` собирает
            // на `Dispatchers.Main.immediate`, а здесь на каждый ответ базы — проходы по
            // всем закладкам хозяйства и арифметика `:domain` поверх них; на главном
            // потоке это кадры, отданные не отрисовке.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, MviSharing.WhileVisible, StartUiState())

    override fun onIntent(intent: StartIntent) {
        when (intent) {
            is StartIntent.SetIncubatorHidden ->
                setIncubatorHidden(intent.incubator, intent.hidden)

            is StartIntent.DeleteIncubator -> deleteIncubator(intent.incubator)
        }
    }

    /**
     * Убирает инкубатор в архив и возвращает обратно.
     *
     * **Архив прерывает все идущие закладки** — [stoppedEarly] с причиной [ARCHIVE_END_REASON],
     * одной датой на всех: закладка в выведенном из работы инкубаторе не закладка, её яйца нельзя
     * считать идущими и будить по ним каждое утро. Уже завершённые не трогаются. Спрашивает об этом
     * экран (`ConfirmArchiveIncubatorDialog`), а не эта функция.
     *
     * **Возврат из архива ничего не отменяет**: снимает только «просмотр», а закладки возвращают в
     * работу поштучно через [reopened] — прерванные архивом и до него уже не отличить.
     *
     * Напоминания снимаются по одной закладке, как в [deleteIncubator]. Сперва запись, потом
     * снятие: потерянное снятие безобидно (работа перечитает закладку), потерянная запись оставила
     * бы идущую закладку без будильников.
     */
    private fun setIncubatorHidden(incubator: Incubator, hidden: Boolean) {
        viewModelScope.launch {
            fermaRepository.updateIncubator(incubator.copy(hidden = hidden))
            if (!hidden) return@launch
            // Список закладок берётся из базы прямо сейчас, а не из состояния экрана: между
            // нажатием и подтверждением закладку могли добавить.
            // Момент — сейчас, и час тоже: им кончается счёт за свет. Без часа он шёл бы до
            // часа закладки в день архивации, то есть мог уйти на полсуток вперёд.
            val now = Date()
            val endDate = todayText()
            val endTime = clockText(now)
            val stopped = fermaRepository.getBatchesFor(incubator.id).first()
                .filter { it.status == BatchStatus.Active }
                .map { batch ->
                    val interrupted = batch.stoppedEarly(ARCHIVE_END_REASON, endDate, endTime)
                    fermaRepository.updateBatch(interrupted)
                    interrupted
                }
            // Третий путь завершения инкубации, и отчёт по нему такой же, как у двух
            // остальных: закладка кончилась, и вопрос «какая модель выводит лучше»
            // обязан её учесть — иначе прерванные архивом закладки выпали бы из
            // статистики устройства, а именно они и означают, что с ним что-то не так.
            // После записи, одним чтением базы на все: см. reportIncubationOutcomes.
            fermaRepository.reportIncubationOutcomes(incubator.id, stopped)
            // Один пересчёт на все прерванные закладки: расписание выводится из базы, и
            // его дело — узнать, что идущих закладок в этом устройстве больше нет.
            workRepository.refreshReminders()
        }
    }

    /**
     * Удаляет инкубатор со всем, что в нём лежит.
     *
     * Закладки, их дни, замеры, овоскопирования и строки напоминаний забирает
     * `ON DELETE CASCADE` — цепочка идёт от `Incubator` через `Batch` до всех его
     * дочерних таблиц. А вот само расписание живёт в WorkManager, а не в базе, и каскад
     * до него не достаёт, поэтому после удаления его пересчитывают. Одним вызовом на
     * весь инкубатор: расписание выводится из базы целиком, и перебирать закладки по
     * одной — как приходилось, пока работа была у каждой своя, — больше незачем.
     */
    private fun deleteIncubator(incubator: Incubator) {
        viewModelScope.launch {
            fermaRepository.deleteIncubator(incubator)
            // Один пересчёт на всё удаление: расписание считается по базе целиком, и
            // перебирать закладки по одной, как раньше, больше незачем — их там уже нет.
            workRepository.refreshReminders()
        }
    }

    /** Дата вывода закладки: дата закладки плюс срок инкубации ведущего вида. */
    private fun hatchMillis(batch: Batch, catalog: SpeciesCatalog): Long? {
        val days = catalog.incubationDays(batch.type) ?: return null
        val start = parseDate(batch.data) ?: return null
        return start.plusDays(days).time
    }

    companion object {
        /**
         * Причина, с которой архив инкубатора прерывает его закладки.
         *
         * Константа, а не литерал на месте: её же читает диалог подтверждения, чтобы
         * пообещать ровно то, что потом окажется на карточке, и по ней же тест отличает
         * закладку, прерванную архивом, от прерванной руками.
         */
        const val ARCHIVE_END_REASON = "Инкубатор переведён в архив"
    }
}

/** Инкубатор вместе с агрегатами по его закладкам — всё, что рисует карточка. */
@Immutable
data class IncubatorCardUi(
    val incubator: Incubator,
    val species: List<String> = emptyList(),
    val eggs: Int = 0,
    val activeBatches: Int = 0,
    /**
     * Сколько всего закладок в инкубаторе — вместе с завершёнными и убранными.
     *
     * Нужно вопросу об удалении: `ON DELETE CASCADE` унесёт их все, и назвать это
     * число до нажатия — единственный способ показать цену действия.
     */
    val batches: Int = 0,
    val nearestHatchMillis: Long? = null,
)

@Immutable
data class StartUiState(
    /** Что видно в списке — всё, кроме убранного в архив. */
    val cards: List<IncubatorCardUi> = emptyList(),
    /**
     * Убранные в архив — отдельным списком, как и спрятанные закладки в инкубаторе.
     * Показываются на соседней вкладке переключателя над списком.
     */
    val archivedCards: List<IncubatorCardUi> = emptyList(),
    /**
     * База ещё не ответила — не путать с «инкубаторов нет».
     *
     * Без этого флага оба состояния выглядели одинаково: `cards` пуст и там и там, и
     * экран успевал поздороваться «Добро пожаловать!» с человеком, у которого пять
     * инкубаторов. Значение по умолчанию — `true`, потому что это и есть начальное
     * значение потока: сперва не знаем, потом узнаём.
     */
    val loading: Boolean = true,
)
