package ru.zaroslikov.incubator.ui.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Контракт MVI, по которому устроена каждая ViewModel приложения.
 *
 * Три типа на экран, и ровно три:
 *
 * - [S] — **состояние**: одна иммутабельная `data class`, всё, что экран рисует.
 *   Экран читает его через `state.collectAsStateWithLifecycle()` и ничего больше у
 *   ViewModel не спрашивает — ни отдельных `StateFlow` на каждое поле, ни свойств с
 *   `get()`, которые пересчитывались бы на каждый кадр.
 * - [I] — **намерения**: `sealed interface`, по варианту на каждое действие человека
 *   или события экрана («открыли шторку», «ввели символ», «нажали сохранить»).
 *   Единственная точка входа — [onIntent]; публичных методов-мутаторов у ViewModel нет.
 * - [E] — **эффекты**: `sealed interface` одноразовых событий — «сохранено, закрой
 *   шторку», «открой окно выбора файла». Они не часть состояния, потому что состояние
 *   можно прочитать дважды (поворот экрана), а эффект должен сработать один раз.
 *
 * **Почему `state` абстрактный, а не готовый `MutableStateFlow`.** У экранов два разных
 * источника правды. Форма держит ввод у себя — ей нужен локальный поток и
 * [MutableStateFlow.update]. Экран-список ничего не хранит: его состояние — функция от
 * базы, `combine(потоки репозитория).stateIn(WhileSubscribed(5 s))`, и это правило
 * записано в корневом `CLAUDE.md` — Room не должен пересчитывать хозяйство ради
 * экрана, на который никто не смотрит. Общий `MutableStateFlow`, наполняемый
 * коллектором в `init`, это правило молча отменил бы: коллектор жил бы, пока жива
 * ViewModel, то есть пока экран в стеке. Поэтому база задаёт только форму контракта,
 * а откуда состояние берётся, решает наследник: [StatefulMviViewModel] — для форм,
 * прямой `stateIn` — для списков.
 *
 * **Эффекты — через [Channel], а не `SharedFlow`.** У `SharedFlow` без буфера эффект,
 * отправленный, пока экран не подписан, пропадает; у шторки такой промежуток есть —
 * между «сохранено» и следующей композицией. Канал с буфером держит событие до первого
 * подписчика. Подписчик один — та композиция, что открыла шторку (`CollectEffects`).
 */
abstract class MviViewModel<S : Any, I : Any, E : Any> : ViewModel() {

    /** Текущее состояние экрана; единственное, что экран читает. */
    abstract val state: StateFlow<S>

    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /** Одноразовые события для экрана; собираются через `CollectEffects`. */
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Единственный вход: всё, что экран хочет от ViewModel, приходит намерением. */
    abstract fun onIntent(intent: I)

    /**
     * Отправляет эффект экрану. `trySend`, а не `send`: буфер канала не переполняется
     * на практике (эффектов единицы за жизнь шторки), а suspend-версия потребовала бы
     * корутину там, где эффект рождается синхронно из редьюсера.
     */
    protected fun sendEffect(effect: E) {
        effectChannel.trySend(effect)
    }
}

/**
 * ViewModel с локальным состоянием — формы и шторки, чей ввод живёт здесь, а не в базе.
 *
 * [reduce] — единственный способ изменить состояние: новая копия из старой, атомарно
 * (`MutableStateFlow.update` повторяет преобразование при гонке, а не теряет одно из
 * двух). Производные величины, которые дорого считать, — итог по замерам, известные
 * породы, — держатся полями состояния и пересчитываются только в тех ветках
 * редьюсера, где меняются их входы; `derivedStateOf` ими больше не занимается, и
 * ветка «ввели символ в форму» их не трогает.
 */
abstract class StatefulMviViewModel<S : Any, I : Any, E : Any>(
    initial: S,
) : MviViewModel<S, I, E>() {

    private val mutableState = MutableStateFlow(initial)

    final override val state: StateFlow<S> = mutableState.asStateFlow()

    /** Снимок состояния на этот момент — для редьюсеров, читающих несколько полей. */
    protected val current: S get() = mutableState.value

    protected inline fun reduce(crossinline transform: S.() -> S) {
        updateState { it.transform() }
    }

    @PublishedApi
    internal fun updateState(transform: (S) -> S) {
        mutableState.update(transform)
    }
}

/**
 * Режим `stateIn` для состояний, собранных из потоков базы, — один на всё приложение.
 *
 * Подписка на базу гаснет через пять секунд после ухода с экрана — правило из
 * корневого `CLAUDE.md`, иначе Room пересчитывал бы хозяйство ради экрана, на который
 * никто не смотрит. Последнее состояние при этом остаётся в кэше: у `WhileSubscribed`
 * это поведение по умолчанию (`replayExpirationMillis = Long.MAX_VALUE`), и здесь оно
 * названо явно, потому что на нём держится возврат из фона — экран показывает то, что
 * на нём было, а не начальное значение, пока Room отвечает заново. Одна константа
 * вместо `TIMEOUT_MILLIS` в каждой ViewModel — чтобы тайм-аут нельзя было поменять в
 * одном экране и забыть в других.
 */
object MviSharing {
    val WhileVisible: SharingStarted = SharingStarted.WhileSubscribed(
        stopTimeoutMillis = 5_000,
        replayExpirationMillis = Long.MAX_VALUE,
    )
}
