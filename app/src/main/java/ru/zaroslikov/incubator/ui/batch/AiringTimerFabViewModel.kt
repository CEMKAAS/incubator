package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.zaroslikov.incubator.airing.AiringTimerController
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.airing.AiringTimerTarget
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel

@Immutable
data class AiringTimerFabState(
    val timer: AiringTimerState = AiringTimerState.Idle,
    /** Формы замера, открытые сейчас, — см. `AiringTimerController.openForms`. */
    val openForms: Set<AiringTimerTarget> = emptySet(),
) {
    /** Кольцо прячется на той форме, где таймер поставлен: там стоит его карточка. */
    val hidden: Boolean get() = timer.targetOrNull?.let { it in openForms } ?: true

    /** Таймер идёт или ждёт записи — экран инкубатора уводит свою кнопку к краю. */
    val active: Boolean get() = timer !is AiringTimerState.Idle
}

sealed interface AiringTimerFabIntent {
    /** Шторка с карточкой таймера появилась на экране. */
    data class FormShown(val target: AiringTimerTarget) : AiringTimerFabIntent

    /** И ушла с него. */
    data class FormHidden(val target: AiringTimerTarget) : AiringTimerFabIntent
}

/** Одноразовых событий нет: переход по нажатию на кольцо — колбэк хозяина, `MainActivity`. */
sealed interface AiringTimerFabEffect

/**
 * Плавающее кольцо таймера и отметки открытых форм замера.
 *
 * Состояние — оба потока контроллера из `AppContainer`; начальное значение — их текущие
 * значения, чтобы кольцо не мигало при повороте. ViewModel берётся там, где её просят:
 * у кольца в `MainActivity` — активностью, у шторок и экрана инкубатора — записью
 * навигации; контроллер один на приложение, так что экземпляры ничего не делят.
 */
class AiringTimerFabViewModel(
    private val controller: AiringTimerController,
) : MviViewModel<AiringTimerFabState, AiringTimerFabIntent, AiringTimerFabEffect>() {

    override val state: StateFlow<AiringTimerFabState> =
        combine(controller.state, controller.openForms) { timer, forms -> AiringTimerFabState(timer, forms) }
            .stateIn(
                viewModelScope,
                MviSharing.WhileVisible,
                AiringTimerFabState(controller.state.value, controller.openForms.value),
            )

    override fun onIntent(intent: AiringTimerFabIntent) {
        when (intent) {
            is AiringTimerFabIntent.FormShown -> controller.formShown(intent.target)
            is AiringTimerFabIntent.FormHidden -> controller.formHidden(intent.target)
        }
    }
}
