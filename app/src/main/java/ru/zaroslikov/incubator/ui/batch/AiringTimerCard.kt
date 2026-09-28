package ru.zaroslikov.incubator.ui.batch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.airing.DEFAULT_MINUTES
import ru.zaroslikov.incubator.airing.MAX_MINUTES
import ru.zaroslikov.incubator.airing.defaultMinutes
import ru.zaroslikov.incubator.airing.parseCustomMinutes
import ru.zaroslikov.incubator.airing.planChoice
import ru.zaroslikov.incubator.airing.progress
import ru.zaroslikov.incubator.airing.remainingMillis
import ru.zaroslikov.incubator.design.components.ChoiceChip
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.incubator.plural
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Что форма замера знает о таймере проветривания.
 *
 * [mine] — таймер (идущий или законченный) поставлен из *этой* формы: только ей он
 * отдаёт минуты и только она показывает его кнопки. Чужой таймер форма показывает одной
 * строкой и второго не предлагает — таймер в приложении один. [planMinutes] — минуты
 * одного проветривания из плана дня: чип, выбранный по умолчанию.
 */
@Immutable
data class AiringTimerSlot(
    val state: AiringTimerState,
    val mine: Boolean,
    val planMinutes: Int?,
)

/** Что можно сделать с таймером из формы; ViewModel переводит это в свои намерения. */
sealed interface AiringTimerAction {
    data class Start(val minutes: Int) : AiringTimerAction
    data object Cancel : AiringTimerAction
    data object Finish : AiringTimerAction
    /** «Готово» у своего таймера: мелодия смолкает, результат больше никого не ждёт. */
    data object Dismiss : AiringTimerAction

    /** «Выключить» у чужого: только мелодия — минуты ждут ту форму, откуда ставили. */
    data object Silence : AiringTimerAction
}

/**
 * Карточка таймера проветривания в форме замера.
 *
 * Три состояния, и между ними `AnimatedContent`, потому что они разной высоты: ряд чипов
 * с кнопкой запуска; кольцо с обратным отсчётом, которое замыкается к сроку, и кнопки
 * «Завершить» / «Отменить»; полное кольцо с пульсом и «Время вышло — закройте
 * инкубатор» с одной кнопкой «Готово». Отсчёт рисуется по кадрам ([withFrameMillis]) —
 * это единственное место приложения, где стрелка должна *ехать*, а не прыгать по
 * секундам: кольцо на 15 минут за секунду проходит четверть градуса, и прыжки были бы
 * видны как дрожь. Секунды в центре обновляются, когда меняется само число.
 */
@Composable
internal fun AiringTimerCard(
    slot: AiringTimerSlot,
    onAction: (AiringTimerAction) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(TimerRadius),
        color = DesignPalette.Surface,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Ключ перехода — фаза, а содержимое — весь слот: уходящая фаза дорисовывается
        // на *своём* состоянии, а не на новом, у которого уже другой тип.
        AnimatedContent(
            targetState = slot,
            contentKey = { it.state.phase(it.mine) },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "airing-timer-phase",
        ) { shown ->
            when (shown.state.phase(shown.mine)) {
                TimerPhase.Idle -> TimerIdle(shown.planMinutes, onStart = { onAction(AiringTimerAction.Start(it)) })
                TimerPhase.Running -> TimerRunning(
                    state = shown.state as AiringTimerState.Running,
                    onCancel = { onAction(AiringTimerAction.Cancel) },
                    onFinish = { onAction(AiringTimerAction.Finish) },
                )
                TimerPhase.Done -> TimerDone(
                    state = shown.state as AiringTimerState.Done,
                    onDismiss = { onAction(AiringTimerAction.Dismiss) },
                )
                TimerPhase.Foreign -> TimerForeign(shown.state, onSilence = { onAction(AiringTimerAction.Silence) })
            }
        }
    }
}

private enum class TimerPhase { Idle, Running, Done, Foreign }

private fun AiringTimerState.phase(mine: Boolean): TimerPhase = when {
    this is AiringTimerState.Idle -> TimerPhase.Idle
    !mine -> TimerPhase.Foreign
    this is AiringTimerState.Running -> TimerPhase.Running
    else -> TimerPhase.Done
}

// --- Покой: чипы и запуск -------------------------------------------------------------

/**
 * Покой: два чипа и кнопка запуска. Первый — минуты плана дня («4 мин · план»), то, ради
 * чего таймер ставят чаще всего; второй — «Своё время», под которым раскрывается поле
 * минут. Ряда «5 / 10 / 15 …» нет нарочно: план уже назвал число, а любое другое
 * набирается в поле быстрее, чем ищется среди чипов. Без плана в форме остаётся одно
 * поле, заранее заполненное [DEFAULT_MINUTES].
 */
@Composable
private fun TimerIdle(planMinutes: Int?, onStart: (Int) -> Unit) {
    val plan = remember(planMinutes) { planChoice(planMinutes) }
    // Выбор и набранное переживают поворот, но не открытие формы заново: план мог смениться.
    var custom by rememberSaveable(plan) { mutableStateOf(plan == null) }
    var customText by rememberSaveable(plan) { mutableStateOf(defaultMinutes(planMinutes).toString()) }
    val minutes = if (custom) parseCustomMinutes(customText) else plan

    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_airing_design),
                contentDescription = null,
                tint = DesignPalette.Accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "ТАЙМЕР ПРОВЕТРИВАНИЯ",
                style = DesignType.MonoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (plan != null) {
                    ChoiceChip(
                        text = "$plan мин · план",
                        selected = !custom,
                        onClick = { custom = false },
                    )
                }
                ChoiceChip(
                    text = "Своё время",
                    selected = custom,
                    onClick = { custom = true },
                )
            }
            Spacer(Modifier.width(8.dp))
            StartButton(enabled = minutes != null, onClick = { minutes?.let(onStart) })
        }
        // Поле — только под своим чипом: под чипом плана ему нечего сказать. Въезжает и
        // уезжает по высоте, а не возникает: карточка стоит в форме, и ряд под ней
        // должен съехать, а не прыгнуть.
        AnimatedVisibility(
            visible = custom,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                SheetTextField(
                    value = customText,
                    onValueChange = { customText = it.filterCountInput().take(2) },
                    placeholder = "10",
                    numeric = true,
                    minHeight = 40.dp,
                    radius = InnerRadius,
                    horizontalPadding = 12.dp,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "мин · от 1 до $MAX_MINUTES",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Круглая акцентная кнопка запуска — «▶» в кольце, каким оно потом и станет. */
@Composable
private fun StartButton(enabled: Boolean, onClick: () -> Unit) {
    // Гаснет, как «Записать замер»: заливка бледнеет, знак остаётся полного цвета.
    val fill by animateColorAsState(
        targetValue = if (enabled) DesignPalette.Accent else DesignPalette.Accent.copy(alpha = 0.16f),
        label = "airing-start-fill",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Запустить таймер проветривания" },
    ) {
        val play = if (enabled) DesignPalette.OnAccent else DesignPalette.Accent
        Canvas(Modifier.size(14.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawPath(path, play)
        }
    }
}

// --- Идёт: кольцо и отсчёт -------------------------------------------------------------

@Composable
private fun TimerRunning(
    state: AiringTimerState.Running,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    val now = frameClock(state.id)
    val remaining = state.remainingMillis(now)
    val progress = state.progress(now)

    // Кнопки — под кольцом во всю ширину, а не в колонке справа от него: на 360 dp
    // колонке остаётся около 190 dp, и второй кнопке в ней места нет.
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimerRing(progress = progress, pulse = 0f, modifier = Modifier.size(RingSize)) {
                Text(
                    text = countdownText(remaining),
                    style = DesignType.MeasureValue,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Проветривание идёт",
                    style = DesignType.CardHeading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // Строка не меняется, а композиция идёт по кадрам: формат — один раз на срок.
                    text = remember(state.endAt) { "${state.minutes} мин · закрыть в ${clockText(Date(state.endAt))}" },
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerButton(
                text = "Завершить",
                accent = true,
                onClick = onFinish,
                modifier = Modifier.weight(1f),
            )
            TimerButton(
                text = "Отменить",
                accent = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Обводная кнопка карточки: акцентная — то, ради чего таймер ставили, серая — отказ. */
@Composable
private fun TimerButton(
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (accent) DesignPalette.Accent else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(InnerRadius),
        border = BorderStroke(0.8.dp, if (accent) DesignPalette.Accent else DesignPalette.CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DesignPalette.Surface,
            contentColor = tint,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.height(38.dp),
    ) {
        Text(text, style = DesignType.ButtonLabel, maxLines = 1)
    }
}

// --- Время вышло ------------------------------------------------------------------------

@Composable
private fun TimerDone(state: AiringTimerState.Done, onDismiss: () -> Unit) {
    // Пульс — пока звенит: кольцо дышит, чтобы глаз нашёл его раньше, чем ухо — мелодию.
    // Бесконечная анимация заводится только тогда: иначе она просила бы кадры и у
    // карточки, которой дышать нечем.
    val pulse = if (state.ringing) {
        val transition = rememberInfiniteTransition(label = "airing-done-pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "airing-done-pulse-value",
        )
        value
    } else {
        0f
    }
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimerRing(
                progress = 1f,
                pulse = pulse,
                modifier = Modifier.size(RingSize),
                alarm = true,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_airing_design),
                    contentDescription = null,
                    tint = DesignPalette.Expense,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Время вышло!",
                    style = DesignType.CardHeading,
                    color = DesignPalette.Expense,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Закройте инкубатор. " +
                        "${plural(state.minutes, "минута подставлена", "минуты подставлены", "минут подставлено")} " +
                        "в форму — запишите замер.",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(InnerRadius),
            colors = accentButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(38.dp),
        ) {
            Text(
                text = if (state.ringing) "Готово, выключить сигнал" else "Готово",
                style = DesignType.ButtonLabel,
                maxLines = 1,
            )
        }
    }
}

// --- Чужой таймер ----------------------------------------------------------------------

/**
 * Таймер поставлен из другой формы: показать, что он есть, — второй рядом с ним не
 * встанет, — и дать выключить мелодию, если она играет. Минуты уйдут туда, откуда его
 * ставили; здесь их не предлагают.
 */
@Composable
private fun TimerForeign(state: AiringTimerState, onSilence: () -> Unit) {
    val running = state as? AiringTimerState.Running
    // Часы по кадрам — только пока чужой таймер идёт: законченному считать нечего.
    val now = if (running != null) frameClock(running.id) else 0L
    val where = state.labelOrEmpty.ifBlank { "другой закладке" }
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_airing_design),
            contentDescription = null,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (running != null) {
                "Таймер проветривания идёт в «$where» · ${countdownText(running.remainingMillis(now))}"
            } else {
                "Проветривание в «$where» закончилось"
            },
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state is AiringTimerState.Done && state.ringing) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onSilence,
                shape = RoundedCornerShape(InnerRadius),
                border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Выключить", style = DesignType.ButtonLabel, maxLines = 1)
            }
        }
    }
}

// --- Кольцо ---------------------------------------------------------------------------

/**
 * Кольцо отсчёта: дорожка, дуга пройденного и точка на её конце. [pulse] 0..1 — дыхание
 * в состоянии «время вышло»: ореол за кольцом расходится и гаснет. Размеры — параметры,
 * потому что то же кольцо, вчетверо меньше, стоит в плавающей кнопке (`AiringTimerFab`):
 * одна геометрия, чтобы кнопка и карточка читались как одна вещь.
 */
@Composable
internal fun TimerRing(
    progress: Float,
    pulse: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = RingSize,
    strokeWidth: Dp = RingStroke,
    halo: Dp = HaloWidth,
    // Зелёный, пока идёт; красный — когда время вышло (см. [TimerDone]).
    alarm: Boolean = false,
    content: @Composable () -> Unit,
) {
    val track = DesignPalette.ProgressTrack
    val accent = if (alarm) DesignPalette.Expense else DesignPalette.Accent
    // Сердцевина точки — то, что стоит на цвете дуги: белый на красном, `OnAccent` на зелёном.
    val core = if (alarm) Color.White else DesignPalette.OnAccent
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(
            Modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = 1f + 0.04f * pulse
                    scaleY = 1f + 0.04f * pulse
                },
        ) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2 + halo.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (pulse > 0f) {
                drawCircle(
                    color = accent.copy(alpha = 0.18f * (1f - pulse)),
                    radius = size.minDimension / 2 - halo.toPx() * (1f - pulse),
                )
            }
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke),
            )
            val sweep = 360f * progress
            if (sweep > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                // Точка на конце дуги — то, что видно двигаться.
                val angle = Math.toRadians((sweep - 90f).toDouble())
                val r = arcSize.width / 2
                val cx = size.width / 2 + r * cos(angle).toFloat()
                val cy = size.height / 2 + r * sin(angle).toFloat()
                drawCircle(color = accent, radius = stroke * 0.9f, center = Offset(cx, cy))
                drawCircle(color = core, radius = stroke * 0.35f, center = Offset(cx, cy))
            }
        }
        content()
    }
}

/**
 * Часы, идущие по кадрам, пока композиция жива. Ключ — идентификатор таймера: новый
 * таймер начинает счёт заново, а не с кадра прежнего.
 */
@Composable
internal fun frameClock(key: Long): Long {
    var now by remember(key) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    return now
}

internal fun countdownText(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private val TimerRadius = 16.dp
private val InnerRadius = 12.dp
private val RingSize = 96.dp
private val RingStroke = 7.dp
private val HaloWidth = 6.dp
