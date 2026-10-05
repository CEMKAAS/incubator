package ru.zaroslikov.incubator.ui.guide

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.Fireworks
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.qr.QrLink
import ru.zaroslikov.incubator.qr.appIconBitmap
import ru.zaroslikov.incubator.qr.qrModules
import ru.zaroslikov.incubator.ui.batch.TimerRing
import ru.zaroslikov.incubator.ui.components.Social
import ru.zaroslikov.incubator.ui.components.openSocial
import ru.zaroslikov.incubator.ui.incubator.ProgressRing
import ru.zaroslikov.incubator.ui.qr.QrImage
import ru.zaroslikov.incubator.ui.start.speciesChipColor
import ru.zaroslikov.incubator.ui.start.SpeciesGlyph
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/*
 * Иллюстрации инструкции — сцены из тех же карточек, плашек и переключателя, что и приложение, с
 * цветами из `DesignPalette` (перекрашиваются с темой). Числа выдуманы: это пример, не данные.
 *
 * Каждая сцена получает `active` — стоит ли её страница на месте — и с этого момента проигрывает
 * движение; до того лежит в исходном состоянии.
 */

/** Высота сцены — на узком экране (360 dp минус поля) она выходит почти квадратной. */
private val StageHeight = 320.dp

/** Кремовая «сцена» с той же рамкой, что у карточек, — фон для мини-интерфейса. */
@Composable
private fun Stage(
    horizontalPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StageHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(DesignPalette.ProgressTrack)
            .border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Белая карточка внутри сцены — та же, что карточка инкубатора или закладки. */
@Composable
private fun MiniCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(DesignPalette.Surface)
            .border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

/** Доля «появления» — 0 до старта, 1 когда сцена проиграла вход. */
@Composable
private fun appearFraction(active: Boolean, delayMillis: Int = 0, durationMillis: Int = 600): Float {
    val fraction by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (active) durationMillis else 200,
            delayMillis = if (active) delayMillis else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "appear",
    )
    return fraction
}

/** Слой, всплывающий снизу и проявляющийся по доле [fraction]. */
private fun Modifier.rise(fraction: Float, distance: Float = 24f): Modifier = graphicsLayer {
    alpha = fraction
    translationY = (1f - fraction) * distance * density
}

// ---------------------------------------------------------------------------------
// 1. Добро пожаловать
// ---------------------------------------------------------------------------------

/**
 * Яйцо в акцентном круге, два дышащих кольца вокруг и три птицы по орбите — чипы
 * видов с главного экрана, только крупнее. Кольца дышат всегда, птицы всплывают на
 * приход страницы.
 */
@Composable
internal fun WelcomeScene(active: Boolean) {
    val breath = rememberInfiniteTransition(label = "breath")
    val ringScale = breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringScale",
    )
    val bob = breath.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val accent = DesignPalette.Accent

    Stage {
        // Кольца — от внешнего к внутреннему, чтобы внутреннее легло сверху.
        Box(
            Modifier
                .size(212.dp)
                .graphicsLayer { scaleX = ringScale.value; scaleY = ringScale.value }
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.06f))
        )
        Box(
            Modifier
                .size(156.dp)
                .graphicsLayer {
                    val s = 1f + (ringScale.value - 1f) * 0.6f
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_egg_design),
                contentDescription = null,
                tint = DesignPalette.OnAccent,
                modifier = Modifier.size(48.dp),
            )
        }

        // Птицы всплывают по очереди и дальше покачиваются в противофазе.
        OrbitBird("Курицы", x = -96.dp, y = -74.dp, phase = { bob.value }, fraction = appearFraction(active, 150))
        OrbitBird("Утки", x = 104.dp, y = -30.dp, phase = { -bob.value }, fraction = appearFraction(active, 300))
        OrbitBird("Гуси", x = -76.dp, y = 88.dp, phase = { bob.value * 0.6f }, fraction = appearFraction(active, 450))
    }
}

@Composable
private fun BoxScope.OrbitBird(bird: String, x: Dp, y: Dp, phase: () -> Float, fraction: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = x, y = y)
            .graphicsLayer {
                alpha = fraction
                translationY = phase() * 4f * density + (1f - fraction) * 16f * density
                scaleX = 0.7f + 0.3f * fraction
                scaleY = 0.7f + 0.3f * fraction
            }
            .size(44.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(speciesChipColor(bird)),
        contentAlignment = Alignment.Center,
    ) {
        SpeciesGlyph(bird = bird, fontSize = 20.sp)
    }
}

// ---------------------------------------------------------------------------------
// 2. Начните с инкубатора
// ---------------------------------------------------------------------------------

/**
 * Карточка инкубатора, как на главном экране, и под ней та самая кнопка «Инкубатор».
 * Полоса вместимости заполняется на приход страницы — так видно, что она полоса.
 */
@Composable
internal fun IncubatorScene(active: Boolean) {
    val fill by animateFloatAsState(
        targetValue = if (active) 0.67f else 0f,
        animationSpec = tween(900, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "capacity",
    )
    val card = appearFraction(active)
    val fab = appearFraction(active, delayMillis = 500)

    Stage {
        Column(Modifier.fillMaxSize()) {
            MiniCard(Modifier.rise(card)) {
                Text(
                    text = "Блиц 72",
                    style = DesignType.CardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Blitz · 2 закладки",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    listOf("Курицы", "Перепела").forEach { bird ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(speciesChipColor(bird)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpeciesGlyph(bird = bird, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_egg_design),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "48 / 72 мест",
                            style = DesignType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${(fill * 100).toInt()}%",
                        style = DesignType.MonoEmphasis,
                        color = DesignPalette.Accent,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(DesignPalette.ProgressTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill.coerceAtLeast(0.001f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(DesignPalette.Accent)
                    )
                }
            }
        }

        // Кнопка — в том же углу, где она стоит на главном экране.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .rise(fab, distance = 32f)
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(DesignPalette.Accent)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = DesignPalette.OnAccent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Инкубатор",
                style = DesignType.ToggleLabel,
                color = DesignPalette.OnAccent,
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// 3. Заложите яйца
// ---------------------------------------------------------------------------------

/**
 * Верх формы закладки — плитки вида и два поля — и под ним две строки расписания,
 * появляющиеся следом: сначала выбор, потом то, что из него получилось.
 */
@Composable
internal fun BatchScene(active: Boolean) {
    val form = appearFraction(active)
    val row1 = appearFraction(active, delayMillis = 450)
    val row2 = appearFraction(active, delayMillis = 600)

    Stage {
        Column(Modifier.fillMaxSize()) {
            MiniCard(Modifier.rise(form), padding = 12.dp) {
                Text(
                    text = "Вид птицы",
                    style = DesignType.FieldLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesTile("Курицы", "21 дн.", selected = true)
                    SpeciesTile("Перепела", "17 дн.", selected = false)
                    SpeciesTile("Гуси", "30 дн.", selected = false)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniField("Дата закладки", "08.09.2026", R.drawable.ic_calendar_design)
                    MiniField("Яиц", "48", null)
                }
            }
            Spacer(Modifier.height(10.dp))
            ScheduleLine(day = 1, temp = "37.8", damp = "60", turns = "4", airing = "0", fraction = row1)
            Spacer(Modifier.height(6.dp))
            ScheduleLine(day = 12, temp = "37.6", damp = "55", turns = "4", airing = "2×15", fraction = row2)
        }
    }
}

@Composable
private fun RowScope.SpeciesTile(bird: String, days: String, selected: Boolean) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DesignPalette.SpeciesTileSelected else DesignPalette.Surface)
            .border(
                width = if (selected) 1.2.dp else 0.8.dp,
                color = if (selected) DesignPalette.Accent else DesignPalette.CardBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeciesGlyph(bird = bird, fontSize = 18.sp)
        Text(
            text = bird,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = days,
            style = DesignType.MonoMicro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.MiniField(label: String, value: String, @DrawableRes icon: Int?) {
    Column(Modifier.weight(1f)) {
        Text(
            text = label,
            style = DesignType.FieldLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DesignPalette.ProgressTrack)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                style = DesignType.MonoField,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (icon != null) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Строка режима дня: номер и четыре величины с их значками из таблицы расписания. */
@Composable
private fun ScheduleLine(
    day: Int,
    temp: String,
    damp: String,
    turns: String,
    airing: String,
    fraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rise(fraction, distance = 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(DesignPalette.Surface)
            .border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(DesignPalette.PillSurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$day",
                style = DesignType.MonoSmallEmphasis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(10.dp))
        ScheduleFigure(R.drawable.ic_temperature_design, "$temp°", Modifier.weight(1.2f))
        ScheduleFigure(R.drawable.ic_humidity_design, "$damp%", Modifier.weight(1f))
        ScheduleFigure(R.drawable.ic_turn_design, turns, Modifier.weight(0.8f))
        ScheduleFigure(R.drawable.ic_airing_design, airing, Modifier.weight(1f))
    }
}

@Composable
private fun ScheduleFigure(@DrawableRes icon: Int, value: String, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = value,
            style = DesignType.MonoSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------
// 4. Инкубатор знает всё
// ---------------------------------------------------------------------------------

/** Вкладки экрана инкубатора — те же подписи и значки, что и в самом экране. */
private val DemoTabs = listOf(
    SlidingTab("Закладки", R.drawable.ic_egg_design_16),
    SlidingTab("Статистика", R.drawable.ic_chart_design),
    SlidingTab("Финансы", R.drawable.ic_wallet_design),
)

/** Сколько ждать между показами жеста. */
private const val DemoPauseMillis = 1500L

/** Длительность одного показанного свайпа — и пальца, и страницы под ним. */
private const val DemoSwipeMillis = 700

/**
 * Экран инкубатора в миниатюре: настоящий переключатель над настоящим пейджером, три карточки
 * показывают, что лежит на каждой вкладке.
 *
 * Сцена сама показывает свайп, пока человек не проведёт пальцем или не нажмёт вкладку (`tried` в
 * `rememberSaveable`); тогда подсказка меняется на подтверждение. Вложенный пейджер отдаёт жест
 * внешнему только на своих краях.
 */
@Composable
internal fun TabsScene(active: Boolean) {
    val demo = rememberPagerState(pageCount = { DemoTabs.size })
    val scope = rememberCoroutineScope()
    val dragged by demo.interactionSource.collectIsDraggedAsState()
    var tried by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) tried = true }

    // Палец: положение вдоль карточки (0 — левый край, 1 — правый) и видимость.
    val fingerX = remember { Animatable(0.85f) }
    val fingerAlpha = remember { Animatable(0f) }

    LaunchedEffect(active, tried) {
        // Перезапуск эффекта обрывает показ на полпути: палец гасится, а пейджер, чей
        // программный скролл отменили, сам не доезжает — без этого «таблетка» зависала
        // бы между вкладками до следующего цикла, ровно на странице про то, что она
        // указатель положения.
        fingerAlpha.snapTo(0f)
        fingerX.snapTo(0.85f)
        if (demo.currentPageOffsetFraction != 0f) demo.scrollToPage(demo.currentPage)
        if (!active || tried) return@LaunchedEffect
        delay(900)
        while (isActive) {
            val next = (demo.currentPage + 1) % DemoTabs.size
            // Вперёд — палец ведёт справа налево; возврат к первой — слева направо.
            val forward = next > demo.currentPage
            fingerX.snapTo(if (forward) 0.85f else 0.15f)
            fingerAlpha.animateTo(1f, tween(180))
            coroutineScope {
                launch {
                    demo.animateScrollToPage(
                        page = next,
                        animationSpec = tween(DemoSwipeMillis, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    fingerX.animateTo(
                        targetValue = if (forward) 0.15f else 0.85f,
                        animationSpec = tween(DemoSwipeMillis, easing = FastOutSlowInEasing),
                    )
                }
            }
            fingerAlpha.animateTo(0f, tween(220))
            delay(DemoPauseMillis)
        }
    }

    Stage(horizontalPadding = 12.dp) {
        Column(Modifier.fillMaxSize()) {
            SlidingTabSwitcher(
                tabs = DemoTabs,
                position = { demo.currentPage + demo.currentPageOffsetFraction },
                onSelect = { page ->
                    tried = true
                    scope.launch { demo.animateScrollToPage(page) }
                },
            )
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                HorizontalPager(
                    state = demo,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 12.dp,
                ) { page ->
                    when (page) {
                        0 -> DemoBatchCard()
                        1 -> DemoStatsCard()
                        else -> DemoFinanceCard()
                    }
                }
                // Палец поверх карточки. Полупрозрачный круг с ободком — так рисуют
                // касание записи экрана, и это читается как палец, а не как кнопка.
                // Смещение и прозрачность читаются в лямбдах: они меняются каждый кадр
                // показа, и через композицию перерисовывали бы всю сцену.
                val fingerSize = 40.dp
                val track = maxWidth - fingerSize
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((track.toPx() * fingerX.value).roundToInt(), 0) }
                        .graphicsLayer { alpha = fingerAlpha.value }
                        .size(fingerSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Crossfade(targetState = tried, animationSpec = tween(220), label = "swipeHint") { done ->
                Text(
                    text = if (done) "Именно так — и везде, где есть такой переключатель"
                    else "Проведите пальцем по карточке",
                    style = DesignType.Micro,
                    color = if (done) DesignPalette.Accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DemoBatchCard() {
    MiniCard(Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction = 12f / 21f, species = "Курицы", diameter = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Курицы",
                    style = DesignType.BatchTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "День 12/21 · 48 яиц",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DesignPalette.StatusActiveSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Инкубация",
                    style = DesignType.Micro,
                    color = DesignPalette.StatusActiveText,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Вывод 29 сент.",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "через 9 дн.",
                style = DesignType.MonoAccent,
                color = DesignPalette.DateEmphasis,
            )
        }
    }
}

@Composable
private fun DemoStatsCard() {
    MiniCard(Modifier.fillMaxHeight()) {
        TileRow(spacing = 10.dp) {
            DemoMetric("ВЫВОД", "86%", "из 550 яиц", tileWeight())
            DemoMetric("ЯИЦ", "700", "10 закладок", tileWeight())
        }
    }
}

@Composable
private fun DemoMetric(label: String, value: String, caption: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DesignPalette.MeasureTile)
            .padding(12.dp),
    ) {
        Text(
            text = label,
            style = DesignType.PillLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = DesignType.MetricValue,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemoFinanceCard() {
    MiniCard(Modifier.fillMaxHeight()) {
        Text(
            text = "БАЛАНС",
            style = DesignType.PillLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "+3 200 ₽",
            style = DesignType.MetricValue,
            color = DesignPalette.Accent,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Окупаемость инкубатора",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "64%",
                style = DesignType.MonoEmphasis,
                color = DesignPalette.Accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(DesignPalette.ProgressTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.64f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(DesignPalette.Accent)
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// 5. Каждый день под контролем
// ---------------------------------------------------------------------------------

/**
 * Две плитки замеров из шторки закладки, под ними напоминание и овоскопирование.
 * Колокольчик качается на приход страницы — как уведомление, которое только что
 * пришло; остальное всплывает по очереди.
 */
@Composable
internal fun DailyScene(active: Boolean) {
    val tiles = appearFraction(active)
    val bell = appearFraction(active, delayMillis = 350)
    val candling = appearFraction(active, delayMillis = 500)

    // Качание колокольчика: несколько затухающих взмахов после появления.
    val swing = remember { Animatable(0f) }
    LaunchedEffect(active) {
        swing.snapTo(0f)
        if (!active) return@LaunchedEffect
        delay(700)
        val amplitudes = listOf(18f, -14f, 10f, -6f, 3f, 0f)
        amplitudes.forEach { angle ->
            swing.animateTo(angle, tween(110, easing = LinearEasing))
        }
    }

    Stage {
        Column(Modifier.fillMaxSize()) {
            MiniCard(Modifier.rise(tiles), padding = 14.dp) {
                Text(
                    text = "Замеры за сегодня · День 12",
                    style = DesignType.CardHeading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeasureTile(
                        icon = R.drawable.ic_temperature_design,
                        label = "ТЕМПЕРАТУРА",
                        value = "37.8°",
                        verdict = "в норме",
                        verdictColor = DesignPalette.StatusActiveText,
                        modifier = Modifier.weight(1f),
                    )
                    MeasureTile(
                        icon = R.drawable.ic_humidity_design,
                        label = "ВЛАЖНОСТЬ",
                        value = "58%",
                        verdict = "чуть выше нормы",
                        verdictColor = DesignPalette.DateEmphasis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            EventRow(
                fraction = bell,
                surface = DesignPalette.IncomeSurface,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = DesignPalette.OnAccent,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                rotationZ = swing.value
                                transformOrigin = TransformOrigin(0.5f, 0.1f)
                            },
                    )
                },
                title = "08:00 · Проверить инкубатор",
                caption = "напоминание по расписанию закладки",
            )
            Spacer(Modifier.height(8.dp))
            EventRow(
                fraction = candling,
                surface = DesignPalette.SpeciesTileSelected,
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_egg_design),
                        contentDescription = null,
                        tint = DesignPalette.OnAccent,
                        modifier = Modifier.size(18.dp),
                    )
                },
                title = "Овоскопирование · День 7",
                caption = "что искать на просвет — покажем",
            )
        }
    }
}

@Composable
private fun MeasureTile(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    verdict: String,
    verdictColor: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DesignPalette.MeasureTile)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = DesignType.PillLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = DesignType.MeasureValue,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = verdict,
            style = DesignType.Micro,
            color = verdictColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun EventRow(
    fraction: Float,
    surface: Color,
    icon: @Composable () -> Unit,
    title: String,
    caption: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rise(fraction, distance = 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .border(BorderStroke(0.8.dp, DesignPalette.CardBorder), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DesignPalette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = caption,
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// 6. QR-код на инкубаторе
// ---------------------------------------------------------------------------------

/**
 * Карточка с QR-кодом, как в шторке «QR-код», и рамка сканера поверх: линия ходит по
 * коду, и следом снизу всплывает то, что камера по нему открывает, — «Замеры за
 * сегодня». Код настоящий — тот же `qrModules` и тот же `QrImage`, что печатает
 * приложение, — только ведёт на выдуманный инкубатор.
 */
@Composable
internal fun QrScene(active: Boolean) {
    val context = LocalContext.current
    val modules = remember { qrModules(QrLink.encode(1)) }
    val logo = remember(context.packageName) { appIconBitmap(context, 96).asImageBitmap() }
    val card = appearFraction(active)
    val found = appearFraction(active, delayMillis = 1500)

    // Линия сканера: туда и обратно, пока страница на месте. Читается только в
    // отрисовке — она меняется каждый кадр.
    val scan = remember { Animatable(0f) }
    LaunchedEffect(active) {
        scan.snapTo(0f)
        if (!active) return@LaunchedEffect
        delay(500)
        while (isActive) {
            scan.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            scan.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val accent = DesignPalette.Accent

    Stage {
        Column(Modifier.fillMaxSize()) {
            MiniCard(Modifier.rise(card), padding = 12.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(136.dp)
                ) {
                    QrImage(
                        modules = modules,
                        logo = logo,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize(),
                    )
                    Canvas(Modifier.matchParentSize()) {
                        val stroke = 3.dp.toPx()
                        val arm = 18.dp.toPx()
                        val w = size.width
                        val h = size.height
                        // Четыре уголка рамки — как видоискатель сканера.
                        listOf(
                            Triple(Offset(0f, 0f), Offset(arm, 0f), Offset(0f, arm)),
                            Triple(Offset(w, 0f), Offset(w - arm, 0f), Offset(w, arm)),
                            Triple(Offset(0f, h), Offset(arm, h), Offset(0f, h - arm)),
                            Triple(Offset(w, h), Offset(w - arm, h), Offset(w, h - arm)),
                        ).forEach { (corner, along, down) ->
                            drawLine(accent, corner, along, stroke, StrokeCap.Round)
                            drawLine(accent, corner, down, stroke, StrokeCap.Round)
                        }
                        val inset = 8.dp.toPx()
                        val y = inset + (h - inset * 2) * scan.value
                        drawLine(
                            color = accent,
                            start = Offset(inset, y),
                            end = Offset(w - inset, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Блиц 72",
                    style = DesignType.CardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = "Blitz · 72 места",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(10.dp))
            EventRow(
                fraction = found,
                surface = DesignPalette.IncomeSurface,
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_temperature_design),
                        contentDescription = null,
                        tint = DesignPalette.OnAccent,
                        modifier = Modifier.size(18.dp),
                    )
                },
                title = "Замеры за сегодня",
                caption = "один замер — сразу во все закладки",
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// 7. Таймер проветривания
// ---------------------------------------------------------------------------------

/** На сколько минут заведён таймер сцены и за сколько он их «проживает». */
private const val DemoTimerMinutes = 15
private const val DemoTimerMillis = 4200

/**
 * Карточка таймера из формы замера: кольцо отсчитывает пятнадцать минут за несколько
 * секунд, краснеет на «Время вышло!» — и строка замера под карточкой получает то,
 * ради чего таймер заводили: минуты, подставленные в замер. Кольцо — настоящее `TimerRing`.
 */
@Composable
internal fun TimerScene(active: Boolean) {
    val card = appearFraction(active)
    val progress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        done = false
        progress.snapTo(0f)
        if (!active) return@LaunchedEffect
        delay(700)
        progress.animateTo(1f, tween(DemoTimerMillis, easing = LinearEasing))
        done = true
    }
    val form = appearFraction(active, delayMillis = 350)

    Stage {
        Column(Modifier.fillMaxSize()) {
            MiniCard(Modifier.rise(card), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) DemoTimerDone() else DemoTimerRunning { progress.value }
                    Spacer(Modifier.width(14.dp))
                    Crossfade(targetState = done, animationSpec = tween(220), label = "timerText") { over ->
                        Column {
                            Text(
                                text = if (over) "Время вышло!" else "Проветривание идёт",
                                style = DesignType.ListItemTitle,
                                color = if (over) DesignPalette.Expense
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (over) "Закройте инкубатор"
                                else "$DemoTimerMinutes мин · закрыть в 15:26",
                                style = DesignType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Crossfade(targetState = done, animationSpec = tween(220), label = "timerButtons") { over ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (over) {
                            DemoTimerButton("Готово, выключить сигнал", filled = true)
                        } else {
                            DemoTimerButton("Завершить", filled = false)
                            DemoTimerButton("Отменить", filled = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            EventRow(
                fraction = form,
                surface = DesignPalette.IncomeSurface,
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_airing_design),
                        contentDescription = null,
                        tint = DesignPalette.OnAccent,
                        modifier = Modifier.size(18.dp),
                    )
                },
                title = if (done) "Проветривание · $DemoTimerMinutes мин" else "Проветривание · — мин",
                caption = if (done) "минуты подставлены в форму замера"
                else "минуты подставятся в замер сами",
            )
        }
    }
}

/**
 * Идущее кольцо. Доля приходит лямбдой и читается здесь, а не в сцене: она меняется
 * каждый кадр, и перестраиваться должно одно кольцо, а не вся страница.
 */
@Composable
private fun DemoTimerRunning(progress: () -> Float) {
    val fraction = progress()
    val left = ((1f - fraction) * DemoTimerMinutes * 60).roundToInt()
    TimerRing(progress = fraction, pulse = 0f) {
        Text(
            text = "%d:%02d".format(left / 60, left % 60),
            style = DesignType.MonoEmphasis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Кольцо «время вышло»: полное, красное, с расходящимся ореолом. */
@Composable
private fun DemoTimerDone() {
    val pulse by rememberInfiniteTransition(label = "timerPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
        label = "pulse",
    )
    TimerRing(progress = 1f, pulse = pulse, alarm = true) {
        Text(
            text = "0:00",
            style = DesignType.MonoEmphasis,
            color = DesignPalette.Expense,
        )
    }
}

@Composable
private fun RowScope.DemoTimerButton(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(shape)
            .background(if (filled) DesignPalette.Accent else DesignPalette.Surface)
            .border(
                width = 0.8.dp,
                color = if (filled) DesignPalette.Accent else DesignPalette.CardBorder,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = DesignType.ToggleLabel,
            color = if (filled) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------
// 8. Вывод и итоги
// ---------------------------------------------------------------------------------

/**
 * Поздравление в миниатюре: карточка итога, над которой идёт настоящий салют
 * (`Fireworks` из `:design`). Числа досчитываются на приход страницы; под ними — две
 * двери, которые открываются вокруг вывода: даты в календарь и птенцы в «Моё хозяйство».
 *
 * Салют рисуется поверх карточки, а не за ней: сцена невелика, полосы над и под
 * карточкой узкие, и залп, родившийся за непрозрачной карточкой, не был бы виден вовсе.
 * Он собран, только пока страница на месте: это цикл кадров, и крутить его за кадром
 * незачем.
 */
@Composable
internal fun HatchScene(active: Boolean) {
    val card = appearFraction(active)
    val count = appearFraction(active, delayMillis = 300, durationMillis = 900)
    val links = appearFraction(active, delayMillis = 700)
    val hatched = (42 * count).roundToInt()

    Stage {
        MiniCard(Modifier.rise(card), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🐣", fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Поздравляем!",
                        style = DesignType.CardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Курицы · вывод за 21 день",
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TileRow(spacing = 8.dp) {
                HatchTile("Заложено", "48", MaterialTheme.colorScheme.onSurface, tileWeight())
                HatchTile("Выведено", "$hatched", DesignPalette.Accent, tileWeight())
                HatchTile("Вывод", "${hatched * 100 / 48}%", DesignPalette.Accent, tileWeight())
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DesignPalette.IncomeSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Прибыль",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "+3 200 ₽",
                    style = DesignType.MonoEmphasis,
                    color = DesignPalette.Accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.rise(links, distance = 12f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HatchLink(R.drawable.baseline_calendar_month_24, "Календарь")
                HatchLink(R.drawable.baseline_cottage_24, "Моё хозяйство")
            }
        }
        if (active) {
            Fireworks(
                modifier = Modifier.matchParentSize(),
                originBands = listOf(0.02f..0.18f, 0.82f..0.98f),
            )
        }
    }
}

@Composable
private fun HatchTile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DesignPalette.MeasureTile)
            .padding(10.dp),
    ) {
        Text(
            text = label,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = DesignType.AnalyticsValue,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.HatchLink(@DrawableRes icon: Int, text: String) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .clip(shape)
            .border(0.8.dp, DesignPalette.Accent, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = DesignType.Micro,
            color = DesignPalette.Accent,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------
// 9. Присоединяйтесь к нам
// ---------------------------------------------------------------------------------

/**
 * Две карточки — группа ВКонтакте и канал в Telegram, — и, в отличие от остальных сцен,
 * настоящие: нажатие открывает соцсеть. Иллюстрация здесь и есть действие, и рисовать
 * поверх неё отдельные кнопки значило бы показать одно и то же дважды. Над карточками —
 * значок приложения в дышащем кольце, тот же жест, что у первой страницы: рассказ
 * начался с него и им же кончается.
 */
@Composable
internal fun SocialScene(active: Boolean) {
    val uriHandler = LocalUriHandler.current
    val breath = rememberInfiniteTransition(label = "social-breath")
    val ringScale = breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "social-ring",
    )
    val badge = appearFraction(active)

    Stage {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(96.dp).rise(badge),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .graphicsLayer { scaleX = ringScale.value; scaleY = ringScale.value }
                        .clip(CircleShape)
                        .background(DesignPalette.Accent.copy(alpha = 0.10f))
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(DesignPalette.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_egg_design),
                        contentDescription = null,
                        tint = DesignPalette.OnAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Social.entries.forEachIndexed { index, social ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                SocialCard(
                    social = social,
                    fraction = appearFraction(active, delayMillis = 200 + index * 180),
                    onClick = { uriHandler.openSocial(social) },
                )
            }
        }
    }
}

/** Карточка соцсети: значок, название, адрес и «Подписаться» — так видно, что она нажимается. */
@Composable
private fun SocialCard(social: Social, fraction: Float, onClick: () -> Unit) {
    MiniCard(
        modifier = Modifier
            .rise(fraction)
            .clickable(onClickLabel = "Открыть ${social.title}", onClick = onClick),
        padding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DesignPalette.IncomeSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = social.icon),
                    contentDescription = null,
                    tint = DesignPalette.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = social.title,
                    style = DesignType.ListItemTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = social.label,
                    style = DesignType.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Подписаться",
                style = DesignType.Micro,
                color = DesignPalette.OnAccent,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DesignPalette.Accent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
