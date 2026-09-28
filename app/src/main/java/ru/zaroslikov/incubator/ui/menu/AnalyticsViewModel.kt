package ru.zaroslikov.incubator.ui.menu

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.stats.IncubatorFinance
import ru.zaroslikov.incubator.domain.stats.IncubatorStats
import ru.zaroslikov.incubator.domain.stats.incubatorFinance
import ru.zaroslikov.incubator.domain.stats.incubatorStats
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
 * «Аналитика»: всё хозяйство одним счётом, плюс имя владельца.
 *
 * Считает ровно теми же функциями `:domain`, что и экран одного инкубатора, — им
 * безразлично, из одного устройства список закладок или из пяти. Это не экономия строк,
 * а условие того, чтобы сводные числа сходились с частными: вторая реализация «того же
 * счёта, но по всем» разошлась бы с первой при первой же правке формулы, и хозяйство
 * увидело бы два разных ответа на один вопрос.
 *
 * Цена техники в [incubatorFinance] — сумма цен всех инкубаторов. Подстановка честная:
 * внутри она входит слагаемым в расход и знаменателем в окупаемость, и оба смысла у
 * суммы те же. Меняются только подписи, за которые отвечает
 * [ru.zaroslikov.incubator.ui.incubator.TabScope].
 *
 * Состояние — чистая функция от базы, поэтому `combine` со `stateIn` прямо в [state]:
 * своего ввода у экрана нет, и держать локальный поток было бы нечем наполнять.
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
        ) { user, incubators, batches, candlings ->
            // Через status, а не сравнением arhive руками: корневой CLAUDE.md прямо
            // предписывает новому UI-коду спрашивать трёхзначный статус закладки.
            val active = batches.filter { it.status == BatchStatus.Active }
            AnalyticsUiState(
                user = user,
                incubatorCount = incubators.size,
                activeBatches = active.size,
                eggsInWork = active.sumOf { it.eggAll },
                // Имена инкубаторов — только здесь: в истории хозяйства строка обязана
                // сказать, в каком устройстве это было, а на экране одного инкубатора
                // тот же вызов идёт без карты и строка остаётся прежней.
                stats = incubatorStats(
                    batches = batches,
                    candlings = candlings,
                    incubatorNames = incubators.associate { it.id to it.name },
                ),
                finance = incubatorFinance(incubators.sumOf { it.price }, batches),
                // Инкубатор без цены входит в сумму нулём, и окупаемость хозяйства
                // считается от заниженной цены техники — то есть выглядит лучше, чем
                // есть. Это та же недосказанность, ради которой в IncubatorFinance
                // заведены batchesWithoutEggPrice и batchesWithoutChickPrice, только у
                // инкубаторов парного счётчика не было. Домен для этого трогать не
                // нужно: вопрос «сколько устройств не назвали цену» — про список
                // инкубаторов, а не про деньги закладок.
                incubatorsWithoutPrice = incubators.count { it.price <= 0 },
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
    /**
     * База ещё не ответила — не то же самое, что «хозяйство пустое».
     *
     * Ровно то же различие, что и на двух экранах ниже: до первого ответа все числа
     * нулевые, и сводка по хозяйству выглядит посчитанной, хотя считать ещё не из
     * чего.
     */
    val loading: Boolean = true,
)
