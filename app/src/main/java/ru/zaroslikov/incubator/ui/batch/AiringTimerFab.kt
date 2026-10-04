package ru.zaroslikov.incubator.ui.batch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.airing.AiringTimerTarget
import ru.zaroslikov.incubator.airing.progress
import ru.zaroslikov.incubator.airing.remainingMillis
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.AppViewModelProvider

/**
 * Плавающая кнопка идущего таймера — поверх всего приложения, в **левом** нижнем углу.
 *
 * Слева, потому что правый угол и низ по центру у экранов заняты: «+ Инкубатор» на
 * стартовом, мини-«+» и «Внести замер» на экране инкубатора; в левом углу не стоит
 * ничего, и кольцо там никого не заслоняет.
 *
 * Таймер ставят в форме замера и уходят по своим делам, а крышка инкубатора открыта.
 * Кнопка показывает, сколько осталось, тем же кольцом, что и карточка в форме, и по
 * нажатию ведёт обратно в ту форму ([onOpen] — тот же переход, что у уведомления). На
 * самой этой форме её нет ([AiringTimerFabState.hidden]): там стоит карточка.
 *
 * Отсчёт идёт по кадрам ([frameClock]): цифры меняются раз в секунду, дуга едет
 * непрерывно. «Время вышло» — полное кольцо с пульсом, пока играет мелодия.
 */
@Composable
internal fun AiringTimerFab(
    onOpen: (AiringTimerTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiringTimerFabViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val fabState by viewModel.state.collectAsStateWithLifecycle()
    val state = fabState.timer
    // Смолкший результат ждёт записи замера в карточке формы; кольцо — только о том, что
    // происходит сейчас: идёт отсчёт или звенит мелодия.
    val active = state is AiringTimerState.Running ||
        (state is AiringTimerState.Done && state.ringing)
    val visible = !fabState.hidden && active
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        // Содержимое анимации дорисовывается на уходящем состоянии, поэтому `Idle`
        // здесь возможен один кадр — и рисуется пустым кругом, а не падает.
        val target = state.targetOrNull
        Surface(
            shape = CircleShape,
            color = DesignPalette.Surface,
            border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                // Те же 20 dp от краёв и та же высота 56 dp, что у «+ Инкубатор» и у
                // «Внести замер»: кольцо стоит с ними в одну линию, как их левая пара.
                .navigationBarsPadding()
                .padding(start = FabInset, bottom = FabInset)
                .size(FabSize)
                .clip(CircleShape)
                .clickable(enabled = target != null) { target?.let(onOpen) }
                .semantics { contentDescription = "Таймер проветривания — открыть форму замера" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (state) {
                    is AiringTimerState.Running -> FabRunning(state)
                    is AiringTimerState.Done -> FabDone(state)
                    AiringTimerState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun FabRunning(state: AiringTimerState.Running) {
    val now = frameClock(state.id)
    TimerRing(
        progress = state.progress(now),
        pulse = 0f,
        diameter = FabSize,
        strokeWidth = FabStroke,
        halo = FabHalo,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = countdownText(state.remainingMillis(now)),
                style = DesignType.MonoSmallEmphasis,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FabDone(state: AiringTimerState.Done) {
    val pulse = if (state.ringing) {
        val transition = rememberInfiniteTransition(label = "airing-fab-pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "airing-fab-pulse-value",
        )
        value
    } else {
        0f
    }
    TimerRing(
        progress = 1f,
        pulse = pulse,
        alarm = true,
        diameter = FabSize,
        strokeWidth = FabStroke,
        halo = FabHalo,
    ) {
        Text(
            text = "0:00",
            style = DesignType.MonoSmallEmphasis,
            color = DesignPalette.Expense,
            maxLines = 1,
        )
    }
}

/**
 * Отмечает форму замера открытой на время её композиции — то, по чему [AiringTimerFab]
 * прячется. Стоит в теле обеих шторок с карточкой таймера: закладки и «Замеров за
 * сегодня» инкубатора.
 */
@Composable
internal fun RegisterTimerForm(
    target: AiringTimerTarget,
    viewModel: AiringTimerFabViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    DisposableEffect(target, viewModel) {
        viewModel.onIntent(AiringTimerFabIntent.FormShown(target))
        onDispose { viewModel.onIntent(AiringTimerFabIntent.FormHidden(target)) }
    }
}

/**
 * Идёт ли таймер сейчас — то, по чему экран инкубатора уводит свою кнопку «Внести
 * замер» из середины к правому краю: кольцо стоит слева в той же линии, а посередине
 * широкая кнопка ложилась бы на него на узком экране.
 */
@Composable
internal fun airingTimerActive(
    viewModel: AiringTimerFabViewModel = viewModel(factory = AppViewModelProvider.Factory),
): Boolean {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state.active
}

/** Отступ от краёв — тот же, что у плавающих кнопок обоих экранов (`ScreenPadding`). */
private val FabInset = 20.dp
private val FabSize = 56.dp
private val FabStroke = 4.dp
private val FabHalo = 3.dp
