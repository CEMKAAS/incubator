package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Салют — залпы разноцветных искр, разлетающихся из случайных точек верхней части
 * холста; стоит фоном за поздравлением после вывода.
 *
 * Считается, а не проигрывается из файла: у приложения нет ни Lottie, ни спрайтов, а
 * искра — это точка, летящая по параболе. У каждого залпа свои момент, точка, цвет и
 * зерно случайности, а положение искры в кадре — чистая функция от прошедшего времени
 * ([particleAt]): состояние на кадр не копится, и пропущенный кадр ничего не сдвигает.
 *
 * Перерисовка идёт по [withFrameNanos] и обходится **без рекомпозиции**: часы кадра
 * лежат в состоянии, которое читает только лямбда отрисовки, так что композиция
 * поздравления над салютом не перестраивается шестьдесят раз в секунду.
 *
 * Первые секунды залпы частые — ради них салют и заводили, — потом редкие: диалог
 * могут читать долго, и непрерывная канонада за ним мешала бы читать цифры.
 *
 * @param colors цвета искр; по умолчанию — акцент, янтарь графика поворотов, красный
 *        расхода, синий влажности и коричневый даты: пять цветов, которые уже есть в
 *        теме, а не пять новых.
 * @param originBands полосы по высоте (доли холста, 0 — верх), в которых рождаются
 *        залпы. По умолчанию верхняя половина; поздравление отдаёт полосы над и под
 *        своей карточкой — залп, родившийся за непрозрачной карточкой, не виден вовсе.
 */
@Composable
fun Fireworks(
    modifier: Modifier = Modifier,
    originBands: List<ClosedFloatingPointRange<Float>> = listOf(0.12f..0.55f),
    colors: List<Color> = listOf(
        DesignPalette.Accent,
        DesignPalette.ChartTurn,
        DesignPalette.Expense,
        DesignPalette.ChartDamp,
        DesignPalette.DateEmphasis,
    ),
) {
    val bursts = remember { mutableStateListOf<Burst>() }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(colors, originBands) {
        val random = Random(System.nanoTime())
        var startNanos = -1L
        var elapsed = 0L
        var nextBurstAt = 0L
        // Салют конечен: после [TOTAL_NANOS] новых залпов нет, а как только догорит
        // последний, цикл кадров кончается — карточку могут читать долго, и рисовать
        // пустой холст шестьдесят раз в секунду всё это время незачем.
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                elapsed = now - startNanos
                if (elapsed >= nextBurstAt && elapsed < TOTAL_NANOS) {
                    val band = originBands[random.nextInt(originBands.size)]
                    bursts += Burst(
                        x = 0.12f + random.nextFloat() * 0.76f,
                        y = band.start + random.nextFloat() * (band.endInclusive - band.start),
                        startNanos = now,
                        color = colors[random.nextInt(colors.size)],
                        seed = random.nextInt(),
                        particles = 30 + random.nextInt(16),
                    )
                    val interval = if (elapsed < INTENSE_NANOS) INTENSE_INTERVAL_NANOS else CALM_INTERVAL_NANOS
                    nextBurstAt = elapsed + interval + (random.nextFloat() * 0.4f * interval).toLong()
                }
                bursts.removeAll { now - it.startNanos > BURST_LIFE_NANOS }
                frameNanos = now
            }
            if (bursts.isEmpty() && elapsed >= TOTAL_NANOS) break
        }
    }

    val dotRadius = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(modifier) {
        val now = frameNanos
        for (burst in bursts) {
            drawBurst(burst, (now - burst.startNanos) / 1_000_000_000f, dotRadius)
        }
    }
}

/** Один залп: откуда, когда, каким цветом и сколько искр. */
private class Burst(
    val x: Float,
    val y: Float,
    val startNanos: Long,
    val color: Color,
    val seed: Int,
    val particles: Int,
)

private const val BURST_LIFE_SECONDS = 1.7f
private const val BURST_LIFE_NANOS = (BURST_LIFE_SECONDS * 1_000_000_000L).toLong()
private const val INTENSE_NANOS = 6_000_000_000L
private const val TOTAL_NANOS = 40_000_000_000L
private const val INTENSE_INTERVAL_NANOS = 480_000_000L
private const val CALM_INTERVAL_NANOS = 1_500_000_000L

/** Сопротивление воздуха: искра быстро теряет скорость и повисает, как настоящая. */
private const val DRAG = 2.4f

private fun DrawScope.drawBurst(burst: Burst, seconds: Float, dotRadius: Float) {
    if (seconds < 0f || seconds > BURST_LIFE_SECONDS) return
    val origin = Offset(burst.x * size.width, burst.y * size.height)
    val reach = min(size.width, size.height) * 0.34f
    val gravity = size.height * 0.22f
    val life = seconds / BURST_LIFE_SECONDS
    // Гаснут не линейно: первую половину жизни искра горит в полную силу.
    val alpha = (1f - ((life - 0.45f) / 0.55f).coerceIn(0f, 1f)) * 0.95f
    val radius = dotRadius * (1f - 0.55f * life)
    val random = Random(burst.seed)
    repeat(burst.particles) { i ->
        val angle = (2.0 * PI * i / burst.particles + random.nextDouble(-0.18, 0.18)).toFloat()
        // Разброс скоростей широкий: искры одной скорости летят ровным кольцом, а
        // настоящий залп заполняет круг целиком.
        val speed = reach * (0.35f + random.nextFloat() * 0.65f)
        drawCircle(
            color = burst.color,
            radius = radius,
            center = particleAt(origin, angle, speed, gravity, seconds),
            alpha = alpha,
        )
    }
}

/**
 * Где искра через [t] секунд: разлёт с затуханием скорости плюс падение.
 *
 * Пробег по направлению — интеграл скорости с сопротивлением, `v·(1−e^(−k·t))/k`,
 * поэтому искра стремительно вылетает и почти замирает; тяжесть добавляет `g·t²/2`.
 */
private fun particleAt(origin: Offset, angle: Float, speed: Float, gravity: Float, t: Float): Offset {
    val travel = speed * (1f - exp(-DRAG * t)) / DRAG
    return Offset(
        x = origin.x + cos(angle) * travel,
        y = origin.y + sin(angle) * travel + gravity * t * t / 2f,
    )
}
