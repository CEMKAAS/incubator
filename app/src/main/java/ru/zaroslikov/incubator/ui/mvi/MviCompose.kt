package ru.zaroslikov.incubator.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Подписывает композицию на эффекты ViewModel.
 *
 * Собирает их, пока экран хотя бы `STARTED`: эффект — это «закрой шторку», «открой
 * системное окно», и выполнять такое над экраном, ушедшим в фон, нельзя — навигация из
 * фона роняет приложение. Канал в [MviViewModel] буферизует эффект до возвращения, так
 * что он не теряется, а ждёт.
 *
 * [onEffect] читается через [rememberUpdatedState]: лямбда на месте вызова
 * пересоздаётся на каждую рекомпозицию, а перезапускать подписку из-за этого незачем.
 */
@Composable
fun <E : Any> CollectEffects(viewModel: MviViewModel<*, *, E>, onEffect: suspend (E) -> Unit) {
    val handler by rememberUpdatedState(onEffect)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { handler(it) }
        }
    }
}
