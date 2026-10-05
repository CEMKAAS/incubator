package ru.zaroslikov.incubator.ui.menu

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.stats.IncubatorFinance
import ru.zaroslikov.incubator.domain.stats.IncubatorStats
import ru.zaroslikov.incubator.domain.stats.incubatorFinance
import ru.zaroslikov.incubator.domain.stats.incubatorStats
import ru.zaroslikov.incubator.ui.incubator.batchElectricity
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel

sealed interface AnalyticsIntent {
    /**
     * Сохранить имя владельца — единственное, что экран здесь пишет.
     *
     * Пустое имя — законное значение: так его и стирают.
     */
    data class SaveName(val name: String) : AnalyticsIntent
}

/**
 * Эффектов у «Аналитики» нет, и интерфейс всё равно объявлен.
 *
 * Экран ничего не закрывает и никаких системных окон не открывает: он только читает
 * хозяйство и отдаёт одно имя. Но третий тип у [MviViewModel] обязателен, а `Nothing`
 * в этой позиции лишил бы будущий эффект места, куда его дописать.
 */
sealed interface AnalyticsEffect

/**
 * «Аналитика»: всё хозяйство одним счётом, плюс имя владельца. Считает теми же функциями `:domain`,
 * что и экран одного инкубатора, — иначе сводные числа разошлись бы с частными. Цена техники в
 * [incubatorFinance] — сумма цен всех инкубаторов (слагаемое расхода и знаменатель окупаемости);
 * меняются только подписи — [ru.zaroslikov.incubator.ui.incubator.TabScope].
 *
 * Состояние — чистая функция от базы: `combine` со `stateIn` прямо в [state].
 */
class AnalyticsViewModel(
    private val itemsRepository: ItemsRepository,
) : MviViewModel<AnalyticsUiState, AnalyticsIntent, AnalyticsEffect>() {

    override val state: StateFlow<AnalyticsUiState> =
        combine(
            itemsRepository.getUser(),
            itemsRepository.getAllIncubators(),
            itemsRepository.getAllBatches(),
            itemsRepository.getAllCandlings(),
            // Каталог — только чтобы история назвала овоскопирование своего вида по счёту.
            itemsRepository.getCustomSpecies(),
        ) { user, allIncubators, allBatches, candlings, customSpecies ->
            // Инкубатор в архиве выпадает из аналитики хозяйства целиком — его закладки,
            // его цена, он сам в счёте устройств: архив здесь значит «как будто не было»,
            // ровно как у отдельной закладки (просьба владельца, 2026-10-02).
            val incubators = allIncubators.filter { !it.hidden }
            val workingIds = incubators.mapTo(HashSet()) { it.id }
            val batches = allBatches.filter { it.incubatorId in workingIds }
            // Через status, а не сравнением arhive руками: корневой CLAUDE.md прямо
            // предписывает новому UI-коду спрашивать трёхзначный статус закладки.
            val active = batches.filter { it.status == BatchStatus.Active }
            // Убранные в архив закладки в аналитику хозяйства не входят — то же правило,
            // что на экране инкубатора (`IncubatorViewModel`), иначе сумма по хозяйству
            // разошлась бы с суммой его инкубаторов.
            val counted = batches.filter { !it.hidden }
            AnalyticsUiState(
                user = user,
                incubatorCount = incubators.size,
                activeBatches = active.size,
                eggsInWork = active.sumOf { it.eggAll },
                // Имена инкубаторов — только здесь: в истории хозяйства строка обязана
                // сказать, в каком устройстве это было, а на экране одного инкубатора
                // тот же вызов идёт без карты и строка остаётся прежней.
                stats = incubatorStats(
                    batches = counted,
                    candlings = candlings,
                    incubatorNames = incubators.associate { it.id to it.name },
                    catalog = SpeciesCatalog(customSpecies),
                ),
                finance = incubatorFinance(
                    incubators.sumOf { it.price },
                    counted,
                    // Без архивных, как на экране инкубатора, — см. batchElectricity.
                    batchElectricity(counted, incubators.associateBy { it.id }),
                ),
                // Инкубатор без цены входит в сумму нулём, и окупаемость хозяйства
                // считается от заниженной цены техники — то есть выглядит лучше, чем
                // есть. Это та же недосказанность, ради которой в IncubatorFinance
                // заведены batchesWithoutEggPrice и batchesWithoutChickPrice, только у
                // инкубаторов парного счётчика не было. Домен для этого трогать не
                // нужно: вопрос «сколько устройств не назвали цену» — про список
                // инкубаторов, а не про деньги закладок.
                incubatorsWithoutPrice = incubators.count { it.price <= 0 },
                // Завершённые, но не вошедшие в счёт: убранные в архив сами или лежащие в
                // инкубаторе из архива. Нужно пустой вкладке — сказать, где прошлые выводы.
                archivedFinished = allBatches.count {
                    it.status != BatchStatus.Active && (it.hidden || it.incubatorId !in workingIds)
                },
                loading = false,
            )
        }
            // Сборка состояния уходит с главного потока. `stateIn(viewModelScope)` собирает
            // на `Dispatchers.Main.immediate`, а здесь на каждый ответ базы — проходы по
            // всем закладкам хозяйства и арифметика `:domain` поверх них; на главном
            // потоке это кадры, отданные не отрисовке.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, MviSharing.WhileVisible, AnalyticsUiState())

    override fun onIntent(intent: AnalyticsIntent) {
        when (intent) {
            is AnalyticsIntent.SaveName -> saveName(intent.name)
        }
    }

    /**
     * Сохраняет имя владельца.
     *
     * Пробелы по краям срезаются: «Семён » и «Семён» — одно имя, а хранить их как разные
     * значило бы показывать в шапке лишний отступ и не находить одного через другое,
     * когда имя однажды понадобится для чего-то ещё.
     */
    private fun saveName(name: String) {
        viewModelScope.launch { itemsRepository.saveUser(User(name.trim())) }
    }
}

@Immutable
data class AnalyticsUiState(
    val user: User = User(),
    val incubatorCount: Int = 0,
    /** Сколько инкубаторов не назвали свою цену — знаменатель окупаемости неполон. */
    val incubatorsWithoutPrice: Int = 0,
    val activeBatches: Int = 0,
    val eggsInWork: Int = 0,
    /** Сводная статистика по всем инкубаторам — см. [incubatorStats]. */
    val stats: IncubatorStats = IncubatorStats(),
    /** Сводные финансы по всем инкубаторам — см. [incubatorFinance]. */
    val finance: IncubatorFinance = IncubatorFinance(),
    /** Завершённых закладок вне счёта — в архиве или в инкубаторе из архива. */
    val archivedFinished: Int = 0,
    /**
     * База ещё не ответила — не то же самое, что «хозяйство пустое».
     *
     * Ровно то же различие, что и на двух экранах ниже: до первого ответа все числа
     * нулевые, и сводка по хозяйству выглядит посчитанной, хотя считать ещё не из
     * чего.
     */
    val loading: Boolean = true,
)
