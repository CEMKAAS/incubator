package ru.zaroslikov.incubator.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import kotlin.math.PI
import kotlin.math.sin

/** Один цикл покачивания яиц вокруг круга — неторопливо, чтобы не отвлекать от текста. */
private const val FLOAT_PERIOD_MILLIS = 4200

/** Насколько яйцо поднимается и опускается за цикл. */
private val FloatAmplitude = 5.dp

/**
 * Пустое состояние с картинкой: «здесь пока ничего нет — и вот что сюда положить».
 *
 * Для пустоты, которая и есть первый шаг: ни одного инкубатора, ни одной закладки в
 * инкубаторе. Строка `DesignType.Placeholder` на таком месте читалась как сноска под
 * несуществующим списком, а это первый экран, который видит человек, только что
 * поставивший приложение, — он должен объяснять, а не констатировать. Для пустоты-
 * результата («прерванных нет», «в архиве пусто») картинка не нужна: там ответ —
 * одна строка, и он остаётся строкой.
 *
 * Картинка собрана из того же, что и приложение, — круг в цвет карточки с рамкой
 * `CardBorder`, мягкий ореол акцента и три яйца в цветах чипов видов, — поэтому следует
 * теме сама. Яйца медленно покачиваются; значение анимации читается только в
 * `graphicsLayer`, так что кадры не перестраивают текст под ней.
 *
 * [actionText] / [onAction] — кнопка под текстом; без них кнопки нет (например, когда
 * действие уже стоит прямо под заглушкой пунктирной кнопкой).
 *
 * [footer] — последняя строка под кнопкой (у приветствия — ссылки на соцсети). Слотом, а
 * не строкой: ссылки собирает экран, у которого есть адреса и аналитика, а `:design` о них
 * не знает.
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionIcon: Painter? = null,
    onAction: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration(emoji = emoji)
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = DesignType.CardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 8.dp),
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                colors = accentButtonColors(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (actionIcon != null) {
                    Icon(painter = actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(text = actionText, style = DesignType.ButtonLabel)
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(20.dp))
            footer()
        }
    }
}

/** Круг с эмодзи в середине, ореол вокруг и три покачивающихся яйца по краям. */
@Composable
private fun EmptyIllustration(emoji: String) {
    val transition = rememberInfiniteTransition(label = "empty-float")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(FLOAT_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "empty-float-phase",
    )

    Box(modifier = Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        // Ореол: два мягких круга акцента, внешний бледнее — даёт картинке глубину,
        // не споря с карточками экрана.
        Box(
            Modifier
                .size(168.dp)
                .background(DesignPalette.Accent.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            Modifier
                .size(136.dp)
                .background(DesignPalette.Accent.copy(alpha = 0.10f), CircleShape)
        )
        Surface(
            shape = CircleShape,
            color = DesignPalette.Surface,
            border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
            shadowElevation = 2.dp,
            modifier = Modifier.size(104.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 48.sp)
            }
        }

        FloatingEgg(
            color = DesignPalette.ChipChicken,
            width = 22.dp,
            phase = phase,
            shift = 0f,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = 22.dp),
        )
        FloatingEgg(
            color = DesignPalette.ChipDuck,
            width = 16.dp,
            phase = phase,
            shift = 0.5f,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 8.dp),
        )
        FloatingEgg(
            color = DesignPalette.ChipGoose,
            width = 19.dp,
            phase = phase,
            shift = 0.25f,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-18).dp),
        )
    }
}

/**
 * Маленькое яйцо-овал. [shift] сдвигает его фазу, чтобы три яйца качались вразнобой, а
 * не строем.
 */
@Composable
private fun FloatingEgg(
    color: Color,
    width: Dp,
    phase: State<Float>,
    shift: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                // Синус по кругу фазы: непрерывен на стыке циклов при любом сдвиге.
                val wave = sin((phase.value + shift) * 2f * PI.toFloat())
                translationY = wave * FloatAmplitude.toPx()
                rotationZ = wave * 6f
            }
            .size(width = width, height = width * 1.3f)
            .background(color, EggShape)
            .border(0.8.dp, DesignPalette.CardBorder, EggShape)
    )
}

/** Овал, чуть заострённый кверху, — яйцо, а не капля. */
private val EggShape = RoundedCornerShape(
    topStartPercent = 50,
    topEndPercent = 50,
    bottomStartPercent = 45,
    bottomEndPercent = 45,
)
