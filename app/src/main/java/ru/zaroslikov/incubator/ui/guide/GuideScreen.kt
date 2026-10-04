package ru.zaroslikov.incubator.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.design.components.SheetCircleButton
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.design.theme.bodyFontFamily
import kotlin.math.abs

/**
 * Инструкция первого запуска — «Как пользоваться».
 *
 * Полноценный пункт назначения, а не шторка: она занимает экран целиком и сама
 * листается вбок, а шторка поверх главного экрана спорила бы с ним за жест. Открывается
 * дважды за жизнь приложения: первым запуском (стартовый маршрут, пока
 * `AppSettings.guidePending`) и повторно — из «О приложении» (`revisit = true`).
 */
object GuideDestination : NavigationDestination {
    override val route = "Guide"
    const val revisitArg = "revisit"
    val routeWithArgs = "$route?$revisitArg={$revisitArg}"
    fun routeFor(revisit: Boolean): String = "$route?$revisitArg=$revisit"
}

/** Отступ по краям экрана — тот же, что у главного. */
private val ScreenPadding = 20.dp

/** Текст под заголовком страницы: Inter 15 / 23 — крупнее `Body`, это читают, а не сверяют. */
private val GuideBody = TextStyle(
    fontFamily = bodyFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 23.sp,
)

/**
 * Страницы инструкции — в том порядке, в каком приложением пользуются: сначала
 * устройство, потом партия яиц в нём, потом сам экран инкубатора и то, что делают
 * каждый день. Страница про вкладки инкубатора стоит четвёртой, а не второй: к ней
 * инкубатор уже заведён и закладка заложена, то есть есть чему считаться на
 * «Статистике» и «Финансах».
 *
 * Последние три страницы — то, что появилось позже и чего по первым пяти не угадать:
 * QR-код на устройстве, таймер проветривания и итог вывода. Они стоят после «Каждый
 * день под контролем», потому что опираются на неё: замер и проветривание там уже
 * названы, а здесь показано, как сделать их быстрее и чем всё кончается.
 */
internal enum class GuidePage(val title: String, val body: String) {
    Welcome(
        title = "Добро пожаловать",
        body = "Инкубатор ведёт закладку яиц от первого дня до вывода: держит режим " +
            "по дням, напоминает о проверках и считает, сколько вылупилось и во что " +
            "это обошлось.",
    ),
    Incubator(
        title = "Начните с инкубатора",
        body = "Инкубатор — это ваше устройство. Заведите его кнопкой «Инкубатор» " +
            "внизу главного экрана. Обязательно только название, а марка, " +
            "вместимость и цена — по желанию.",
    ),
    Batch(
        title = "Заложите яйца",
        body = "Закладка — партия яиц внутри инкубатора: название, вид птицы, дата и " +
            "число яиц. " +
            "Режим по дням — температура, влажность, перевороты и проветривания — " +
            "составится сам, а напоминания придут в выбранное время.",
    ),
    Tabs(
        title = "Инкубатор знает всё",
        // Абзац один и он короткий не от скупости: страница прокручивается, но текст,
        // ушедший под точки страниц, читается как отсутствующий, а здесь под него
        // остаётся семь строк — сцена сверху занимает ровно столько же, сколько на
        // остальных страницах. Про сам свайп сказано вскользь: его показывает и
        // подтверждает сцена, а слова о нём в третий раз были бы уже нравоучением.
        body = "Откройте инкубатор — и три вкладки ответят на три вопроса. " +
            "«Закладки» — что идёт сейчас и когда ждать вывод. " +
            "«Статистика» — сколько вылупилось и у каких пород выходит лучше. " +
            "«Финансы» — окупился ли он. Вкладки листаются свайпом.",
    ),
    Daily(
        title = "Каждый день под контролем",
        body = "Записывайте замеры — приложение сравнит их с режимом дня. Отмечайте " +
            "овоскопирование, получайте напоминания, а в конце внесите результат: " +
            "вывод, потери и финансы.",
    ),
    Qr(
        title = "QR-код на инкубаторе",
        body = "Откройте инкубатор и нажмите значок QR-кода: его можно распечатать и " +
            "наклеить на устройство. Наведите камеру телефона или сканер в приложении " +
            "— сразу откроются «Замеры за сегодня», и один замер запишется во все " +
            "идущие закладки.",
    ),
    Timer(
        title = "Таймер проветривания",
        body = "Открыли крышку — запустите таймер прямо в форме замера. Он досчитает, " +
            "даже если приложение свернуть, подаст сигнал, когда пора закрывать " +
            "инкубатор, и сам подставит минуты в замер.",
    ),
    Hatch(
        title = "Вывод и итоги",
        body = "Важные даты закладки — овоскопирование и вывод — можно добавить в " +
            "календарь телефона. А когда птенцы вылупятся, приложение поздравит и " +
            "подведёт итог: вывод, расходы на яйца и электричество, прибыль. Птенцов " +
            "можно перенести в «Моё хозяйство».",
    ),
}

/**
 * @param revisit открыто из «О приложении», а не первым запуском: вместо «Пропустить»
 *   в углу стоит крестик, последняя кнопка читается «Понятно», и закрытие возвращает
 *   назад, а не на главный экран.
 * @param onFinish инструкцию закрыли; `true` — дочитали до конца, `false` — пропустили.
 */
@Composable
fun GuideScreen(
    revisit: Boolean,
    onFinish: (completed: Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val pages = GuidePage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    // «Назад» листает на страницу раньше, а не выкидывает из инструкции: на первом
    // запуске за ней ничего нет, и системный жест закрывал бы приложение с первой же
    // страницы. На первой странице жест не перехватывается и ведёт себя как обычно.
    BackHandler(enabled = pagerState.currentPage > 0) {
        // От `targetPage`, а не `currentPage`: второе нажатие во время анимации первого
        // иначе прочло бы ещё не сменившуюся страницу и вернуло бы на ту же самую.
        scope.launch {
            pagerState.animateScrollToPage((pagerState.targetPage - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        TopRow(
            step = pagerState.currentPage + 1,
            total = pages.size,
            revisit = revisit,
            showSkip = !lastPage,
            onClose = { onFinish(false) },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Соседняя страница собрана заранее: демонстрация листания начинает свою
            // анимацию с задержкой, и подготовленная страница выезжает уже нарисованной.
            beyondViewportPageCount = 1,
        ) { page ->
            GuidePageContent(
                page = pages[page],
                index = page,
                pagerState = pagerState,
            )
        }

        BottomBar(
            pagerState = pagerState,
            label = when {
                !lastPage -> "Далее"
                revisit -> "Понятно"
                else -> "Начать"
            },
            onNext = {
                if (lastPage) onFinish(true)
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
        )
    }
}

/**
 * Счётчик шагов слева и выход справа.
 *
 * «Пропустить» — текстом, а не крестиком: крестик на первом запуске читается как
 * «закрыть приложение». В повторном просмотре наоборот — крестик, потому что там
 * пропускать нечего, есть только «вернуться». На последней странице «Пропустить»
 * гаснет: пропускать уже нечего, а кнопка «Начать» стоит внизу.
 */
@Composable
private fun TopRow(
    step: Int,
    total: Int,
    revisit: Boolean,
    showSkip: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 12.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ШАГ $step ИЗ $total",
            style = DesignType.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (revisit) {
            SheetCircleButton(onClick = onClose, contentDescription = "Закрыть")
        } else {
            AnimatedVisibility(
                visible = showSkip,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(160)),
            ) {
                TextButton(onClick = onClose) {
                    Text(
                        text = "Пропустить",
                        style = DesignType.ToggleLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Одна страница: иллюстрация, заголовок, текст.
 *
 * Иллюстрация едет медленнее страницы, текст — почти вместе с ней: разная скорость
 * слоёв и есть то, что отличает пролистывание от переключения слайдов. Доля смещения
 * читается внутри `graphicsLayer`, а не в композиции, — иначе каждый кадр свайпа
 * перерисовывал бы весь экран.
 */
@Composable
private fun GuidePageContent(page: GuidePage, index: Int, pagerState: PagerState) {
    // Анимации сцены запускаются, когда её страница встала на место, а не когда она
    // впервые собрана: собранная заранее (см. `beyondViewportPageCount`) она проиграла
    // бы всё за кадром. Первая страница «на месте» с самого первого кадра, и без
    // `started` её вход никто бы не увидел: `animateFloatAsState` не анимирует значение,
    // с которого начал. Один кадр «ещё нет» — и вход играет и у неё.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val active = started && pagerState.settledPage == index

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Слои со смещением параллакса иначе высовывались бы из своей страницы в
            // соседнюю: край текста следующей страницы был виден у правого края этой.
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.pageParallax(pagerState, index, factor = 0.35f)) {
            when (page) {
                GuidePage.Welcome -> WelcomeScene(active)
                GuidePage.Incubator -> IncubatorScene(active)
                GuidePage.Batch -> BatchScene(active)
                GuidePage.Tabs -> TabsScene(active)
                GuidePage.Daily -> DailyScene(active)
                GuidePage.Qr -> QrScene(active)
                GuidePage.Timer -> TimerScene(active)
                GuidePage.Hatch -> HatchScene(active)
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.pageParallax(pagerState, index, factor = 0.12f)) {
            Text(
                text = page.title,
                style = DesignType.HeaderTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.body,
                style = GuideBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Параллакс слоя страницы: чем больше [factor], тем сильнее слой отстаёт от пейджера.
 * Заодно слой слегка гаснет, уходя за край, — так соседние страницы не сливаются в одну
 * ленту на середине свайпа.
 */
private fun Modifier.pageParallax(pagerState: PagerState, page: Int, factor: Float): Modifier =
    graphicsLayer {
        val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        translationX = offset * size.width * factor
        alpha = 1f - 0.4f * abs(offset).coerceIn(0f, 1f)
    }

/**
 * Точки страниц и главная кнопка.
 *
 * Точка текущей страницы вытягивается в чёрточку, а не просто темнеет: ширина видна
 * боковым зрением, цвет — нет. Кнопка та же, что сохраняет формы, — 52 dp, акцент,
 * скругление 16, — чтобы первая кнопка, которую человек нажимает в приложении,
 * выглядела как все следующие.
 */
@Composable
private fun BottomBar(pagerState: PagerState, label: String, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pagerState.pageCount) { index ->
                val selected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (selected) 24.dp else 8.dp,
                    animationSpec = tween(220),
                    label = "dotWidth",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(
                            if (selected) DesignPalette.Accent else DesignPalette.CardBorder
                        ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Accent,
                contentColor = DesignPalette.OnAccent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Crossfade(targetState = label, animationSpec = tween(160), label = "nextLabel") {
                Text(text = it, style = DesignType.ButtonLabel)
            }
        }
    }
}
