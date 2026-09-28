package ru.zaroslikov.incubator.ads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import kotlin.math.PI
import kotlin.math.sin

/** Сколько длится покачивание яйца в одну сторону. */
private const val WobbleMillis = 900

/** Полный круг бегущей точки: три точки по [DotsCycleMillis] / 3 каждая. */
private const val DotsCycleMillis = 1350

private const val DotCount = 3

/**
 * Заставка «сейчас будет реклама», пока объявление грузится.
 *
 * Полноэкранное объявление приезжает не мгновенно: SDK сперва поднимается, потом идёт в
 * сеть, и на это уходят секунды (на эмуляторе — почти шесть). Без заставки эти секунды
 * человек проводит в приложении, а потом ему на руки падает реклама — и выглядит она
 * как сбой, потому что ничто её не предвещало. Заставка превращает то же ожидание в
 * объявленное: сначала говорим, что сейчас будет и зачем, потом показываем.
 *
 * Поэтому же она **держит экран**: касания до приложения не доходят, и человек не
 * начинает работать за секунду до того, как поверх ляжет реклама. Это заодно сняло
 * нужду в отдельной проверке «а не притронулись ли уже к экрану» — ожидание теперь
 * ограничено самой заставкой, и рекламе после неё показываться нечего (см.
 * [AppOpenAdController]).
 *
 * Тон выбран мягкий намеренно. Реклама — плата за приложение, и честнее сказать об этом
 * прямо и по-доброму, чем молча отнять у человека несколько секунд. Отсюда и яйцо: оно
 * покачивается, как перед проклёвом, — ожидание в этом приложении вообще главное занятие,
 * и шутка тут своя, не чужая.
 *
 * Всё живое читается внутри `graphicsLayer`, а не в композиции: значения меняются каждый
 * кадр, и чтение снаружи перерисовывало бы весь экран целиком.
 */
@Composable
fun AdLoadingNotice(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        NoticeBody()
    }
}

@Composable
private fun NoticeBody() {
    val transition = rememberInfiniteTransition(label = "adNotice")
    val wobble by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(WobbleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )
    val dots by transition.animateFloat(
        initialValue = 0f,
        targetValue = DotCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(DotsCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Заставка держит экран: пока она видна, приложение под ней не трогают.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(DesignPalette.IncomeSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🥚",
                    fontSize = 44.sp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = wobble * 9f
                        // Качается вокруг нижней точки — так яйцо перекатывается,
                        // а не вращается вокруг своей середины.
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Одну секунду…",
                style = DesignType.ScreenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Загружаем рекламу — она помогает развивать «Инкубатор» " +
                    "и держать его бесплатным. Спасибо, что поддерживаете проект.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )

            Spacer(Modifier.height(28.dp))
            LoadingDots(phase = { dots })
        }
    }
}

/**
 * Три точки, по которым пробегает волна.
 *
 * [phase] — лямбда, а не значение: она читается внутри `graphicsLayer`, и точки
 * перерисовываются без рекомпозиции всей заставки.
 */
@Composable
private fun LoadingDots(phase: () -> Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(DotCount) { index ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer {
                        // Расстояние от бегущей волны до этой точки, 0..DotCount по кругу.
                        val distance = (phase() - index).mod(DotCount.toFloat())
                        // Волна поднимает точку только на своём участке, дальше — покой.
                        val bump = if (distance < 1f) sin(distance * PI).toFloat() else 0f
                        val scaled = 0.75f + 0.45f * bump
                        scaleX = scaled
                        scaleY = scaled
                        alpha = 0.35f + 0.65f * bump
                    }
                    .clip(CircleShape)
                    .background(DesignPalette.Accent),
            )
        }
    }
}
