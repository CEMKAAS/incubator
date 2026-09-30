package ru.zaroslikov.incubator.ui.stories

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.stories.SlideType
import ru.zaroslikov.incubator.stories.Story
import ru.zaroslikov.incubator.stories.StoryParser
import ru.zaroslikov.incubator.stories.StorySlide

/** Какая часть ширины слева листает назад; остальное — вперёд. Как в любых историях. */
private const val BackZone = 0.3f

/** Насколько надо стянуть историю вниз, чтобы она закрылась. */
private val DismissDrag = 140.dp

/**
 * Просмотр историй на весь экран — чёрный, со своими правилами, как камера у сканера:
 * это витрина, а не экран приложения, и тема здесь не участвует.
 *
 * Жесты — общепринятые, чтобы их не надо было объяснять: тап справа — следующий слайд,
 * слева — предыдущий, удержание — пауза, свайп вбок — соседняя история, свайп вниз —
 * закрыть. Последний слайд последней истории закрывает просмотр сам.
 *
 * **Слайд идёт, только пока его видно**: пока картинка не загрузилась, пока палец держит
 * экран, пока страница едет под пальцем, пока историю тянут вниз и пока приложение не на
 * переднем плане (открыли ссылку из кнопки). Иначе история досматривалась бы сама, пока
 * человек читает сайт, — и отметка «просмотрена» ушла бы на сервер за него.
 *
 * Системная строка на время просмотра прячется: над чёрным её тёмные значки не видны, а
 * светлые спорили бы с полосками прогресса.
 */
@Composable
fun StoryViewer(
    stories: List<Story>,
    startIndex: Int,
    onViewed: (Story) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        HideSystemBars()
        // Лента на время просмотра неподвижна: ответ сервера, пришедший посреди просмотра,
        // переставил бы истории под пальцем — номер страницы указывал бы уже на другую.
        val snapshot = remember { stories }
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, (snapshot.size - 1).coerceAtLeast(0)),
        ) { snapshot.size }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissPx = with(density) { DismissDrag.toPx() }
        var dragY by remember { mutableFloatStateOf(0f) }
        val dragging by remember { derivedStateOf { dragY > 0f } }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val lifecycleState by lifecycle.currentStateAsState()
        val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (dragY > dismissPx) onDismiss() else dragY = 0f },
                        onDragCancel = { dragY = 0f },
                    ) { change, delta ->
                        dragY = (dragY + delta).coerceAtLeast(0f)
                        change.consume()
                    }
                }
                .graphicsLayer {
                    translationY = dragY
                    val shrink = (dragY / (size.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
                    scaleX = 1f - shrink * 0.15f
                    scaleY = 1f - shrink * 0.15f
                    alpha = 1f - shrink * 0.6f
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { snapshot[it].id },
            ) { page ->
                val story = snapshot[page]
                val active = pagerState.settledPage == page &&
                    !pagerState.isScrollInProgress && !dragging && resumed
                StoryPage(
                    story = story,
                    active = active,
                    onCompleted = {
                        onViewed(story)
                        if (page < snapshot.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        } else {
                            onDismiss()
                        }
                    },
                    onPreviousStory = {
                        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
                    },
                    onClose = onDismiss,
                )
            }
        }
    }
}

/**
 * Одна история: полоски прогресса, заголовок, слайд и его подпись с кнопкой.
 *
 * Номер слайда переживает уход на соседнюю историю и возврат (`rememberSaveable` по id):
 * вернувшись назад, человек продолжает с того слайда, где ушёл. Досмотренную же историю
 * возврат показывает с начала — иначе пустой остаток последнего слайда тут же «досмотрел»
 * бы её ещё раз и перелистнул дальше.
 */
@Composable
private fun StoryPage(
    story: Story,
    active: Boolean,
    onCompleted: () -> Unit,
    onPreviousStory: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val images = storyImages()
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnPrevious by rememberUpdatedState(onPreviousStory)

    var slideIndex by rememberSaveable(story.id) { mutableIntStateOf(0) }
    var restart by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val slide = story.slides[slideIndex.coerceIn(0, story.slides.lastIndex)]
    var ready by remember(story.id, slideIndex) { mutableStateOf(false) }
    var failed by remember(story.id, slideIndex) { mutableStateOf(false) }
    val progress = remember(story.id, slideIndex, restart) { Animatable(0f) }

    fun next() {
        if (slideIndex < story.slides.lastIndex) {
            slideIndex++
        } else {
            completed = true
            currentOnCompleted()
        }
    }

    fun previous() {
        if (slideIndex > 0) slideIndex-- else {
            restart++
            currentOnPrevious()
        }
    }

    // Вернулись на уже досмотренную — с начала.
    LaunchedEffect(active) {
        if (active && completed) {
            completed = false
            slideIndex = 0
            restart++
        }
    }

    val running = active && ready && !held && !completed
    LaunchedEffect(running, story.id, slideIndex, restart) {
        if (!running) return@LaunchedEffect
        val remaining = ((1f - progress.value) * slide.durationMs).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        next()
    }

    // Следующая картинка — заранее, пока идёт эта: переход на неё не должен начинаться
    // со спиннера.
    val screenSide = screenMaxSidePx()
    LaunchedEffect(story.id, slideIndex) {
        story.slides.getOrNull(slideIndex + 1)
            ?.takeIf { it.type == SlideType.IMAGE }
            ?.let { images.load(it.mediaUrl, screenSide) }
    }

    Box(Modifier.fillMaxSize()) {
        // Слайд и зоны касания. Кнопки поверх (закрыть, кнопка слайда) ловят свои
        // нажатия сами и сюда их не пускают.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(story.id) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                        // Удержание — только пауза: отпущенный палец не должен ещё и листать.
                        onLongPress = {},
                        onTap = { offset -> if (offset.x < size.width * BackZone) previous() else next() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            key(story.id, slideIndex) {
                when (slide.type) {
                    SlideType.IMAGE -> ImageSlide(
                        url = slide.mediaUrl,
                        maxSide = screenSide,
                        onReady = { ok -> ready = true; failed = !ok },
                    )
                    SlideType.VIDEO -> VideoSlide(
                        url = slide.mediaUrl,
                        playing = running,
                        onReady = { ok -> ready = true; failed = !ok },
                    )
                }
            }
            if (!ready) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            } else if (failed) {
                Text(
                    text = "Не удалось загрузить",
                    color = Color.White.copy(alpha = 0.8f),
                    style = DesignType.Body,
                )
            }
        }

        // Затемнения сверху и снизу — чтобы белые полоски и подпись читались на любой
        // картинке, в том числе на белой.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )
        if (slide.text != null || slide.buttonUrl != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ProgressSegments(
                count = story.slides.size,
                current = slideIndex,
                progress = { progress.value },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
            )
            StoryHeader(story = story, onClose = onClose)
            Spacer(Modifier.weight(1f))
            SlideCaption(
                slide = slide,
                onButton = { url ->
                    Analytics.report(
                        Events.STORY_BUTTON,
                        mapOf(
                            "История" to story.title,
                            "Ссылка" to if (isAppLink(url)) "приложение" else "сайт",
                        ),
                    )
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    // Свой deep link — только в себя: `incubator://` из чужого приложения с
                    // той же схемой открывать нельзя.
                    if (isAppLink(url)) intent.setPackage(context.packageName)
                    try {
                        context.startActivity(intent)
                        // Ссылка внутрь приложения ведёт на экран под просмотром — закрыть,
                        // иначе переход случится за ним.
                        if (isAppLink(url)) onClose()
                    } catch (e: ActivityNotFoundException) {
                        // Нечем открыть — кнопка просто ничего не делает; история идёт дальше.
                    }
                },
            )
        }
    }
}

private fun isAppLink(url: String) = url.lowercase().startsWith("${StoryParser.APP_SCHEME}:")

/**
 * Полоски прогресса: по одной на слайд. Доля текущей читается в `drawBehind`, а не в
 * композиции, — она меняется каждый кадр, и перерисовывать ради неё весь слайд незачем.
 */
@Composable
private fun ProgressSegments(
    count: Int,
    current: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(2.5.dp)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = radius)
                        val fraction = when {
                            index < current -> 1f
                            index == current -> progress().coerceIn(0f, 1f)
                            else -> 0f
                        }
                        if (fraction > 0f) {
                            drawRoundRect(
                                Color.White,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = radius,
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun StoryHeader(story: Story, onClose: () -> Unit) {
    val images = storyImages()
    val side = with(LocalDensity.current) { 32.dp.roundToPx() }
    val cover = rememberStoryBitmap(images, story.coverUrl, side)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = story.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Закрыть историю", tint = Color.White)
        }
    }
}

/** Подпись слайда и его кнопка — внизу, над затемнением. */
@Composable
private fun SlideCaption(slide: StorySlide, onButton: (String) -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        slide.text?.let { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val url = slide.buttonUrl
        if (url != null) {
            if (slide.text != null) Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onButton(url) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    text = slide.buttonText ?: StoryParser.DEFAULT_BUTTON_TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImageSlide(url: String, maxSide: Int, onReady: (Boolean) -> Unit) {
    val images = storyImages()
    var bitmap by remember(url) { mutableStateOf(images.cached(url, maxSide)) }
    val currentOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(url) {
        val loaded = bitmap ?: images.load(url, maxSide)
        bitmap = loaded
        currentOnReady(loaded != null)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Видео — платформенный `VideoView`, а не ExoPlayer: истории — единственное видео в
 * приложении, и полтора мегабайта плеера ради пары роликов в месяц — плохой обмен.
 * Слайд длится `durationMs` с сервера, а не столько, сколько ролик: так его задал автор
 * истории, и прогресс-полоска не зависит от того, успело ли видео скачаться.
 */
@Composable
private fun VideoSlide(url: String, playing: Boolean, onReady: (Boolean) -> Unit) {
    val currentOnReady by rememberUpdatedState(onReady)
    var view by remember { mutableStateOf<VideoView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    currentOnReady(true)
                }
                setOnErrorListener { _, _, _ ->
                    currentOnReady(false)
                    true
                }
                setVideoURI(Uri.parse(url))
                view = this
            }
        },
    )
    LaunchedEffect(view, playing) {
        val video = view ?: return@LaunchedEffect
        if (playing) video.start() else if (video.isPlaying) video.pause()
    }
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }
}

/** Большая сторона экрана в пикселях — предел, до которого уменьшается слайд. */
@Composable
private fun screenMaxSidePx(): Int {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return with(density) {
        maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx()
    }
}

/** Прячет системные строки окна диалога на время просмотра; жест от края их вернёт. */
@Composable
private fun HideSystemBars() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val view = LocalView.current
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { }
    }
}
