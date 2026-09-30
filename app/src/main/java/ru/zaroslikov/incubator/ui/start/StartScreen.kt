package ru.zaroslikov.incubator.ui.start

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ads.AdBannerAfter
import ru.zaroslikov.incubator.ads.rememberBannerAdHosts
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.rememberSheetDraft
import ru.zaroslikov.incubator.ui.incubator.AddIncubatorSheet
import ru.zaroslikov.incubator.ui.qr.IncubatorQrSheet
import ru.zaroslikov.incubator.ui.incubator.CapacityBlock
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.shortDate
import java.util.Date
import kotlinx.coroutines.launch


object StartDestination : NavigationDestination {
    override val route = "Start"
    override val titleRes = R.string.app_name
}

/** Отступ по краям экрана из макета. */
private val ScreenPadding = 20.dp

/** Запас снизу под плавающую кнопку — в макете под неё оставлено 112. */
private val FabReserve = 112.dp

/**
 * За сколько переключатель вкладок раздвигается и схлопывается.
 *
 * Ровно столько же, сколько строка фильтров на экране инкубатора (`ChipsSlideMillis`):
 * это одно и то же движение — полоска над списком уходит и приходит, — и разная его
 * длительность на двух экранах читалась бы как разная скорость приложения.
 */
private const val TabsSlideMillis = 180


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    navigateToIncubator: (Long) -> Unit,
    navigateToProfile: () -> Unit,
    navigateToAnalytics: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToAbout: () -> Unit,
    /** Открыть сканер QR-кода инкубатора — значок в шапке рядом с меню. */
    navigateToScanner: () -> Unit = {},
    /**
     * Открыть форму инкубатора сразу — так экран встречает того, кто пришёл сюда с
     * инструкции первого запуска: без устройства работать не с чем. Обновившийся
     * приходит с той же инструкции, но с этим `false`: его инкубаторы уже здесь.
     */
    openAddOnStart: Boolean = false,
    viewModel: StartScreenViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    // Владельцы рекламы живут с экраном, а не с элементом списка: см. BannerAdHosts.
    val adHosts = rememberBannerAdHosts()
    var showAddSheet by rememberSaveable { mutableStateOf(openAddOnStart) }
    // Черновик формы инкубатора живёт в композиции экрана, а не шторки: свёрнутую
    // шторку открывают тем же вводом, а уход со стартового экрана его отменяет.
    val addIncubatorDraft = rememberSheetDraft()
    // Ноль в `pendingDeleteId` — «никого не удаляем».
    var pendingDeleteId by rememberSaveable { mutableLongStateOf(0L) }
    // Ноль — «ни у кого не спрашиваем про архив»; см. `requestArchive` ниже.
    var pendingArchiveId by rememberSaveable { mutableLongStateOf(0L) }
    // «QR-код» из меню карточки: шторка с кодом этого инкубатора. Ноль — закрыта.
    var qrIncubatorId by rememberSaveable { mutableLongStateOf(0L) }

    // Архив инкубатора прерывает его идущие закладки, поэтому спрашивает — но только
    // когда есть что прерывать: у инкубатора без идущих закладок вопрос был бы о
    // последствиях, которых не будет, и приучал бы отвечать «да» не читая.
    val requestArchive: (IncubatorCardUi) -> Unit = { card ->
        if (card.activeBatches > 0) pendingArchiveId = card.incubator.id
        else send(StartIntent.SetIncubatorHidden(card.incubator, true))
    }

    // Вторая вкладка появляется, только когда в архиве кто-то есть: переключатель на две
    // страницы, одна из которых всегда пуста, — это указатель, показывающий в никуда.
    val hasArchive = uiState.archivedCards.isNotEmpty()
    val pagerState = rememberPagerState(pageCount = { if (hasArchive) 2 else 1 })
    val scope = rememberCoroutineScope()
    // Вернули последний инкубатор из архива, стоя в нём же, — вкладка исчезает вместе с
    // ним, и экран должен сам вернуться к списку. То же правило, что и у фильтра закладок
    // внутри инкубатора: выбор, под которым кончилось содержимое, откатывается к «Все».
    LaunchedEffect(hasArchive) {
        if (!hasArchive && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = MaterialTheme.colorScheme.background,
        // Нижний отступ системной панели уже внесён `contentPadding` корневого
        // `Scaffold`; свой этот считал бы его второй раз и поднимал «+ Инкубатор» на
        // высоту панели выше, чем стоят кнопки экрана инкубатора и кольцо таймера.
        // Верхний оставлен как был: заголовок стоит там, где стоял.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = DesignPalette.Accent,
                contentColor = DesignPalette.OnAccent,
                icon = { Icon(Icons.Filled.Add, "Добавить") },
                text = { Text(text = "Инкубатор") },
                // `Scaffold` ставит кнопку в 16 dp от краёв; ещё 4 — чтобы она стояла на
                // тех же 20 dp, что кнопки экрана инкубатора и кольцо таймера
                // проветривания слева (`AiringTimerFab`), с которым она в одной линии.
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Шапка и переключатель не прокручиваются — прокручиваются страницы
            // под ними, ровно как на экране инкубатора: «таблетка» указывает, где ты
            // сейчас, и указатель, уезжающий вместе со списком, указывать перестаёт.
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                Spacer(Modifier.height(8.dp))
                ScreenHeader(
                    onProfile = navigateToProfile,
                    onAnalytics = navigateToAnalytics,
                    onScan = navigateToScanner,
                    onSettings = navigateToSettings,
                    onAbout = navigateToAbout,
                )
                Spacer(Modifier.height(24.dp))
                // Первый убранный в архив инкубатор заводит вторую вкладку, последний
                // возвращённый её уносит — и то и другое случается ровно в тот момент,
                // когда смотришь на список. Переключатель, появляющийся рывком, читается
                // как подскочивший экран, поэтому он раздвигается и схлопывается по
                // вертикали за те же 180 мс, что и строка фильтров внутри инкубатора,
                // а список под ним доезжает следом, а не прыгает.
                AnimatedVisibility(
                    visible = hasArchive,
                    enter = expandVertically(tween(TabsSlideMillis)) +
                            fadeIn(tween(TabsSlideMillis)),
                    exit = shrinkVertically(tween(TabsSlideMillis)) +
                            fadeOut(tween(TabsSlideMillis)),
                ) {
                    // Отступ под переключателем уезжает вместе с ним: оставшись снаружи,
                    // он свернул бы не весь блок, а только его половину.
                    Column {
                        SlidingTabSwitcher(
                            tabs = StartTabs,
                            position = {
                                pagerState.currentPage + pagerState.currentPageOffsetFraction
                            },
                            onSelect = { page ->
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp,
            ) { page ->
                val cards = if (page == 0) uiState.cards else uiState.archivedCards
                LazyColumn(
                    // Именно `fillMaxSize`, а не `fillMaxWidth`: страница пейджера
                    // центрирует содержимое, которое ниже её самой, и короткий список
                    // повисал в середине экрана с одинаковыми полями сверху и снизу.
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        bottom = FabReserve,
                    ),
                ) {
                    if (uiState.loading) {
                        // База ещё не ответила. Приветствие здесь врало бы: список
                        // пуст не потому, что инкубаторов нет, а потому, что их ещё
                        // не прочитали, — а «Добро пожаловать!» человеку с пятью
                        // инкубаторами читается как потерянные данные.
                        item { LoadingBox(Modifier.fillParentMaxSize()) }
                    } else if (cards.isEmpty()) {
                        // Пустой бывает только первая страница: вторая появляется вместе
                        // с тем, что в ней лежит. Пусто, но инкубаторы есть — они все в
                        // архиве, и «Добро пожаловать!» тут было бы враньём: добавлять
                        // ничего не нужно, нужно перейти на соседнюю вкладку.
                        item { if (hasArchive) AllArchivedNote() else EmptyState() }
                    } else {
                        itemsIndexed(
                            items = cards,
                            key = { _, card -> card.incubator.id },
                        ) { index, card ->
                            IncubatorCard(
                                card = card,
                                onClick = { navigateToIncubator(card.incubator.id) },
                                onArchive = { requestArchive(card) },
                                onUnarchive = {
                                    send(StartIntent.SetIncubatorHidden(card.incubator, false))
                                },
                                onDelete = { pendingDeleteId = card.incubator.id },
                                onShowQr = {
                                    Analytics.report(Events.QR_SHOWN)
                                    qrIncubatorId = card.incubator.id
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
                            // Реклама — ещё одна карточка списка: сразу под первой, дальше
                            // через каждые три. В конце её никто не видит (у хозяйства с
                            // десятком инкубаторов конец списка лежит за нижним краем
                            // экрана), а одна-единственная сверху пропадает из виду, стоит
                            // пролистать. Какие места рекламные, считает `adSlotAfter`.
                            // Только на первой странице: в архиве смотрят на выведенные из
                            // работы устройства, и реклама среди них была бы единственным,
                            // что «в работе». Под пустым списком её тоже нет — приветствию
                            // она не сосед.
                            if (page == 0) {
                                AdBannerAfter(
                                    index = index,
                                    hosts = adHosts,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Инкубатор ищем в списках заново: пока висит вопрос, поток мог его обновить, и
        // удалять нужно то, что в базе сейчас. В архиве тоже — удаляют и оттуда.
        (uiState.cards + uiState.archivedCards)
            .firstOrNull { it.incubator.id == pendingDeleteId }?.let { card ->
                ConfirmDeleteIncubatorDialog(
                    name = card.incubator.name,
                    batches = card.batches,
                    onDismiss = { pendingDeleteId = 0L },
                    onConfirm = {
                        send(StartIntent.DeleteIncubator(card.incubator))
                        Analytics.report(Events.INCUBATOR_DELETED)
                        pendingDeleteId = 0L
                    },
                )
            }

        // Тот же приём, что и у удаления: карточку ищем в списках заново, потому что
        // пока висит вопрос, поток мог пересчитать и число идущих закладок.
        (uiState.cards + uiState.archivedCards)
            .firstOrNull { it.incubator.id == pendingArchiveId }?.let { card ->
                ConfirmArchiveIncubatorDialog(
                    name = card.incubator.name,
                    activeBatches = card.activeBatches,
                    onDismiss = { pendingArchiveId = 0L },
                    onConfirm = {
                        send(StartIntent.SetIncubatorHidden(card.incubator, true))
                        Analytics.report(Events.INCUBATOR_ARCHIVED)
                        pendingArchiveId = 0L
                    },
                )
            }

        if (showAddSheet) {
            AddIncubatorSheet(
                incubatorId = 0,
                draft = addIncubatorDraft,
                onDismiss = { showAddSheet = false },
                onSaved = { id ->
                    showAddSheet = false
                    Analytics.report(Events.INCUBATOR_CREATED)
                    navigateToIncubator(id)
                },
            )
        }

        // QR-код инкубатора — та же шторка, что открывается из шапки инкубатора; отсюда,
        // чтобы наклейки на все приборы печатались с одного экрана, не заходя в каждый.
        if (qrIncubatorId != 0L) {
            IncubatorQrSheet(
                incubatorId = qrIncubatorId,
                onDismiss = { qrIncubatorId = 0L },
            )
        }

    }
}

/**
 * Шапка из макета: крупный заголовок экрана и меню приложения справа.
 *
 * Ни иконки, ни меню в макете нет — но экрану оно нужно, раз TopAppBar убран, а
 * главный экран остаётся единственным местом, откуда виден весь остальной интерфейс.
 * Прежнего переключателя архива в шапке нет: убранные инкубаторы открываются строкой
 * «Архив (N)» под самим списком, там же, где их и ищут.
 *
 * Прежняя иконка «инфо» с нижней шторкой («Инкубатор v1.00» и кнопка в группу) отсюда
 * ушла: ровно это, и подробнее, теперь показывает «О приложении», а две кнопки в одной
 * шапке, ведущие к одному и тому же, — вопрос «а чем они отличаются» на пустом месте.
 * Событие AppMetrica «Информация» сохранено за тем же разделом, чтобы старая статистика
 * оставалась сравнимой.
 */
@Composable
private fun ScreenHeader(
    onProfile: () -> Unit,
    onAnalytics: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Инкубаторы",
            style = DesignType.ScreenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Сканер — рядом с меню, а не внутри него: наклейку на приборе сканируют каждый
        // день, и два нажатия вместо одного здесь были бы на каждом обходе птичника.
        IconButton(onClick = onScan) {
            Icon(
                painter = painterResource(R.drawable.ic_qr_scanner_design),
                contentDescription = "Сканировать QR-код инкубатора",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Меню приложения",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = DesignPalette.Surface,
                shape = RoundedCornerShape(16.dp),
            ) {
                // Профиль — первым и за чертой: единственный пункт меню про человека, а
                // не про хозяйство. Регистрация необязательна, и пункт лишь предлагает её.
                AppMenuItem("Профиль", { MenuIcon(Icons.Filled.AccountCircle) }) {
                    expanded = false
                    onProfile()
                }
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                AppMenuItem("Аналитика", { MenuVectorIcon(R.drawable.ic_chart_design) }) {
                    expanded = false
                    onAnalytics()
                }
                AppMenuItem("Настройки", { MenuIcon(Icons.Filled.Settings) }) {
                    expanded = false
                    onSettings()
                }
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                AppMenuItem("О приложении", { MenuIcon(Icons.Filled.Info) }) {
                    expanded = false
                    onAbout()
                }
            }
        }
    }
}

/** Значок пункта меню из material-icons-core. */
@Composable
private fun MenuIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** То же из ресурсов: значка диаграммы в material-icons-core нет, а свой в проекте есть. */
@Composable
private fun MenuVectorIcon(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun AppMenuItem(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = icon,
        onClick = onClick,
    )
}

/**
 * Карточка инкубатора: агрегаты по всем его активным закладкам.
 *
 * Троеточие справа открывает меню карточки — архив и удаление, см. [IncubatorCardMenu].
 * Оно стоит на месте шеврона из макета, а не рядом с ним: карточка нажимается целиком,
 * так что стрелка только повторяла бы то, что и так понятно, а две иконки в одном углу
 * заставляли бы целиться. Ровно так же устроена карточка закладки внутри инкубатора —
 * там шеврона нет и не было.
 */
@Composable
fun IncubatorCard(
    card: IncubatorCardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    /** «QR-код» в меню карточки — шторка с кодом этого инкубатора. */
    onShowQr: () -> Unit = {},
) {
    val incubator = card.incubator
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp), onClick = onClick
    ) {
        // Отступ справа урезан: у кнопки меню свои 12 dp вокруг иконки, и с полными
        // двадцатью она отходила бы от края дальше остального содержимого карточки.
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .weight(1f)
                        // Правый край названия остаётся на месте: меню шире шеврона,
                        // и без этого длинное имя обрывалось бы раньше, чем в макете.
                        .padding(end = 12.dp)
                ) {
                    TruncatedText(
                        text = incubator.name,
                        style = DesignType.CardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val model = modelLine(incubator.brand, incubator.model)
                    if (model.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        TruncatedText(
                            text = model,
                            style = DesignType.Mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IncubatorCardMenu(
                    isArchived = incubator.hidden,
                    onArchive = onArchive,
                    onUnarchive = onUnarchive,
                    onDelete = onDelete,
                    onShowQr = onShowQr,
                    modifier = Modifier.align(Alignment.Top),
                )
            }

            // Всё, что ниже строки заголовка, добирает те же 12 dp: у полосы
            // вместимости и подписей своего меню справа нет, и обрываться раньше
            // правого края карточки им незачем.
            Column(Modifier.padding(end = 12.dp)) {
                if (card.species.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SpeciesChips(card.species)
                }

                Spacer(Modifier.height(16.dp))
                CapacityBlock(
                    eggs = card.eggs,
                    capacity = incubator.capacity,
                    archived = incubator.hidden,
                )

                val hatch = card.nearestHatchMillis?.let(::formatHatchDate)
                if (hatch != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Ближайший вывод — ")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Medium,
                                    color = DesignPalette.DateEmphasis,
                                )
                            ) { append(hatch) }
                        },
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Троеточие карточки инкубатора и его меню: архив и удаление.
 *
 * Пунктов всего два, и «Убрать в архив» здесь значит ровно то, что написано, — в
 * отличие от меню закладки, где тот же пункт у идущей закладки означает завершение.
 * Завершать у устройства нечего: инкубатор не кончается, он либо в работе, либо
 * убран с глаз, и второе отменяется тем же пунктом.
 *
 * Удаление отсюда сразу не удаляет — вопрос задаёт хозяин экрана: `ON DELETE CASCADE`
 * унесёт все закладки инкубатора вместе с их днями, замерами, овоскопированиями и
 * напоминаниями, и вернуть их нечем.
 */
@Composable
private fun IncubatorCardMenu(
    isArchived: Boolean,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Действия с инкубатором",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DesignPalette.Surface,
            shape = RoundedCornerShape(16.dp),
        ) {
            // Первым, выше архива: единственный пункт, который ничего не меняет, и
            // единственный, который нужен и у архивного инкубатора — наклейку печатают
            // заранее, а прибор возвращают в работу и позже.
            CardMenuItem(
                text = "QR-код",
                icon = R.drawable.ic_qr_code_design,
            ) {
                expanded = false
                onShowQr()
            }
            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
            if (isArchived) {
                CardMenuItem(
                    text = "Вернуть в список",
                    icon = R.drawable.baseline_unarchive_24,
                ) {
                    expanded = false
                    onUnarchive()
                }
            } else {
                CardMenuItem(
                    text = "Убрать в архив",
                    icon = R.drawable.baseline_archive_24,
                ) {
                    expanded = false
                    onArchive()
                }
            }

            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
            CardMenuItem(
                text = "Удалить",
                icon = R.drawable.baseline_delete_24,
                tint = DesignPalette.Expense,
            ) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun CardMenuItem(
    text: String,
    @DrawableRes icon: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = text, style = DesignType.ListItemTitle, color = tint) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

/**
 * Две вкладки переключателя: инкубаторы в работе и убранные в архив.
 *
 * Подписи — прилагательные, «Активные» и «Архивные», а не «Инкубаторы» и «Архив»:
 * заголовок экрана уже сказал, что речь об инкубаторах, и повторять его в подписи
 * вкладки значит тратить половину её ширины на слово, которое ничего не разделяет.
 * Обе половины при этом названы одинаково устроенно — по состоянию устройства.
 *
 * Чисел рядом нет, как и у вкладок инкубатора: счётчик у подписи меняется под пальцем
 * (убрали инкубатор в архив — и обе цифры разом поехали), а «таблетка» должна отвечать
 * на вопрос «где я», а не показывать сводку. Сколько лежит на каждой стороне, видно по
 * самому списку — ради этого на вкладку и переходят.
 *
 * Значок архива — тот же `baseline_archive_24`, что у плашки архивного инкубатора и у
 * чипа «Архив» в списке закладок: одна картинка на одно понятие во всём приложении.
 */
private val StartTabs = listOf(
    SlidingTab("Активные", R.drawable.ic_egg_design_16),
    SlidingTab("Архивные", R.drawable.baseline_archive_24),
)

/**
 * Вопрос перед архивом — потому что архив инкубатора прерывает его идущие закладки.
 *
 * Раньше «Убрать в архив» ничего не спрашивал, и был прав: он убирал карточку из списка,
 * а это отменяется тем же пунктом меню. Теперь то же нажатие пишет закладкам «Не
 * завершено» с нулевым выводом — итог, который сам собой не отменяется, — и молчать об
 * этом нельзя. Вопрос показывается только когда есть что прерывать (`requestArchive`).
 *
 * Названо всё, что произойдёт, и в том числе цифра: сколько закладок будет прервано,
 * с какой причиной их потом найдут на карточке и что возврат из архива их обратно в
 * работу не вернёт. Последнее — самое неочевидное и потому стоит отдельным абзацем:
 * «в архив» звучит как обратимое, и обратимо оно ровно наполовину.
 *
 * Кнопка подтверждения красная, как у удаления: прерванная закладка — это ноль вывода
 * и вся её стоимость в расход, а не перестановка карточки.
 */
@Composable
private fun ConfirmArchiveIncubatorDialog(
    name: String,
    activeBatches: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Убрать в архив?", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = "«$name» уйдёт в архив, а " +
                    plural(
                        activeBatches,
                        "идущая закладка будет завершена",
                        "идущие закладки будут завершены",
                        "идущих закладок будут завершены",
                    ) +
                    " досрочно, с причиной «${StartScreenViewModel.ARCHIVE_END_REASON}». " +
                    "Вывод станет нулевым, напоминания замолчат.\n\n" +
                    "Инкубатор из архива вернуть можно, а закладки — только по одной, " +
                    "каждую её собственным «Вернуть в инкубацию».",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Убрать в архив", color = DesignPalette.Expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/**
 * Тот же вопрос, что и об удалении закладки, только цена выше: с инкубатором уходят
 * все его закладки — по внешним ключам с `ON DELETE CASCADE`, вместе с режимом по дням,
 * замерами, овоскопированиями и напоминаниями.
 *
 * Число закладок названо прямо в вопросе: без него «Удалить инкубатор?» звучит как
 * «убрать карточку», а это единственное действие в приложении, которое одним нажатием
 * уносит годы записей.
 */
@Composable
private fun ConfirmDeleteIncubatorDialog(
    name: String,
    batches: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Удалить инкубатор?", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = if (batches > 0) {
                    "«$name» исчезнет вместе с " +
                        plural(batches, "закладкой", "закладками", "закладками") +
                        ", их режимом по дням, замерами и напоминаниями. " +
                        "Вернуть это будет нельзя."
                } else {
                    "«$name» исчезнет из списка. Вернуть его будет нельзя."
                },
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Удалить", color = DesignPalette.Expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/** Круглые чипы видов, налезающие друг на друга — как в макете. */
@Composable
private fun SpeciesChips(species: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        species.forEach { bird ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(speciesChipColor(bird)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = speciesEmoji(bird), fontSize = 15.sp)
            }
        }
    }
}

/** Пустого состояния в макете нет — текст свой. */
@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Добро пожаловать!",
            style = DesignType.CardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Добавьте инкубатор, а внутри него — закладки яиц.",
            style = DesignType.Placeholder,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Список пуст, но не потому, что инкубаторов нет: они все убраны в архив.
 *
 * Текст называет вкладку, а не просто сообщает факт: пустая страница с «таблеткой» над
 * ней и так спрашивает «а где всё», и ответ должен стоять там же, где вопрос.
 */
@Composable
private fun AllArchivedNote() {
    Text(
        text = "Все инкубаторы в архиве — они на соседней вкладке.",
        style = DesignType.Placeholder,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
    )
}

/**
 * «Бренд Модель» одной строкой; у инкубатора с миграции они пустые. Тогда строка пуста —
 * заглушки вроде «Модель не указана» тут не место: пустое поле не новость, и вместо неё
 * вызывающий просто не рисует строку.
 */
fun modelLine(brand: String, model: String): String =
    listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")

/** Эмодзи вида — как в макете, вместо прежних PNG-картинок птиц. */
fun speciesEmoji(bird: String): String = when (bird) {
    "Курицы" -> "🐔"
    "Гуси" -> "🪿"
    "Перепела" -> "🐦"
    "Индюки" -> "🦃"
    "Утки" -> "🦆"
    else -> "🥚"
}

/**
 * Заливка круглого чипа вида. Композабл, потому что [DesignPalette] зависит от темы:
 * вне композиции текущего набора цветов просто нет.
 */
@Composable
@ReadOnlyComposable
fun speciesChipColor(bird: String): Color = when (bird) {
    "Курицы" -> DesignPalette.ChipChicken
    "Гуси" -> DesignPalette.ChipGoose
    "Перепела" -> DesignPalette.ChipQuail
    "Индюки" -> DesignPalette.ChipTurkey
    "Утки" -> DesignPalette.ChipDuck
    else -> DesignPalette.ChipQuail
}

fun formatHatchDate(millis: Long): String = shortDate(Date(millis))
