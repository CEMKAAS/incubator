package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel

/**
 * Состояние формы инкубатора — всё, что рисует [AddIncubatorSheet].
 *
 * Поля формы лежат в [form] отдельным классом, потому что у него есть переводы в
 * [Incubator] и обратно ([toIncubator] / [toUiState]), и держать их на классе, где
 * рядом лежат подсказки брендов, значило бы переводить подсказки в модель.
 */
@Immutable
data class AddIncubatorState(
    val form: IncubatorFormUiState = IncubatorFormUiState(),
    /** Правка существующего; при создании — `false`. */
    val isEditing: Boolean = false,
    /**
     * Форма правки ещё читается из базы.
     *
     * Только при правке: у новой формы читать нечего, и колесо над пустыми полями
     * означало бы, что там что-то будет. А вот пустые поля над существующим
     * инкубатором — это утверждение, что у него нет ни названия, ни модели, ни цены.
     */
    val loading: Boolean = false,
    /**
     * Подсказка в поле названия: «Инкубатор 3» у того, у кого уже два.
     *
     * `null` — пока база не ответила, и тогда поле подсказывает просто «Инкубатор»:
     * между «номер ещё не известен» и «номер 1» разница в том, что второе — это
     * утверждение, и у владельца трёх устройств оно неверное.
     *
     * При правке номера нет вовсе. Устройство уже посчитано, так что следующий номер
     * назвал бы не его, а ещё не существующее — а очистив название у второго из трёх
     * инкубаторов, подсказку про четвёртый читать незачем.
     */
    val nextNumber: Int? = null,
    /** Ранее введённые бренды и модели — выпадают подсказками в соответствующих полях. */
    val usedBrands: List<String> = emptyList(),
    val usedModels: List<String> = emptyList(),
) {
    /**
     * Обязательное поле только одно — название.
     *
     * Так в макете: звёздочка стоит лишь у названия, а подпись прямо говорит
     * «остальные параметры необязательны». Вместимость поэтому может остаться пустой,
     * и карточка на стартовом экране попросит её заполнить.
     */
    val isValid: Boolean get() = form.name.isNotBlank()
}

sealed interface AddIncubatorIntent {
    /** Вызывается при каждом открытии шторки — состояние прошлого показа не переживает. */
    data class Load(val incubatorId: Long) : AddIncubatorIntent

    /** Правка любого поля формы: шторка отдаёт копию [IncubatorFormUiState] целиком. */
    data class Update(val form: IncubatorFormUiState) : AddIncubatorIntent

    data object Save : AddIncubatorIntent
}

sealed interface AddIncubatorEffect {
    /** Инкубатор записан; [id] — его идентификатор, новый или прежний. */
    data class Saved(val id: Long) : AddIncubatorEffect
}

/**
 * Создание и правка инкубатора — одна форма, живущая в нижней шторке.
 *
 * Шторка не является местом назначения в навигации, поэтому id приходит через
 * [AddIncubatorIntent.Load], а не через SavedStateHandle: она открывается поверх того
 * экрана, что уже на виду.
 *
 * Правка нужна не только для удобства: у инкубатора, синтезированного при миграции с
 * первой версии базы, вместимость нулевая, и заполнить её больше негде.
 *
 * Состояние собирается из двух источников: локального — ввод формы, флаги — и трёх
 * потоков базы (счётчик устройств, бренды, модели). `combine` со `stateIn`, а не
 * коллекторы в `init`: подписка на базу живёт, пока на форму смотрят, и гаснет через
 * пять секунд после ухода — тот же договор, что у экранов-списков.
 */
class AddIncubatorViewModel(
    private val itemsRepository: ItemsRepository,
) : MviViewModel<AddIncubatorState, AddIncubatorIntent, AddIncubatorEffect>() {

    /** Локальная половина состояния: ввод и флаги формы. */
    private data class Local(
        val form: IncubatorFormUiState = IncubatorFormUiState(),
        /** Ноль означает «создаём новый». */
        val incubatorId: Long = 0,
        val loading: Boolean = false,
    ) {
        val isEditing: Boolean get() = incubatorId != 0L
    }

    private val local = MutableStateFlow(Local())

    /** Подсказки из базы: номер для названия, бренды и модели. */
    private data class Hints(
        val nextNumber: Int? = null,
        val usedBrands: List<String> = emptyList(),
        val usedModels: List<String> = emptyList(),
    )

    /**
     * Подсказки собраны в свой `StateFlow`, а не в общий `combine` с формой, и это
     * не тонкость. `combine` отдаёт первое значение, когда ответили *все* источники, а
     * три из них — Room. Форма, ждущая базу, показывала бы на первом кадре правки
     * начальное состояние — «Новый инкубатор» и пустые поля над существующим
     * устройством, — а при повторном открытии через пять секунд, когда подписка на
     * базу перезапускается, — форму *прошлого* инкубатора до нового ответа. У
     * `StateFlow` значение есть всегда, так что форма ниже рисуется по своей половине
     * сразу, а подсказки подъезжают, когда подъедут.
     */
    private val hints: StateFlow<Hints> =
        combine(
            itemsRepository.countIncubators().map { it + 1 },
            itemsRepository.getUsedBrands(),
            itemsRepository.getUsedModels(),
        ) { nextNumber, brands, models -> Hints(nextNumber, brands, models) }
            .stateIn(viewModelScope, MviSharing.WhileVisible, Hints())

    override val state: StateFlow<AddIncubatorState> =
        combine(local, hints) { local, hints ->
            AddIncubatorState(
                form = local.form,
                isEditing = local.isEditing,
                loading = local.loading,
                nextNumber = hints.nextNumber,
                usedBrands = hints.usedBrands,
                usedModels = hints.usedModels,
            )
        }.stateIn(viewModelScope, MviSharing.WhileVisible, AddIncubatorState())

    override fun onIntent(intent: AddIncubatorIntent) {
        when (intent) {
            is AddIncubatorIntent.Load -> load(intent.incubatorId)
            is AddIncubatorIntent.Update -> local.update { it.copy(form = intent.form) }
            AddIncubatorIntent.Save -> save()
        }
    }

    private fun load(id: Long) {
        local.value = Local(incubatorId = id, loading = id != 0L)
        if (id == 0L) return
        viewModelScope.launch {
            val form = itemsRepository.getIncubator(id).filterNotNull().first().toUiState()
            // Ответ базы кладётся только под тот же идентификатор: шторку могли успеть
            // открыть заново на другом устройстве, и тогда ответ уже не о нём.
            local.update { if (it.incubatorId == id) it.copy(form = form, loading = false) else it }
        }
    }

    private fun save() {
        val snapshot = local.value
        if (snapshot.form.name.isBlank()) return
        viewModelScope.launch {
            val id = if (snapshot.isEditing) {
                itemsRepository.updateIncubator(snapshot.form.toIncubator(snapshot.incubatorId))
                snapshot.incubatorId
            } else {
                itemsRepository.insertIncubator(snapshot.form.toIncubator())
            }
            sendEffect(AddIncubatorEffect.Saved(id))
        }
    }
}

data class IncubatorFormUiState(
    val name: String = "",
    val capacity: String = "",
    val brand: String = "",
    val model: String = "",
    val price: String = "",
    val note: String = "",
    val autoTurn: Boolean = false,
    val autoAiring: Boolean = false,
    /**
     * «Убран в архив» — форма его не показывает и не меняет, но носит с собой.
     *
     * Ровно та же причина, по которой [ru.zaroslikov.incubator.ui.batch.BatchUiState]
     * тащит через себя причину завершения и цену птенцов: правка идёт через
     * [toIncubator], и состояние, потерявшее этот флаг, вернуло бы инкубатор из архива
     * при первом же сохранении формы — переименовали устройство, а оно всплыло в списке.
     */
    val hidden: Boolean = false,
)

fun IncubatorFormUiState.toIncubator(id: Long = 0): Incubator = Incubator(
    id = id,
    name = name.trim(),
    capacity = capacity.toIntOrNull() ?: 0,
    brand = brand.trim(),
    model = model.trim(),
    price = price.toIntOrNull() ?: 0,
    note = note.trim(),
    autoTurn = autoTurn,
    autoAiring = autoAiring,
    hidden = hidden,
)

fun Incubator.toUiState(): IncubatorFormUiState = IncubatorFormUiState(
    name = name,
    // Ноль показываем пустым полем, а не нулём: это «не указано».
    capacity = if (capacity > 0) capacity.toString() else "",
    brand = brand,
    model = model,
    price = if (price > 0) price.toString() else "",
    note = note,
    autoTurn = autoTurn,
    autoAiring = autoAiring,
    hidden = hidden,
)
