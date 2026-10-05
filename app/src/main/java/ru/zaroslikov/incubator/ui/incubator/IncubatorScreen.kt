package ru.zaroslikov.incubator.ui.incubator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.AdBannerAfter
import ru.zaroslikov.incubator.ads.BannerAdHosts
import ru.zaroslikov.incubator.ads.rememberBannerAdHost
import ru.zaroslikov.incubator.ads.rememberBannerAdHosts
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.domain.stats.combined
import ru.zaroslikov.incubator.ui.batch.HatchCelebrationDialog
import ru.zaroslikov.incubator.ui.batch.HatchSummariesSaver
import ru.zaroslikov.incubator.calendar.CalendarOffer
import ru.zaroslikov.incubator.farm.FarmApp
import ru.zaroslikov.incubator.farm.FarmChicks
import ru.zaroslikov.incubator.farm.FarmStatus
import ru.zaroslikov.incubator.farm.farmChicksOf
import ru.zaroslikov.incubator.ui.todayText
import ru.zaroslikov.incubator.ui.batch.AddToCalendarDialog
import ru.zaroslikov.incubator.ui.batch.CalendarOfferSaver
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.exportable
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.transfer.ScheduleTransferController
import ru.zaroslikov.incubator.transfer.ScheduleTransferState
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.batch.AddBatchSheet
import ru.zaroslikov.incubator.ui.batch.BatchDetailSheet
import ru.zaroslikov.incubator.ui.batch.airingTimerActive
import ru.zaroslikov.incubator.ui.batch.FinishBatchHost
import ru.zaroslikov.incubator.ui.batch.FinishGroupHost
import ru.zaroslikov.incubator.ui.batch.StatusChip
import ru.zaroslikov.incubator.ui.batch.batchStartMoment
import ru.zaroslikov.incubator.ui.batch.incubationDay
import ru.zaroslikov.incubator.ui.qr.IncubatorQrSheet
import ru.zaroslikov.incubator.design.components.FieldHeight
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.SheetDropdownField
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.EmptyState
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.design.components.StatusBarAppearance
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.rememberSheetDraft
import ru.zaroslikov.incubator.design.components.statValue
import ru.zaroslikov.incubator.design.components.withoutTop
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.ui.start.modelLine
import ru.zaroslikov.incubator.ui.start.SpeciesGlyph
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.shortDate
import ru.zaroslikov.incubator.ui.today
import java.util.Date


object IncubatorDestination : NavigationDestination {
    override val route = "Incubator"
    const val itemIdArg = "incubatorId"

    /**
     * Закладка, чью шторку надо раскрыть поверх экрана, — необязательный аргумент.
     *
     * Нужен ровно одному входу: нажатию по напоминанию. Уведомление обязано привести
     * не «в приложение», а к той закладке, о которой напомнило, а шторка своего
     * маршрута не имеет — она часть экрана инкубатора. Поэтому адресуется она через
     * него, запросом, а не отдельным пунктом назначения: обычный переход в инкубатор
     * (`routeFor(id)`) остаётся прежним, аргумент по умолчанию ноль — «ничего не
     * раскрывать».
     */
    const val batchIdArg = "batchId"

    /**
     * Раскрыть «Замеры за сегодня» по инкубатору сразу при входе — тоже необязательный.
     *
     * Его передаёт один вход: приход по QR-коду инкубатора — с камеры телефона или из
     * сканера внутри приложения. Наклейку на приборе сканируют, чтобы записать показание
     * термометра, и открыть экран без шторки значило бы заставить нажимать «Внести замер»
     * после каждого сканирования. Тем же приёмом, что и [batchIdArg]: шторка — часть
     * экрана, и адресуется через него.
     */
    const val measureArg = "measure"
    val routeWithArgs =
        "$route/{$itemIdArg}?$batchIdArg={$batchIdArg}&$measureArg={$measureArg}"

    fun routeFor(incubatorId: Long, batchId: Long = 0L, measure: Boolean = false): String =
        "$route/$incubatorId?$batchIdArg=$batchId&$measureArg=$measure"
}

internal val ScreenPadding = 20.dp

/** Насколько кремовая «шторка» с вкладками наезжает на зелёную шапку — из макета (200 − 183). */
private val SheetOverlap = 17.dp

/** Диаметр мини-кнопки «+», всплывающей вместо уехавшей за край пунктирной. */
private val FabDiameter = 48.dp

/**
 * Насколько быстро ряд фильтров уезжает вверх и возвращается.
 *
 * Столько же сворачивается и строка цифр в шапке: движение одно — полоска над списком
 * уходит и приходит, — и разная его длительность на одном экране читалась бы как
 * разная скорость приложения.
 */
private const val ChipsSlideMillis = 180

/** Порог в пикселях, ниже которого движение пальца не считается листанием. */
private const val ChipsScrollThreshold = 3f

/** Проявление списка при смене фильтра и при входе в архив или выходе из него. */
private const val ListSwitchMillis = 260

/** Откуда въезжает список: на столько он сдвинут в начале анимации. */
private val ArchiveSlideDistance = 48.dp

/** Насколько ниже начинает подъём список после смены статуса: короче въезда — это не смена места. */
private val FilterRiseDistance = 16.dp

/** Что сменилось в списке закладок — от этого зависит, откуда он появляется. */
private enum class ListSwitch { Filter, IntoArchive, OutOfArchive }

/** Её появление и исчезновение: короткое, чтобы кнопка не тянулась следом за пальцем. */
private const val FabFadeMillis = 160

/** Скругление верхних углов шторки, из-за которого по краям видно зелёный фон шапки. */
private val SheetCorner = 24.dp

/**
 * Ширина левого столбика карточки закладки — статус, кольцо, день. Под самый широкий
 * из статусов («Инкубация», «Завершено» — около 79 dp) с небольшим запасом на
 * трёхзначный день своего вида, чтобы текст справа начинался с одного места на всех
 * карточках. Крупный шрифт системы чип не раздвинет: он обрежется многоточием.
 */
private val RingColumnWidth = 84.dp

private enum class IncubatorTab(val title: String, @param:DrawableRes val icon: Int) {
    Batches("Закладки", R.drawable.ic_egg_design_16),
    Stats("Статистика", R.drawable.ic_chart_design),
    Finance("Финансы", R.drawable.ic_wallet_design),
}

/** Вкладки в том виде, в каком их принимает [SlidingTabSwitcher]; порядок — страницы пейджера. */
private val incubatorTabs = IncubatorTab.entries.map { SlidingTab(it.title, it.icon) }

/**
 * Один инкубатор — экран `IncubatorDetail` из макета
 * ([узел 5:547](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=5-547)).
 *
 * Тап по закладке — любой, не только идущей — открывает шторку [BatchDetailSheet]
 * (макет 14:4893), а не отдельный экран; расписание по дням — вторая её страница, за
 * свайпом вбок. Завершённая закладка открывается в той же шторке на просмотр.
 *
 * Зелёная шапка с показателями, поверх неё кремовая шторка с тремя вкладками —
 * «Закладки», «Статистика», «Финансы». Вкладки листаются свайпом.
 *
 * Два отступления от макета, оба намеренные:
 * третий показатель в шапке — «Выведено», а не «Прибыль». Прибыль теперь есть, её
 * считает [ru.zaroslikov.incubator.domain.stats.incubatorFinance], но все три денежные
 * графы необязательны, и у хозяйства, которое цен не вводит, шапка вечно показывала бы
 * «0 ₽» — то есть поломку. На вкладке «Финансы» тот же ноль объясняет себя карточкой,
 * а шапка объясниться не может. Второе: шестерёнка справа от «Все инкубаторы» в макете
 * отсутствует, но без неё некуда деться к редактированию инкубатора.
 */
@Composable
fun IncubatorScreen(
    navigateBack: () -> Unit,
    viewModel: IncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Закладка, чью шторку надо раскрыть сразу при открытии экрана, — приход по
     * нажатию на напоминание. Ноль (обычный случай) ничего не открывает.
     */
    openBatchId: Long = 0L,
    /**
     * Раскрыть шторку «Замеры за сегодня» сразу при открытии — приход по QR-коду
     * инкубатора. Архивному инкубатору шторка не открывается: замер там некому записать.
     */
    openMeasurements: Boolean = false,
    /** Открыть сканер QR-кода — кнопка в шторке «Замеры за сегодня». */
    navigateToScanner: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // По владельцу объявления на каждую вкладку, и все трое живут с экраном, а не со
    // страницей пейджера (см. BannerAdHost): страница при свайпе уходит из композиции, и
    // объявление заказывалось бы заново на каждое переключение. Один на всех тут не
    // годится — один `View` не может стоять в двух родителях, а во время свайпа обе
    // страницы скомпонованы разом. Лишних запросов это не создаёт: объявление
    // заказывается в момент, когда вкладку впервые открыли, а не когда завели владельца.
    // У «Закладок» мест несколько — по одному на каждые три карточки, отсюда набор.
    val batchesAdHosts = rememberBannerAdHosts()
    val statsAdHost = rememberBannerAdHost()
    val financeAdHost = rememberBannerAdHost()
    // `rememberSaveable`, как у остальных флагов экрана: поворот не должен закрывать
    // форму, а `SheetDraft` сохраняет себя ровно при смене конфигурации — и без этого
    // хранил бы черновик для шторки, которой после поворота нет.
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var showAddBatchSheet by rememberSaveable { mutableStateOf(false) }
    // «Внести замер» под списком: замер по всему инкубатору, по копии в каждую идущую
    // закладку. Шторка, как и остальные, и по той же причине — она стоит над списком.
    var showMeasurementSheet by rememberSaveable { mutableStateOf(false) }
    // «QR-код» из шапки: шторка с кодом, который печатают и клеят на прибор.
    var showQrSheet by rememberSaveable { mutableStateOf(false) }
    // Ноль — шторка закрыта; иначе закладка, которую она показывает (макет 14:4893).
    // `rememberSaveable`, а не `remember`: поворот экрана не должен закрывать открытую
    // закладку.
    var detailBatchId by rememberSaveable { mutableLongStateOf(0L) }
    // Три действия из меню карточки. Правка — та же шторка формы, что и создание;
    // архив — те же диалоги завершения, что и в шторке закладки; удаление сперва
    // спрашивает. Идентификатор, а не сама закладка: список на потоке и пересоздаёт
    // свои объекты, а `rememberSaveable` умеет хранить число, но не `Batch`.
    var editBatchId by rememberSaveable { mutableLongStateOf(0L) }
    var finishBatchId by rememberSaveable { mutableLongStateOf(0L) }
    // Партия из нескольких пород, чей срок вышел разом, — один диалог на все её закладки
    // (`FinishGroupHost`). Пустой массив — диалог закрыт. `LongArray`, потому что его
    // `rememberSaveable` кладёт в `Bundle` как есть.
    var finishGroupIds by rememberSaveable { mutableStateOf(LongArray(0)) }
    // Кто открыл диалоги завершения: пункт «Убрать в архив» — и тогда закладка тем же
    // сохранением уходит в архив, — или подсказка «Инкубация завершена», которой нужен
    // только итог. Диалоги одни и те же, разное у них лишь это.
    var finishHides by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableLongStateOf(0L) }
    // Карточка итога — `HatchCelebrationDialog`: поздравление с салютом за вывод или
    // «Инкубация прервана» без салюта за досрочную остановку. Пустой список — закрыто;
    // иначе сводки только что завершённых закладок (одна или партия пород).
    // `rememberSaveable` со своим `Saver`: поворот экрана не должен гасить карточку, а
    // сводка после записи больше ниоткуда не приедет.
    var celebration by rememberSaveable(stateSaver = HatchSummariesSaver) {
        mutableStateOf(emptyList<HatchSummary>())
    }
    // Одна точка на все пути завершения: сюда приходит сводка из шторки закладки, из
    // хоста диалогов меню карточки и из партии. Праздник — только с птенцами; прерванная
    // закладка и вывод «ноль» (у партии — ноль по всем породам) получают ту же карточку
    // без салюта, без события поздравления и без просьбы об оценке.
    val context = LocalContext.current
    val celebrate:(List<HatchSummary>) -> Unit = { summaries ->
        val hatched = summaries.sumOf { it.hatched }
        if (hatched == 0) {
            if (summaries.isNotEmpty()) celebration = summaries
        } else {
            Analytics.report(
                Events.HATCH_CELEBRATED,
                mapOf(
                    "Птенцов" to hatched,
                    "Закладок" to summaries.size,
                    "Вывод, %" to summaries.combined().rate,
                    "Хозяйство" to when (FarmApp.status(context)) {
                        FarmStatus.Ready -> "принимает"
                        FarmStatus.Outdated -> "старая версия"
                        FarmStatus.Absent -> "нет"
                    },
                ),
            )
            celebration = summaries
        }
    }
    // «Моё хозяйство» спрашивается на каждом возврате в приложение, а не раз на экран:
    // его ставят и обновляют из магазина, и вернувшийся оттуда должен сразу увидеть
    // «Добавить» — в поздравлении, не закрывая его, и в меню завершённой закладки.
    var farmCheck by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        farmCheck++
        onPauseOrDispose { }
    }
    val farmStatus = remember(farmCheck) { FarmApp.status(context) }
    // Открывает форму хозяйства; `false` — не вышло (его удалили между проверкой и
    // нажатием). Один путь для поздравления и для меню карточки, «Откуда» их различает.
    val sendToFarm: (FarmChicks, String) -> Boolean = { chicks, origin ->
        FarmApp.send(context, chicks).also { opened ->
            if (opened) {
                Analytics.report(
                    Events.FARM_CHICKS_SENT,
                    mapOf("Птенцов" to chicks.count, "Вид" to chicks.type, "Откуда" to origin),
                )
            }
        }
    }
    // «В «Моё хозяйство»» из меню карточки, когда стоящее хозяйство птенцов не принимает:
    // вопрос «обновить?» вместо ссылки, которую оно проигнорирует.
    var farmUpdateAsked by rememberSaveable { mutableStateOf(false) }
    // «Экспортировать» из меню карточки: сперва диалог «куда», потом системное окно.
    // Идентификатор, как и у остальных, — закладку ищем в списке заново в момент записи.
    var exportBatchId by rememberSaveable { mutableLongStateOf(0L) }
    // Важные даты только что заложенной закладки — вопрос «добавить в календарь?».
    // `rememberSaveable` со своим `Saver`, как и поздравление: поворот не должен молча
    // снимать вопрос, а даты после записи закладки больше ниоткуда не приедут.
    var calendarOffer by rememberSaveable(stateSaver = CalendarOfferSaver) {
        mutableStateOf<CalendarOffer?>(null)
    }
    // Итог экспорта — поле того же состояния экрана: его источник, контроллер из
    // `AppContainer`, входит в `combine` внутри ViewModel, и второй подписки ему не нужно.
    val scheduleTransfer = uiState.scheduleTransfer
    // Подсказку «Инкубация завершена» показываем один раз за заход на экран: ответив
    // «Позже», её незачем видеть до следующего открытия инкубатора. `rememberSaveable`,
    // чтобы поворот экрана не считался новым заходом.
    var finishPromptDismissed by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { IncubatorTab.entries.size })
    val scope = rememberCoroutineScope()

    // Приход по напоминанию: шторка нужной закладки раскрывается сама — и ровно один
    // раз. Флага мало было бы не заводить, если бы `LaunchedEffect` держался ключом:
    // ключ тут не меняется, зато поворот экрана пересоздаёт композицию целиком, эффект
    // запускается заново и возвращает шторку, которую только что закрыли. Флаг
    // `rememberSaveable`, как и `finishPromptDismissed` выше, и по той же причине —
    // поворот не должен считаться новым приходом по уведомлению.
    var reminderOpened by rememberSaveable { mutableStateOf(false) }

    // Черновики форм в шторках. Живут в композиции экрана, а не самих шторок: свёрнутую
    // вниз шторку открывают ровно тем, что в ней набрали, а уход с экрана инкубатора
    // ввод отменяет. Черновик закладки один на обе шторки формы — создание и правку:
    // ViewModel у них общая, и два черновика делили бы одно состояние пополам.
    val incubatorFormDraft = rememberSheetDraft()
    val batchFormDraft = rememberSheetDraft()
    val batchDetailDraft = rememberSheetDraft()
    val measurementDraft = rememberSheetDraft()
    LaunchedEffect(openBatchId, openMeasurements, uiState.loading) {
        if (reminderOpened) return@LaunchedEffect
        if (openBatchId != 0L) {
            reminderOpened = true
            detailBatchId = openBatchId
        } else if (openMeasurements) {
            // Приход по QR-коду: шторка замеров, и только когда база ответила — до
            // ответа неизвестно, архивный ли это инкубатор и есть ли он вообще, а
            // шторка над несуществующим инкубатором обещала бы записать замер в никуда.
            // Тот же флаг, что у напоминания: и здесь поворот — не новый приход.
            if (uiState.loading) return@LaunchedEffect
            reminderOpened = true
            if (!uiState.readOnly && uiState.incubator != null) showMeasurementSheet = true
        }
    }

    // Инкубатора нет: удалён, пока экран открыт, или QR-код напечатан с другого
    // телефона. Шапка с пустым названием над пустыми вкладками читалась бы как поломка,
    // а это обычный ответ, и у него есть свои слова.
    if (!uiState.loading && uiState.incubator == null) {
        MissingIncubator(navigateBack = navigateBack, contentPadding = contentPadding)
        return
    }

    if (showEditSheet) {
        AddIncubatorSheet(
            incubatorId = uiState.incubatorId,
            draft = incubatorFormDraft,
            onDismiss = { showEditSheet = false },
            onSaved = { showEditSheet = false },
        )
    }

    // Форма закладки — тоже шторка (макет 12:3555), поэтому её хозяин экран, а не навигация.
    if (showAddBatchSheet) {
        AddBatchSheet(
            incubatorId = uiState.incubatorId,
            draft = batchFormDraft,
            onDismiss = { showAddBatchSheet = false },
            onSaved = { batchIds, calendar ->
                showAddBatchSheet = false
                Analytics.report(Events.OPEN_ADD_BATCH)
                // Одна закладка открывается сразу; несколько — нет: какую из них, форма
                // не знает, а список уже показывает их все.
                detailBatchId = batchIds.singleOrNull() ?: 0L
                // Вопрос о календаре встаёт поверх открытой закладки: даты — её, и,
                // ответив, человек оказывается ровно там, куда шёл.
                calendarOffer = calendar
            },
        )
    }

    // Шторка закладки — макет 14:4893. Маршрута у неё нет, как и у форм выше.
    // В архивном инкубаторе она открывается на просмотр: замеры, овоскопирование,
    // правка режима по дням и завершение — это тоже изменения закладки.
    if (detailBatchId != 0L) {
        BatchDetailSheet(
            batchId = detailBatchId,
            draft = batchDetailDraft,
            onDismiss = { detailBatchId = 0L },
            readOnly = uiState.readOnly,
            onSummary = { celebrate(listOf(it)) },
        )
    }

    // Замер по инкубатору — шторка над списком: закрыв её, человек видит те же
    // карточки, и в каждой идущей замер уже есть. Архивному инкубатору не предлагается
    // вовсе, кнопка к ней там не рисуется.
    if (showMeasurementSheet) {
        IncubatorMeasurementSheet(
            incubatorId = uiState.incubatorId,
            draft = measurementDraft,
            onDismiss = { showMeasurementSheet = false },
            onScan = navigateToScanner,
        )
    }

    // QR-код инкубатора — шторка над экраном, как и остальные. Открывается из шапки, у
    // архивного инкубатора тоже: код — свойство прибора, а не его закладок, и наклейку
    // печатают заранее.
    if (showQrSheet) {
        IncubatorQrSheet(
            incubatorId = uiState.incubatorId,
            onDismiss = { showQrSheet = false },
        )
    }

    // «Редактировать» из меню карточки — та же форма, что и создание закладки, только
    // с идентификатором. Экран под ней остаётся: правка не повод уходить из списка.
    if (editBatchId != 0L) {
        AddBatchSheet(
            incubatorId = uiState.incubatorId,
            batchId = editBatchId,
            draft = batchFormDraft,
            onDismiss = { editBatchId = 0L },
            onSaved = { _, _ -> editBatchId = 0L },
        )
    }

    // «Убрать в архив» — те же два диалога, что и кнопка внизу «Обзора»: архив идущей
    // закладки это и есть её завершение, и спрашивают при нём то же самое. Отличие одно:
    // `hide` — пункт обещал архив, значит закладка попадает туда сразу, а не остаётся
    // завершённой карточкой в списке, которую надо убирать вторым таким же нажатием.
    if (finishBatchId != 0L) {
        FinishBatchHost(
            batchId = finishBatchId,
            onDismiss = { finishBatchId = 0L },
            onFinished = { summary ->
                finishBatchId = 0L
                summary?.let { celebrate(listOf(it)) }
            },
            hide = finishHides,
        )
    }

    if (finishGroupIds.isNotEmpty()) {
        FinishGroupHost(
            incubatorId = uiState.incubatorId,
            batchIds = finishGroupIds.toList(),
            onDismiss = { finishGroupIds = LongArray(0) },
            onFinished = { summaries ->
                finishGroupIds = LongArray(0)
                celebrate(summaries)
            },
        )
    }

    calendarOffer?.let { offer ->
        AddToCalendarDialog(offer = offer, onDone = { calendarOffer = null })
    }

    // Поздравление — диалог экрана, а не шторки: та к этому моменту закрыта. Его закрытие
    // после вывода с птенцами — единственная просьба оценить приложение; после салюта, а
    // не поверх него (`IncubatorIntent.CelebrationClosed`).
    if (celebration.isNotEmpty()) {
        val farm = remember(celebration) {
            val today = todayText()
            celebration.mapNotNull { farmChicksOf(it, today) }
        }
        HatchCelebrationDialog(
            summaries = celebration,
            farm = farm,
            farmStatus = farmStatus,
            onUpdateFarm = {
                if (FarmApp.openStorePage(context)) {
                    Analytics.report(Events.FARM_UPDATE_OPENED, mapOf("Откуда" to FARM_FROM_CELEBRATION))
                }
            },
            onSendToFarm = { chicks -> sendToFarm(chicks, FARM_FROM_CELEBRATION) },
            onDismiss = {
                val withChicks = celebration.any { it.hatched > 0 }
                celebration = emptyList()
                viewModel.onIntent(IncubatorIntent.CelebrationClosed(withChicks))
            },
        )
    }

    if (farmUpdateAsked) {
        FarmUpdateDialog(
            onDismiss = { farmUpdateAsked = false },
            onUpdate = {
                farmUpdateAsked = false
                if (FarmApp.openStorePage(context)) {
                    Analytics.report(Events.FARM_UPDATE_OPENED, mapOf("Откуда" to FARM_FROM_CARD))
                }
            },
        )
    }

    // «Инкубация завершена»: срок вышел, а итог никто не внёс — предлагаем внести его
    // прямо на входе. Считается в composition, а не в потоке ViewModel: ответ зависит от
    // текущего времени, а не только от того, что лежит в базе.
    //
    // Ничего не показываем, пока открыто что-то ещё: подсказка встречает вошедшего, а не
    // накрывает шторку, которую он только что открыл сам.
    //
    // В архивном инкубаторе её нет вовсе: подсказка зовёт внести итог, то есть завершить
    // закладку, а это ровно то изменение, которого архивный режим не допускает.
    val overlayOpen = showEditSheet || showAddBatchSheet || showMeasurementSheet || showQrSheet ||
            detailBatchId != 0L || editBatchId != 0L || finishBatchId != 0L || finishGroupIds.isNotEmpty() ||
            pendingDeleteId != 0L || celebration.isNotEmpty() || calendarOffer != null || farmUpdateAsked
    // Разбор дат — по ответу базы, сверка с часами — на каждую перерисовку. Тело экрана
    // перерисовывается на каждое открытие и закрытие шторки (девять состояний ниже), и
    // раньше каждая такая перерисовка заново разбирала даты всех закладок инкубатора.
    val finishMoments = remember(uiState.batches, uiState.catalog) {
        dueMoments(uiState.batches, uiState.catalog)
    }
    // Не одна закладка, а партия: лоток из двух пород лежит двумя закладками, и срок у них
    // выходит в одну минуту. Спросить только про первую значило бы оставить вторую до
    // следующего захода — после «Внести птенцов» подсказка гаснет.
    val due = if (finishPromptDismissed || overlayOpen || uiState.readOnly) {
        null
    } else {
        groupDueToFinish(finishMoments, Date())
    }

    due?.let { (batches, finishedAt) ->
        FinishedBatchPrompt(
            batches = batches,
            finishedAt = finishedAt,
            catalog = uiState.catalog,
            onLater = { finishPromptDismissed = true },
            onEnterChicks = {
                // Гасим подсказку до ухода с экрана: иначе «Отмена» в диалоге завершения
                // вернула бы её на место, и выйти из этой пары было бы нечем.
                finishPromptDismissed = true
                // Без архива: подсказка спрашивает итог, а не убирает закладку с глаз —
                // только что внесённый вывод логичнее увидеть в списке.
                finishHides = false
                // Одна закладка — прежние два диалога; партия — один на все породы,
                // где любую можно оставить пустой и внести позже.
                if (batches.size == 1) {
                    finishBatchId = batches.single().id
                } else {
                    finishGroupIds = batches.map { it.id }.toLongArray()
                }
            },
        )
    }

    // Экспорт закладки файлом. Системное окно выбора места — `ACTION_CREATE_DOCUMENT`,
    // то же, что у копии базы; тип MIME произвольный, у файла закладки своего нет.
    // Закладка на момент ответа берётся из списка по идентификатору: окно системное и
    // живёт дольше любого снимка.
    val allBatches = uiState.batches + uiState.hiddenBatches
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val batch = allBatches.firstOrNull { it.id == exportBatchId }
        if (uri != null && batch != null) {
            viewModel.onIntent(IncubatorIntent.ExportBatch(batch, uri))
        }
        exportBatchId = 0L
    }
    // Отправку запускает экран, а не контроллер, — у того нет активности. Состояние
    // сбрасывается сразу, иначе окно открывалось бы заново при каждом возврате на экран.
    LaunchedEffect(scheduleTransfer) {
        val ready = scheduleTransfer as? ScheduleTransferState.ReadyToShare ?: return@LaunchedEffect
        shareScheduleFile(context, ready.uri)
        viewModel.onIntent(IncubatorIntent.ClearScheduleTransfer)
    }
    allBatches.firstOrNull { it.id == exportBatchId }?.let { batch ->
        ExportScheduleDialog(
            batch = batch,
            onDismiss = { exportBatchId = 0L },
            onSaveToDevice = {
                Analytics.report(Events.SCHEDULE_EXPORT, mapOf("Вид" to batch.type))
                // Идентификатор остаётся до ответа системного окна: по нему закладку
                // найдут, когда место выбрано.
                exportLauncher.launch(ScheduleTransferController.fileNameFor(batch))
            },
            onShare = {
                Analytics.report(Events.SCHEDULE_SHARE, mapOf("Вид" to batch.type))
                exportBatchId = 0L
                viewModel.onIntent(IncubatorIntent.ShareBatch(batch))
            },
        )
    }
    ScheduleTransferDialog(
        state = scheduleTransfer,
        onDismiss = { viewModel.onIntent(IncubatorIntent.ClearScheduleTransfer) },
    )

    // Закладку ищем в списках заново: пока диалог открыт, поток мог её обновить, и
    // удалять нужно то, что в базе сейчас, а не снимок момента нажатия. Спрятанные тоже:
    // удаляют и из раскрытого архива.
    (uiState.batches + uiState.hiddenBatches)
        .firstOrNull { it.id == pendingDeleteId }?.let { batch ->
        ConfirmDeleteBatchDialog(
            title = batch.title.ifBlank { batch.type },
            onDismiss = { pendingDeleteId = 0L },
            onConfirm = {
                viewModel.onIntent(IncubatorIntent.DeleteBatch(batch))
                pendingDeleteId = 0L
            },
        )
    }

    // Полоса статус-бара уходит под цвет шапки: зелёная у работающего инкубатора,
    // серая у архивного. На Android 15+ её закрашивает сама шапка (см. `headerInset`),
    // на устройствах постарше — окно; [StatusBarAppearance] делает и то и другое.
    val headerColor =
        if (uiState.readOnly) DesignPalette.HeaderSurfaceArchived else DesignPalette.HeaderSurface
    StatusBarAppearance(color = headerColor, lightIcons = true)

    // Строка цифр в шапке сворачивается при листании вниз и возвращается при листании
    // вверх — как ряд фильтров в «Закладках», но соединение висит на колонке всего экрана
    // и слышит все три страницы. Название и модель не уезжают: они отвечают на «где я».
    var statsCollapsed by remember { mutableStateOf(false) }
    val headerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Тот же порог, что и у фильтров: он же отсекает вертикальную составляющую
                // горизонтального свайпа между вкладками.
                when {
                    available.y < -ChipsScrollThreshold -> statsCollapsed = true
                    available.y > ChipsScrollThreshold -> statsCollapsed = false
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding.withoutTop())
            .nestedScroll(headerScroll)
    ) {
        IncubatorHeader(
            uiState = uiState,
            navigateBack = navigateBack,
            onEdit = { showEditSheet = true },
            onShowQr = {
                Analytics.report(Events.QR_SHOWN)
                showQrSheet = true
            },
            statsExpanded = !statsCollapsed,
            // Верхний системный отступ достаётся шапке, а не экрану: под статус-баром
            // должен быть её фон, иначе над зелёным остаётся кремовая полоса.
            headerInset = contentPadding.calculateTopPadding(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = -SheetOverlap)
                .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Тот же переключатель, что и в шторке закладки: «таблетка» едет за пальцем
            // по дробной странице пейджера, а не перекрашивает вкладку по факту свайпа.
            // Доля читается лямбдой внутри него — иначе каждый кадр свайпа перерисовывал
            // бы весь экран вместе с шапкой и пейджером.
            SlidingTabSwitcher(
                tabs = incubatorTabs,
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier
                    .padding(horizontal = ScreenPadding)
                    .padding(vertical = 12.dp),
            )
            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp,
            ) { page ->
                // Каждая вкладка прокручивается сама: «Закладки» — потому что кнопка
                // «Добавить закладку» под списком прокручиваться не должна и держит
                // собственный скролл внутри себя, остальные две — обычной страницей.
                when (IncubatorTab.entries[page]) {
                    IncubatorTab.Batches -> BatchesTab(
                        uiState = uiState,
                        adHosts = batchesAdHosts,
                        // Любая карточка — идущая, завершённая, прерванная — ведёт в одну
                        // шторку; кончившуюся она откроет на просмотр (`viewOnly`).
                        onBatchClick = { batch ->
                            Analytics.report(Events.OPEN_BATCH)
                            detailBatchId = batch.id
                        },
                        onEditBatch = { editBatchId = it.id },
                        onFinishBatch = {
                            finishHides = true
                            finishBatchId = it.id
                        },
                        onHideBatch = {
                            viewModel.onIntent(IncubatorIntent.SetBatchHidden(it, hidden = true))
                        },
                        onUnhideBatch = {
                            viewModel.onIntent(IncubatorIntent.SetBatchHidden(it, hidden = false))
                        },
                        onReopenBatch = { viewModel.onIntent(IncubatorIntent.UnarchiveBatch(it)) },
                        onDeleteBatch = { pendingDeleteId = it.id },
                        onExportBatch = { exportBatchId = it.id },
                        onSendToFarm = when (farmStatus) {
                            FarmStatus.Absent -> null
                            FarmStatus.Outdated -> { _ -> farmUpdateAsked = true }
                            FarmStatus.Ready -> { batch ->
                                val chicks = farmChicksOf(batch, todayText())
                                // Не открылось — хозяйство удалили между проверкой и
                                // нажатием: спрашиваем заново, и пункт уйдёт из меню.
                                if (chicks != null && !sendToFarm(chicks, FARM_FROM_CARD)) farmCheck++
                            }
                        },
                        onAddBatch = { showAddBatchSheet = true },
                        // Замер по инкубатору есть только пока есть кому его записать:
                        // без идущих закладок под списком стоит прежняя «Добавить
                        // закладку» целиком.
                        onAddMeasurement = if (uiState.activeCount > 0) {
                            {
                                Analytics.report(Events.OPEN_INCUBATOR_MEASUREMENTS)
                                showMeasurementSheet = true
                            }
                        } else {
                            null
                        },
                        readOnly = uiState.readOnly,
                    )

                    // Обе вкладки — целиком про числа, и до ответа базы все они нули:
                    // «0 ₽ баланс» и «0 %» вывода это не пустая, а неверная сводка.
                    IncubatorTab.Stats ->
                        if (uiState.loading) LoadingBox()
                        else ScrollingPage {
                            if (uiState.readOnly) ArchivedTotalsNotice()
                            StatsTab(
                                uiState.stats,
                                adHost = statsAdHost,
                                archivedFinished = uiState.hiddenBatches.size,
                            )
                        }

                    IncubatorTab.Finance ->
                        if (uiState.loading) LoadingBox()
                        else ScrollingPage {
                            if (uiState.readOnly) ArchivedTotalsNotice()
                            FinanceTab(
                                uiState.finance,
                                adHost = financeAdHost,
                                archivedFinished = uiState.hiddenBatches.size,
                            )
                        }
                }
            }
        }
    }
}

// --- Шапка ---------------------------------------------------------------------------------

/**
 * Шапка экрана: название устройства, строка модели и три цифры под ними.
 *
 * **У инкубатора в архиве она серая и без цифр вовсе** — остаются название и модель.
 * Зелёный означает в этом приложении «в работе», и под ним выведенное из работы
 * устройство выглядит действующим; серый — тот же нейтральный цвет, которым помечено
 * «Завершено».
 *
 * Цифры уходят все три, а не одна: шапка отвечает на вопрос «что здесь происходит
 * сейчас», а в архиве не происходит ничего. «Яиц в работе» к тому же нулевое по
 * построению — архив прерывает все идущие закладки (`StartIntent.SetIncubatorHidden`), —
 * и «0 · Яиц в работе» рассказывало бы про архив, а не про этот инкубатор. «Выведено» и
 * «Эффективность» — настоящие итоги, но под названием они читаются как показатели
 * работающего устройства; те же цифры, вместе со всем, из чего они сложились, показывает
 * вкладка «Статистика», которая в архиве никуда не делась.
 */
@Composable
private fun IncubatorHeader(
    uiState: IncubatorUiState,
    navigateBack: () -> Unit,
    onEdit: () -> Unit,
    /** «QR-код» — шторка с кодом инкубатора; см. [IncubatorQrSheet]. */
    onShowQr: () -> Unit,
    /**
     * Раскрыта ли строка цифр. Считает её экран — по направлению листания любой из
     * страниц, см. `headerScroll` в [IncubatorScreen].
     */
    statsExpanded: Boolean = true,
    /** Высота статус-бара: шапка дотягивается под него, чтобы полоса была её цвета. */
    headerInset: Dp = 0.dp,
) {
    val incubator = uiState.incubator
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (uiState.readOnly) DesignPalette.HeaderSurfaceArchived
                else DesignPalette.HeaderSurface
            )
            .padding(start = ScreenPadding, end = 8.dp, top = 8.dp + headerInset, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = navigateBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left_design),
                    contentDescription = null,
                    tint = DesignPalette.HeaderIcon,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Все инкубаторы",
                    style = DesignType.FieldValue.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = DesignPalette.HeaderIcon,
                )
            }
            // QR-код слева от шестерёнки: оба — про прибор, а не про закладки, и оба остаются
            // у архивного инкубатора. Код первым, потому что нажимают его чаще: настройки —
            // раз при покупке, код — при каждой печати наклейки.
            IconButton(onClick = onShowQr) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_qr_code_design),
                    contentDescription = "QR-код инкубатора",
                    tint = DesignPalette.HeaderIcon,
                )
            }
            // Шестерёнка, а не карандаш: правится прибор — его вместимость, автоматика,
            // потребление, — и карандаш читался как «переименовать».
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки инкубатора",
                    tint = DesignPalette.HeaderIcon,
                )
            }
        }

        // Между «Все инкубаторы» и названием нет отступа, и верхний свинец первой строки
        // срезан ([LineHeightStyle.Trim.FirstLineTop]): воздух уже даёт ряд кнопки высотой
        // 48 dp — размер цели нажатия, уменьшать который нельзя.
        // Название и модель — в одну строку каждое: шапка стоит над всем экраном, и
        // длинное имя отодвигало бы цифры и вкладки. Обрезанное договаривает подсказка.
        TruncatedText(
            text = incubator?.name.orEmpty(),
            style = DesignType.HeaderTitle.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.FirstLineTop,
                ),
            ),
            color = DesignPalette.HeaderTitle,
            maxLines = 1,
            modifier = Modifier.padding(end = 12.dp),
        )
        val subtitle = if (incubator == null) "" else headerModelLine(incubator.brand, incubator.model, incubator.capacity)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            TruncatedText(
                text = subtitle,
                style = DesignType.Caption,
                color = DesignPalette.HeaderMuted,
                maxLines = 1,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // Отступ над строкой уезжает вместе с ней: оставшись снаружи, он свернул бы
        // не весь блок, а только его часть, и шапка не села бы до конца.
        AnimatedVisibility(
            visible = !uiState.readOnly && statsExpanded,
            enter = expandVertically(tween(ChipsSlideMillis)) + fadeIn(tween(ChipsSlideMillis)),
            exit = shrinkVertically(tween(ChipsSlideMillis)) + fadeOut(tween(ChipsSlideMillis)),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                // Три равные доли, а не `FlowRow`: показатели читаются одной сводкой, и
                // перенос третьего под первый делал бы его подписью к первому. Ширина по
                // содержимому отдала бы место подписям, а не числам. Уместиться ряд
                // обязан сам: число — одна строка с подсказкой, подпись переносится.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Пока база не ответила, в шапке стоят прочерки: ноль здесь — это
                    // утверждение о работающем инкубаторе, и притом самым крупным шрифтом
                    // на экране.
                    HeaderStat(
                        statValue(uiState.loading, uiState.eggsInWork),
                        "Яиц в работе",
                        Modifier.weight(1f),
                    )
                    HeaderStat(
                        statValue(uiState.loading, uiState.hatched),
                        "Выведено",
                        Modifier.weight(1f),
                    )
                    // Эффективность — то же `hatchRate`, что печатает вкладка «Статистика»
                    // (процент вывода по завершённым закладкам), а не свой счёт: два числа
                    // об одном и том же разошлись бы при первой правке формулы. Пока
                    // завершённых нет, процента нет — прочерк, а не «0%».
                    HeaderStat(
                        if (uiState.loading) "—"
                        else uiState.stats.hatchRate?.let { "$it%" } ?: "—",
                        "Эффективность",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Одна цифра шапки с подписью под ней; ширину получает снаружи — треть ряда.
 *
 * Цифра — в одну строку, обрезанная показывает себя в подсказке ([TruncatedText]):
 * многоточие съедает лишь младшие разряды. Подпись, наоборот, переносится — это слова
 * приложения, и в двух строках они читаются целиком, а обрезанные не читаются никак.
 */
@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        TruncatedText(
            text = value,
            style = DesignType.HeaderStatValue,
            color = DesignPalette.HeaderTitle,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = DesignType.Micro,
            color = DesignPalette.HeaderMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * «Blitz 72 Turbo · 72 мест» — строка модели под названием инкубатора; вместимость 0
 * значит «не указана». Пусто, когда не задан ни бренд, ни модель: одна вместимость без
 * модели читалась бы как обрывок, поэтому строки тогда нет вовсе.
 */
private fun headerModelLine(brand: String, model: String, capacity: Int): String {
    val line = modelLine(brand, model)
    if (line.isBlank()) return ""
    return if (capacity > 0) {
        "$line · " + plural(capacity, "место", "места", "мест")
    } else line
}

// --- Вкладка «Закладки» --------------------------------------------------------------------

/** Обычная страница пейджера: общие отступы макета и собственный вертикальный скролл. */
@Composable
private fun ScrollingPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 20.dp, bottom = 32.dp),
        content = content,
    )
}

@Composable
private fun BatchesTab(
    uiState: IncubatorUiState,
    adHosts: BannerAdHosts,
    onBatchClick: (Batch) -> Unit,
    onEditBatch: (Batch) -> Unit,
    onFinishBatch: (Batch) -> Unit,
    onHideBatch: (Batch) -> Unit,
    onUnhideBatch: (Batch) -> Unit,
    onReopenBatch: (Batch) -> Unit,
    onDeleteBatch: (Batch) -> Unit,
    onExportBatch: (Batch) -> Unit,
    /**
     * «В «Моё хозяйство»» у завершённой закладки с птенцами. `null` — хозяйства на
     * телефоне нет, и пункта нет ни у одной карточки.
     */
    onSendToFarm: ((Batch) -> Unit)?,
    onAddBatch: () -> Unit,
    /**
     * «Внести замер» — замер по всему инкубатору. `null`, когда идущих закладок нет:
     * тогда под списком стоит одна «Добавить закладку», как в макете.
     */
    onAddMeasurement: (() -> Unit)?,
    /** Инкубатор в архиве: список только показывают — см. [IncubatorUiState.readOnly]. */
    readOnly: Boolean,
) {
    // До первого ответа базы — колесо, а не пустой список: «Закладок пока нет»
    // и «закладки ещё не прочитаны» выглядели одинаково, и второе читалось как
    // первое. Выходим раньше всего остального: ни чипы, ни кнопка «Добавить
    // закладку» не относятся к списку, которого ещё нет.
    if (uiState.loading) {
        LoadingBox()
        return
    }

    // Разбиение по статусам и ряд фильтров считаются один раз на ответ базы, а не на
    // кадр. Тело вкладки перерисовывается на каждое движение пальца — прокрутка прячет и
    // возвращает ряд фильтров, — и без этого `remember` в каждый такой кадр уходили
    // `groupBy` по всем закладкам инкубатора и пять `buildList` следом.
    val groups = remember(uiState.batches, uiState.hiddenBatches) {
        batchGroups(uiState.batches, uiState.hiddenBatches)
    }

    // Фильтр держим индексом: `rememberSaveable` кладёт значение в `Bundle`, а enum
    // туда напрямую не ложится.
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    // Архив — не пункт списка статусов, а отдельная кнопка рядом с ним: статус говорит,
    // чем закладка кончилась, архив — где лежит карточка. Статусный фильтр работает и
    // внутри архива: там тоже есть что делить на «Завершённые» и «Прерванные».
    var archiveRequested by rememberSaveable { mutableStateOf(false) }

    // Архив может опустеть под ногами — последнюю закладку вернули в список, — и тогда
    // экран молча возвращается к основному списку.
    val showArchive = archiveRequested && groups.archive.all.isNotEmpty()
    val scope = if (showArchive) groups.archive else groups.main

    // Выбранный фильтр тоже может исчезнуть — последнюю закладку категории завершили или
    // убрали, — и тогда список молча показывает «Все».
    val selected = BatchFilter.entries[filterIndex]
        .takeIf { chosen -> groups.showFilters && scope.filters.any { it.first == chosen } }
        ?: BatchFilter.All
    // И сам выбор при этом сбрасывается, а не только показ. Иначе состояние дожидается,
    // пока категория появится снова, и экран сам прыгает в неё — например, в архив в
    // ответ на «Убрать в архив» сразу после того, как оттуда достали последнюю закладку.
    LaunchedEffect(selected, showArchive) {
        if (selected.ordinal != filterIndex) filterIndex = selected.ordinal
        if (archiveRequested != showArchive) archiveRequested = showArchive
    }

    val shown = scope.listFor(selected)

    // Ряд фильтров прячется при листании вниз и возвращается, стоит потянуть вверх, —
    // на экран садится ещё одна карточка, а фильтр остаётся в одном движении от пальца.
    // Направление берём из `onPreScroll`, а не из положения списка: там оно приходит
    // готовым и до того, как список сдвинулся.
    val listState = rememberLazyListState()
    var chipsHidden by remember { mutableStateOf(false) }
    val chipsScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Порог отсекает дрожание пальца: без него ряд мигал бы на месте.
                when {
                    available.y < -ChipsScrollThreshold -> chipsHidden = true
                    available.y > ChipsScrollThreshold -> chipsHidden = false
                }
                return Offset.Zero
            }
        }
    }
    // В самом верху список показывает ряд всегда: там прятать нечего, а вернувшись к
    // началу, пользователь ждёт экран в исходном виде. Через `derivedStateOf`, потому что
    // положение списка меняется каждый кадр прокрутки, а ответ «мы наверху» — дважды за
    // всё листание; читая положение напрямую, вкладка перерисовывалась бы на каждый
    // пиксель пути.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val chipsVisible = groups.showFilters && (!chipsHidden || atTop)

    // Видна ли пунктирная кнопка под списком; уехала за край — всплывает мини-кнопка.
    // Элемент ленивого списка за экраном не построен, поэтому ищем его по ключу среди
    // видимых. Пустой `layoutInfo` — первый кадр до измерения: кнопка считается видимой,
    // чтобы мини-кнопка не мигнула поверх пустого экрана.
    val addButtonVisible by remember {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            items.isEmpty() || items.any { it.key == AddButtonKey }
        }
    }

    // Смена фильтра возвращает список к началу: ленивый список помнит место по номеру
    // элемента, и короткий результат открывался бы пустым экраном — карточка выше края.
    LaunchedEffect(selected, showArchive) {
        listState.scrollToItem(0)
    }

    // Новый список проявляется, а не подменяется в один кадр, — и по движению видно, что
    // сменилось. Вход в архив и выход из него — смена места, поэтому список въезжает
    // сбоку: архив справа, основной слева. Смена статуса — тот же список, только уже, и
    // он лишь чуть поднимается снизу. Если в одном кадре сменилось и то и другое (архив
    // открыли, и выбранный статус там пропал), это вход в архив.
    // Анимируется весь список одним слоем, а не каждая карточка: у ленивого списка
    // уходящие элементы продолжали бы жить в композиции рядом с пришедшими, и один
    // `BannerAdView` рекламного места оказался бы нужен двум карточкам сразу.
    val listSwitch = remember { Animatable(1f) }
    var lastArchive by remember { mutableStateOf(showArchive) }
    var lastFilter by remember { mutableStateOf(selected) }
    var switchKind by remember { mutableStateOf(ListSwitch.Filter) }
    LaunchedEffect(selected, showArchive) {
        if (lastArchive == showArchive && lastFilter == selected) return@LaunchedEffect
        switchKind = when {
            lastArchive == showArchive -> ListSwitch.Filter
            showArchive -> ListSwitch.IntoArchive
            else -> ListSwitch.OutOfArchive
        }
        lastArchive = showArchive
        lastFilter = selected
        listSwitch.snapTo(0f)
        listSwitch.animateTo(1f, tween(ListSwitchMillis, easing = FastOutSlowInEasing))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(chipsScroll)
        ) {
            // Ряд фильтров — над списком, а не под ним: вход в архив виден сразу.
            AnimatedVisibility(
                visible = chipsVisible,
                enter = expandVertically(tween(ChipsSlideMillis)) + fadeIn(tween(ChipsSlideMillis)),
                exit = shrinkVertically(tween(ChipsSlideMillis)) + fadeOut(tween(ChipsSlideMillis)),
            ) {
                BatchFilterRow(
                    filters = scope.filters,
                    selected = selected,
                    onSelect = { filterIndex = it.ordinal },
                    archiveCount = groups.archive.all.size,
                    archiveShown = showArchive,
                    onToggleArchive = { archiveRequested = !showArchive },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .padding(top = 16.dp, bottom = 12.dp),
                )
            }

            // Список ленивый не впрок: каждое третье место — живой `BannerAdView` с
            // `WebView`, и обычная колонка строила бы все карточки и объявления разом.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Значение анимации читается внутри слоя, а не в композиции: кадры
                    // въезда не перестраивают вкладку.
                    .graphicsLayer {
                        val rest = 1f - listSwitch.value
                        alpha = listSwitch.value
                        when (switchKind) {
                            ListSwitch.IntoArchive -> translationX = rest * ArchiveSlideDistance.toPx()
                            ListSwitch.OutOfArchive -> translationX = -rest * ArchiveSlideDistance.toPx()
                            ListSwitch.Filter -> translationY = rest * FilterRiseDistance.toPx()
                        }
                    },
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = if (groups.showFilters) 4.dp else 20.dp,
                    bottom = 32.dp,
                ),
            ) {
                // Плашка стоит над списком, а не вместо кнопки: пропавшие «⋮» и
                // «Добавить закладку» нужно объяснить до того, как их начнут искать.
                if (readOnly) {
                    item(key = "read-only") {
                        ReadOnlyNotice(modifier = Modifier.padding(bottom = 12.dp))
                    }
                }

                // Совсем пустой рабочий инкубатор — картинка с объяснением: это первый
                // экран после создания устройства, и строка «пока нет закладок» говорила
                // только что их нет, но не что они такое. Кнопки в заглушке нет — сразу
                // под ней стоит пунктирная «Добавить закладку». Пустой фильтр, пустой
                // архив и инкубатор в архиве остаются строкой: там ответ — одна фраза.
                val noBatchesAtAll = uiState.batches.isEmpty() && uiState.hiddenBatches.isEmpty()
                if (shown.isEmpty() && noBatchesAtAll && !readOnly) {
                    item(key = "empty") {
                        EmptyState(
                            emoji = "🥚",
                            title = "Закладок пока нет",
                            text = "Заложите яйца — приложение составит режим по дням, " +
                                "напомнит о замерах и овоскопировании и посчитает срок вывода.",
                            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                        )
                    }
                } else if (shown.isEmpty() && !showArchive && uiState.batches.isEmpty() &&
                    uiState.hiddenBatches.isNotEmpty()
                ) {
                    // Закладки есть, но все убраны в архив: список пуст не потому, что
                    // ничего не закладывали, и «пока нет закладок» было бы неправдой. Кнопка
                    // ведёт туда, где они лежат, — та же, что «Архив» над списком.
                    item(key = "empty") {
                        EmptyState(
                            emoji = "📦",
                            title = "Все закладки в архиве",
                            // В инкубаторе из архива меню у карточек нет, и новых закладок
                            // не будет — обещать ни то ни другое там нельзя.
                            text = if (readOnly) {
                                "Они не пропали — их можно посмотреть в архиве."
                            } else {
                                "Они не пропали — их можно посмотреть в архиве и вернуть " +
                                    "в список из меню карточки. Новая закладка появится здесь."
                            },
                            actionText = "Открыть архив",
                            actionIcon = painterResource(R.drawable.baseline_archive_24),
                            onAction = { archiveRequested = true },
                            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                        )
                    }
                } else if (shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = emptyBatchesText(selected, showArchive),
                            style = DesignType.Placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )
                    }
                }

                // Реклама повторяется по списку — сразу под первой закладкой, дальше через
                // каждые три, — а не стоит одна: в инкубаторе на десяток закладок конец
                // прокрутки не показывается вовсе, а единственное объявление сверху уезжает
                // с первым же движением пальца. Какие места рекламные, считает
                // `adSlotAfter`; объявление стоит в том же элементе, что и карточка, за
                // которой оно следует, а сам `BannerAdView` живёт в `adHosts` на уровне
                // экрана, и прокрутка мимо его не пересоздаёт.
                itemsIndexed(shown, key = { _, batch -> batch.id }) { index, batch ->
                    Column {
                        BatchCard(
                            batch = batch,
                            catalog = uiState.catalog,
                            onClick = { onBatchClick(batch) },
                            onEdit = { onEditBatch(batch) },
                            onFinish = { onFinishBatch(batch) },
                            onHide = { onHideBatch(batch) },
                            onUnhide = { onUnhideBatch(batch) },
                            onReopen = { onReopenBatch(batch) },
                            onDelete = { onDeleteBatch(batch) },
                            onExport = { onExportBatch(batch) },
                            onSendToFarm = onSendToFarm?.let { send -> { send(batch) } },
                            readOnly = readOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                        AdBannerAfter(
                            index = index,
                            hosts = adHosts,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }

                // Пока в инкубаторе что-то идёт, место пунктирной кнопки делят две:
                // широкая «Внести замер» — ежедневное действие, ради которого экран и
                // открывают, — и квадратный «+» для новой закладки, которая заводится
                // раз в месяц. Без идущих закладок замерять нечего, и «Добавить закладку»
                // снова стоит одна во всю ширину, как в макете. Оба варианта — один
                // элемент списка под одним ключом: мини-«+» в углу по-прежнему
                // спрашивает у списка, виден ли этот элемент.
                if (!readOnly) {
                    item(key = AddButtonKey) {
                        if (onAddMeasurement == null) {
                            DashedButton(
                                text = "Добавить закладку",
                                onClick = onAddBatch,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                DashedButton(
                                    text = "Внести замер",
                                    onClick = onAddMeasurement,
                                    icon = R.drawable.ic_temperature_design,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                DashedIconButton(
                                    contentDescription = "Добавить закладку",
                                    onClick = onAddBatch,
                                )
                            }
                        }
                    }
                }

                // Пустому списку рекламное место достаётся отдельно: карточки, за которой
                // её поставить, нет вовсе, поэтому объявление уходит под кнопку.
                if (shown.isEmpty()) {
                    item(key = "ad-empty") {
                        AdBanner(adHosts.host(0), Modifier.padding(top = 16.dp))
                    }
                }
            }
        }

        // Плавающая кнопка — пока пунктирная под списком за экраном. Пока в инкубаторе
        // что-то идёт, это разрезанная надвое кнопка по центру низа (`MeasurementSplitFab`):
        // широкий сегмент «Внести замер» и сегмент «+» — то же разделение, что у пунктирной
        // в списке, и в том же порядке, чтобы палец, знающий одну, знал и другую. По центру,
        // а не в углу, потому что широкой кнопке в углу не место: она закрыла бы «⋮» у двух
        // карточек разом. Без идущих закладок — прежний мини-«+» в правом нижнем углу, там
        // же, где «+ Инкубатор» на `StartScreen`: маленький и круглый, он перекрывает меню
        // только той карточки, что остановилась прямо под ним.
        AnimatedVisibility(
            // В архивном инкубаторе кнопки нет ни под списком, ни снизу: добавлять
            // закладки в устройство, выведенное из работы, некуда.
            visible = !addButtonVisible && !readOnly,
            enter = fadeIn(tween(FabFadeMillis)) + scaleIn(tween(FabFadeMillis), initialScale = 0.7f),
            exit = fadeOut(tween(FabFadeMillis)) + scaleOut(tween(FabFadeMillis), targetScale = 0.7f),
            // Пока идёт таймер проветривания, слева стоит его кольцо (`AiringTimerFab`), и
            // широкая кнопка посередине легла бы на него — она уходит к правому краю.
            // Лист с вкладками нарисован на `SheetOverlap` выше, чем размечен; обратный
            // сдвиг ставит кнопку на те же 20 dp над системной панелью, что и кольцо.
            modifier = if (onAddMeasurement != null && !airingTimerActive()) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = SheetOverlap)
                    .padding(bottom = ScreenPadding)
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = SheetOverlap)
                    .padding(end = ScreenPadding, bottom = ScreenPadding)
            },
        ) {
            if (onAddMeasurement != null) {
                MeasurementSplitFab(onAddMeasurement = onAddMeasurement, onAddBatch = onAddBatch)
            } else {
                AddBatchFab(onClick = onAddBatch)
            }
        }
    }
}

/** Скругление внешних углов split-кнопки; внутренние, у разреза, — [SplitFabInnerRadius]. */
private val SplitFabOuterRadius = 28.dp
private val SplitFabInnerRadius = 8.dp
private val SplitFabHeight = 56.dp

/**
 * Плавающая split-кнопка «Внести замер | +» по центру низа списка.
 *
 * Рисуется как split button из Material 3: два сегмента одного цвета с зазором в 2 dp,
 * у каждого внешний угол скруглён вполовину высоты, внутренний — чуть, чтобы разрез
 * читался как разрез одной кнопки, а не как две кнопки рядом. Ведущий сегмент — всё, что
 * делают каждый день: термометр и подпись; хвостовой — только плюс, потому что это тот
 * же «+», что мини-кнопка в углу, и подпись ему не нужна. Собрана руками, а не из
 * `SplitButtonLayout`: тот в Material 3 экспериментальный и подписан под меню в хвосте,
 * а здесь хвост — самостоятельное действие.
 */
@Composable
private fun MeasurementSplitFab(
    onAddMeasurement: () -> Unit,
    onAddBatch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onAddMeasurement,
            shape = RoundedCornerShape(
                topStart = SplitFabOuterRadius,
                bottomStart = SplitFabOuterRadius,
                topEnd = SplitFabInnerRadius,
                bottomEnd = SplitFabInnerRadius,
            ),
            color = DesignPalette.Accent,
            contentColor = DesignPalette.OnAccent,
            shadowElevation = 6.dp,
            modifier = Modifier.height(SplitFabHeight),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_temperature_design),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(text = "Внести замер", style = DesignType.ButtonLabel, maxLines = 1)
            }
        }
        Spacer(Modifier.width(2.dp))
        Surface(
            onClick = onAddBatch,
            shape = RoundedCornerShape(
                topStart = SplitFabInnerRadius,
                bottomStart = SplitFabInnerRadius,
                topEnd = SplitFabOuterRadius,
                bottomEnd = SplitFabOuterRadius,
            ),
            color = DesignPalette.Accent,
            contentColor = DesignPalette.OnAccent,
            shadowElevation = 6.dp,
            modifier = Modifier.height(SplitFabHeight),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Добавить закладку")
            }
        }
    }
}

/** Ключ элемента с пунктирной кнопкой: по нему список отвечает, видна ли она сейчас. */
private const val AddButtonKey = "add-batch"

/**
 * Закладки, разложенные по статусам, и ряд фильтров над ними.
 *
 * Считается один раз на ответ базы (`remember` во вкладке), а не на кадр прокрутки.
 */
private class BatchGroups(
    /** Основной список — всё, что не убрано в архив. */
    val main: StatusGroups,
    /** Архив: те же статусы, но среди закладок с `hidden`. */
    val archive: StatusGroups,
    val showFilters: Boolean,
)

/** Одна область списка (основной или архив), разбитая по статусам. */
private class StatusGroups(
    val all: List<Batch>,
    val active: List<Batch>,
    val hatched: List<Batch>,
    val stopped: List<Batch>,
    /** Пункты выпадающего списка: «Все» и только непустые статусы, со счётчиками. */
    val filters: List<Pair<BatchFilter, Int>>,
) {
    /** Сколько статусов из трёх в этой области непусты. */
    val statusCount: Int get() = listOf(active, hatched, stopped).count { it.isNotEmpty() }

    fun listFor(filter: BatchFilter): List<Batch> = when (filter) {
        BatchFilter.All -> all
        BatchFilter.Active -> active
        BatchFilter.Finished -> hatched
        BatchFilter.Stopped -> stopped
    }
}

private fun statusGroups(batches: List<Batch>): StatusGroups {
    // Статусы — разбиение списка, а не пересекающиеся выборки: «Завершённые» не включают
    // прерванные, иначе счётчики в списке не сходились бы с «Все» и одна и та же закладка
    // считалась бы дважды.
    val byStatus = batches.groupBy { it.status }
    val active = byStatus[BatchStatus.Active].orEmpty()
    val hatched = byStatus[BatchStatus.Hatched].orEmpty()
    val stopped = byStatus[BatchStatus.Stopped].orEmpty()
    return StatusGroups(
        all = batches,
        active = active,
        hatched = hatched,
        stopped = stopped,
        filters = buildList {
            add(BatchFilter.All to batches.size)
            if (active.isNotEmpty()) add(BatchFilter.Active to active.size)
            if (hatched.isNotEmpty()) add(BatchFilter.Finished to hatched.size)
            if (stopped.isNotEmpty()) add(BatchFilter.Stopped to stopped.size)
        },
    )
}

private fun batchGroups(batches: List<Batch>, archived: List<Batch>): BatchGroups {
    val main = statusGroups(batches)
    // Ряд показываем, только когда есть что разделять: с одной закладкой в списке
    // фильтр «Все (1)» ничего не сообщает. «Есть что разделять» — это две непустых
    // категории из трёх, а не «идущие и завершённые»: инкубатор, где одна закладка идёт,
    // а другую прервали, разделять тоже есть на что. Но если в архиве что-то лежит, ряд
    // нужен всегда — иначе спрятанные закладки негде было бы найти, а при инкубаторе,
    // где убрано вообще всё, экран выглядел бы пустым.
    return BatchGroups(
        main = main,
        archive = statusGroups(archived),
        showFilters = archived.isNotEmpty() || main.statusCount > 1,
    )
}

/**
 * Плашка «инкубатор в архиве» над списком закладок.
 *
 * Обводкой и без иконки-кнопки: это объяснение, а не действие. Вернуть инкубатор в
 * работу отсюда нельзя намеренно — архив снимается на главном экране, где его и ставили,
 * и второй вход в то же решение развёл бы два места, где инкубатор «достают».
 */
@Composable
private fun ReadOnlyNotice(
    modifier: Modifier = Modifier,
    text: String = "Инкубатор в архиве — закладки только для просмотра. " +
        "Верните его в список на главном экране, чтобы менять их снова.",
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.8.dp, DesignPalette.CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.baseline_archive_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Та же плашка над «Статистикой» и «Финансами» архивного инкубатора. Цифры на вкладках
 * по-прежнему считаются по его закладкам, а в общую «Аналитику» хозяйства они уже не
 * входят — и смотрящий на эти цифры должен знать, что они больше никуда не идут.
 */
@Composable
private fun ArchivedTotalsNotice() {
    ReadOnlyNotice(
        modifier = Modifier.padding(bottom = 16.dp),
        text = "Инкубатор в архиве, и его закладки не учитываются в общей статистике " +
            "и финансах хозяйства — как будто их не было. Чтобы они снова вошли " +
            "в итоги, верните инкубатор в список на главном экране.",
    )
}

/**
 * Мини-кнопка «добавить закладку» — только плюс, без подписи: она всплывает поверх
 * списка, и всё, что шире круга, закрывало бы карточки. Пунктирную кнопку под списком
 * она не заменяет, а подменяет на время, пока та не видна.
 */
@Composable
private fun AddBatchFab(onClick: () -> Unit) {
    // Тот же зелёный и белый, что у «+ Инкубатор» на `StartScreen`, но маленький и
    // круглый: там кнопка стоит на пустом фоне и может позволить себе подпись, здесь
    // она висит над карточками.
    SmallFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = DesignPalette.Accent,
        contentColor = DesignPalette.OnAccent,
        modifier = Modifier.size(FabDiameter),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = "Добавить закладку")
    }
}

/**
 * Чем список закладок можно ограничить. Порядок — порядок пунктов в выпадающем списке,
 * и он же порядок жизни закладки: идёт → дошла до срока → сорвалась.
 *
 * Это ровно [BatchStatus] плюс «Все». Архив сюда не входит: `hidden` — не статус, а
 * место хранения карточки, и у него своя кнопка рядом со списком ([ArchiveToggleButton]).
 */
private enum class BatchFilter(val title: String) {
    All("Все"),
    Active("Инкубация"),
    Finished("Завершённые"),
    Stopped("Прерванные"),
}

/** Что написать вместо списка, когда выбранный фильтр ничего не нашёл. */
private fun emptyBatchesText(filter: BatchFilter, archive: Boolean): String = when (filter) {
    BatchFilter.All -> if (archive) "В архиве пусто." else "В этом инкубаторе пока нет закладок."
    BatchFilter.Active -> "Сейчас в инкубации нет ни одной закладки."
    BatchFilter.Finished -> "Завершённых закладок пока нет."
    BatchFilter.Stopped -> "Ни одной прерванной закладки — и хорошо."
}

/**
 * Ряд фильтров над списком: выпадающий список статусов и кнопка архива справа от него.
 *
 * Выпадающий список, а не ряд чипов: пять чипов со счётчиками на узкий экран в одну
 * строку не вставали и переносились на вторую, отъедая у списка высоту. Список
 * занимает одну строку при любом числе статусов. Архив стоит отдельной кнопкой, потому
 * что это не ещё один статус, а другое место, — и виден сразу, без раскрытия списка.
 */
@Composable
private fun BatchFilterRow(
    filters: List<Pair<BatchFilter, Int>>,
    selected: BatchFilter,
    onSelect: (BatchFilter) -> Unit,
    /** Сколько закладок в архиве; `0` — кнопки нет совсем. */
    archiveCount: Int,
    archiveShown: Boolean,
    onToggleArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = filters.toMap()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetDropdownField(
            options = filters.map { it.first },
            selected = selected,
            label = { filter ->
                val title = if (filter == BatchFilter.All && archiveShown) "Весь архив" else filter.title
                "$title (${counts[filter] ?: 0})"
            },
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        // Кнопка появляется с первой убранной закладкой и уходит с последней, обе —
        // на глазах у человека, поэтому выдвигается, а не возникает. Зазор живёт внутри
        // анимации: снаружи он стоял бы и при спрятанной кнопке, и поле не доходило бы
        // до края. Число в ней запоминается, чтобы уходящая кнопка не показывала «0», —
        // простым держателем, а не `State`: композиция не пишет состояние, которое читает.
        val heldCount = remember { intArrayOf(archiveCount) }
        if (archiveCount > 0) heldCount[0] = archiveCount
        AnimatedVisibility(
            visible = archiveCount > 0,
            enter = expandHorizontally(tween(ChipsSlideMillis), expandFrom = Alignment.Start) +
                fadeIn(tween(ChipsSlideMillis)),
            exit = shrinkHorizontally(tween(ChipsSlideMillis), shrinkTowards = Alignment.Start) +
                fadeOut(tween(ChipsSlideMillis)),
        ) {
            ArchiveToggleButton(
                count = heldCount[0],
                selected = archiveShown,
                onClick = onToggleArchive,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Кнопка «Архив» рядом со списком статусов: включает и выключает показ закладок,
 * убранных в архив.
 *
 * Высотой и скруглением — ровно поле слева ([FieldHeight], [FieldRadius]), чтобы ряд
 * читался одной строкой. Включённая залита акцентом, как выбранный чип: так видно, что
 * список под ней — архив, а не основной.
 */
@Composable
private fun ArchiveToggleButton(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content by animateColorAsState(
        if (selected) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(ChipsSlideMillis),
        label = "archiveContent",
    )
    val fill by animateColorAsState(
        if (selected) DesignPalette.Accent else DesignPalette.Surface,
        tween(ChipsSlideMillis),
        label = "archiveFill",
    )
    val border by animateColorAsState(
        if (selected) DesignPalette.Accent else DesignPalette.CardBorder,
        tween(ChipsSlideMillis),
        label = "archiveBorder",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FieldRadius),
        color = fill,
        contentColor = content,
        border = BorderStroke(0.8.dp, border),
        modifier = modifier
            .height(FieldHeight)
            .semantics {
                stateDescription = if (selected) "архив показан" else "архив скрыт"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_archive_24),
                contentDescription = "Архив",
                modifier = Modifier.size(18.dp),
            )
            Text(text = formatCount(count), style = DesignType.FieldValue, maxLines = 1)
        }
    }
}

/**
 * Карточка закладки в списке инкубатора.
 *
 * Троеточие справа открывает меню карточки: правка, архив, удаление — см.
 * [BatchCardMenu]. Макет его не рисует, как и шестерёнки в шапке, но иначе к этим
 * действиям не подобраться, не открыв саму закладку, а правку и удаление часто хотят
 * прямо из списка, глядя на несколько закладок сразу.
 */
@Composable
private fun BatchCard(
    batch: Batch,
    /** Откуда карточка узнаёт срок: у своего вида он не в коде, а в базе. */
    catalog: SpeciesCatalog,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    /** Хозяйство на телефоне есть; пункт появится, только если у закладки есть птенцы. */
    onSendToFarm: (() -> Unit)?,
    /** Инкубатор в архиве: карточка остаётся, троеточия у неё нет. */
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Текущая минута — часть ключа: `batchProgress` считает «День N/M» от часа и минуты
    // закладки, а «через N дн.» — от сегодняшней даты, и без неё экран, оставленный
    // открытым, показывал бы прошлый день до следующего ответа базы.
    val progress = remember(batch, catalog, System.currentTimeMillis() / 60_000L) {
        batchProgress(batch, catalog)
    }
    val status = batch.status
    val isFinished = status != BatchStatus.Active

    // Нажатие — собственное `onClick` карточки, как у `IncubatorCard` на `StartScreen`,
    // а не `Modifier.clickable` снаружи: так форма нажатия совпадает со скруглением
    // карточки, а не с прямоугольником вокруг неё.
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        // Меню лежит поверх карточки, а не в её строке, и это ради нижней строки.
        // Стоя в `Row`, кнопка отнимала свои 56 dp у всей колонки текста, до самого низа
        // карточки, — а нужна она только вровень с названием. На узком экране этих
        // 56 dp как раз и не хватало: «Вывод 4 окт.» рядом с «через 28 дн.» обрезалось
        // до «Вывод 4 …», то есть терялось ровно то, ради чего строка написана. Место
        // под кнопкой пустует, и теперь его занимает текст — как и сказано в макете,
        // где «⋮» не было вовсе. Отступ названия справа возвращает те же 56 dp только
        // ему одному (16 dp своих + 40 накинутых), чтобы длинное имя не лезло под
        // троеточие.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                // По верху, а не по центру: левый столбик (статус, кольцо, день) выше
                // текста справа, и по центру название съезжало вниз, отрываясь от
                // верхнего края карточки и от троеточия, которое стоит у этого края.
                verticalAlignment = Alignment.Top,
            ) {
                // «День 6/21» стоит под кольцом, а не в строке с яйцами: кольцо и есть
                // картинка этого числа — заполненная дуга говорит «шестой из
                // двадцати одного», и подпись под ней читается как её значение, а не
                // как ещё одна цифра в ряду. Столбик центрирован по кольцу, чтобы
                // «День 21/21» и «День 6/21» не ёрзали относительно него.
                // Ширина постоянная, а не по содержимому: статусы и дни разной длины,
                // и столбик по содержимому сдвигал бы текст справа — названия соседних
                // карточек начинались бы с разных мест.
                Column(
                    modifier = Modifier.width(RingColumnWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Статус — над кольцом, а не рядом с названием: так название берёт
                    // всю ширину строки и не роняет чип на вторую, а сам статус
                    // открывает столбик, который кольцом и днём рассказывает о том же.
                    StatusChip(status)
                    Spacer(Modifier.height(8.dp))
                    ProgressRing(
                        fraction = progress.fraction,
                        species = batch.type,
                        color = progressRingColor(status = status, archived = batch.hidden),
                    )
                    if (progress.dayLabel.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = progress.dayLabel,
                            style = DesignType.Mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    // Чипов в строке названия больше нет: статус стоит над кольцом, а
                    // «Архив» не пишется вовсе — в архиве карточка видна только за
                    // кнопкой «Архив» над списком, которая и так это говорит.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (readOnly) 0.dp else 40.dp),
                    ) {
                        // Название закладки человек пишет сам, и обрезанное оно
                        // договаривает подсказкой по нажатию — так же, как название
                        // инкубатора на стартовом экране. Тап у обрезанного названия
                        // уходит подсказке, а не карточке: соседние закладки различаются
                        // как раз хвостом («Куры 2», «Куры 2 весна»), и прочесть его
                        // больше негде — карточка же открывается с любого другого места.
                        TruncatedText(
                            text = batch.title.ifBlank { batch.type },
                            style = DesignType.BatchTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }

                    // «Курицы · Ломан Браун» — вид и породы под названием. Кольцо
                    // говорит о виде только смайликом, а порода на карточке иначе не
                    // значилась бы вовсе, хотя два лотка одной птицы различаются именно
                    // ею. Вид пишется лишь при своём названии: у безымянной закладки
                    // он уже стоит заголовком, и повторять его строкой ниже незачем.
                    // Без пород и без названия строки нет совсем — пустая строка под
                    // заголовком читалась бы как выпавшее поле.
                    val subtitle = remember(batch) {
                        listOfNotNull(
                            batch.type.takeIf { batch.title.isNotBlank() },
                            batch.breed.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                    }
                    // Со значком, как строки яиц и даты вывода ниже: без него она одна
                    // из трёх подписей карточки выглядела оторванной от остальных. Не
                    // `IconLabel`: текст здесь вписал человек, и обрезанный он
                    // договаривает подсказкой. `weight` — на `Box`, а не на
                    // `TruncatedText`: тот теряет его внутри своей обёртки.
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.padding(end = if (readOnly) 0.dp else 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_feather_design),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Box(Modifier.weight(1f, fill = false)) {
                                TruncatedText(
                                    text = subtitle,
                                    style = DesignType.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    IconLabel(
                        icon = R.drawable.ic_egg_design,
                        text = plural(batch.eggAll, "яйцо", "яйца", "яиц"),
                    )

                    // Дата вывода — следующей строкой с тем же шагом, что и яйца над
                    // ней: это ещё одна строка того же списка, и лишний отступ отрывал
                    // её от остальных.
                    Spacer(Modifier.height(4.dp))
                    IconLabel(
                        icon = R.drawable.ic_calendar_design,
                        text = progress.hatchLabel,
                    )
                    // «Через N дн.» стоит в правом нижнем углу карточки (ниже, поверх
                    // `Box`), а здесь под него держится место высотой в его строку.
                    // Обычно левый столбик выше текста, и резерв ничего не стоит; когда
                    // текст выше (архив, длинный вид) — срок встаёт своей строкой под
                    // датой, а не наезжает на неё.
                    if (progress.trailing != null) {
                        val lineHeight = with(LocalDensity.current) {
                            DesignType.MonoAccent.lineHeight.toDp()
                        }
                        Spacer(Modifier.height(lineHeight + 4.dp))
                    }
                }
            }

            if (progress.trailing != null) {
                Text(
                    text = progress.trailing,
                    style = DesignType.MonoAccent,
                    color = if (isFinished) {
                        DesignPalette.Accent
                    } else {
                        DesignPalette.DateEmphasis
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }

            // В архивном инкубаторе меню нет целиком, а не с погашенными пунктами:
            // все четыре его действия — правка, завершение, архив и удаление — меняют
            // закладку, и открывать пустое меню незачем. Исключение одно — экспорт: он
            // закладку читает, а не меняет, и архив устройства ему не помеха; у удачной
            // закладки архивного инкубатора меню остаётся из одного этого пункта. Второе
            // такое же — «В «Моё хозяйство»»: оно тоже только читает закладку.
            val farmOffer = onSendToFarm?.takeIf { farmChicksOf(batch, "") != null }
            if (!readOnly || batch.exportable || farmOffer != null) {
                BatchCardMenu(
                    isFinished = isFinished,
                    isHidden = batch.hidden,
                    readOnly = readOnly,
                    onEdit = onEdit,
                    onFinish = onFinish,
                    onHide = onHide,
                    onUnhide = onUnhide,
                    onReopen = onReopen,
                    onDelete = onDelete,
                    canExport = batch.exportable,
                    onExport = onExport,
                    onSendToFarm = farmOffer,
                    // Те же 16/8 dp, что и раньше: у кнопки свои 12 dp вокруг иконки,
                    // поэтому справа их 8, иначе троеточие отошло бы от края дальше
                    // остального. Ставит её ровно туда, где она стояла в строке.
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 8.dp),
                )
            }
        }
    }
}

/**
 * Троеточие карточки и его меню.
 *
 * «Убрать в архив» значит разное в зависимости от статуса, и это намеренно:
 * — «Инкубация»: закладка ещё идёт, и убрать её значит завершить. Открываются те же
 *   диалоги, что и по кнопке в шторке, — молчаливого пути к завершению быть не должно,
 *   он терял бы итог (сколько вывелось либо почему бросили);
 * — «Завершено»: итог уже записан, спрашивать нечего, и пункт просто прячет карточку
 *   из списка — отменяется он там же, «Вернуть в список».
 *
 * «Вернуть в инкубацию» стоит отдельным пунктом и только у завершённой: это не про
 * список, а про состояние закладки, и стирает её итог ([reopened]).
 *
 * Удаление отсюда сразу не удаляет — вопрос задаёт хозяин экрана: `ON DELETE CASCADE`
 * унесёт дни, замеры, овоскопирования и напоминания, и вернуть их нечем.
 *
 * «Экспортировать» — только у удачно завершённой закладки ([Batch.exportable]): файл
 * закладки — это её режим, предлагаемый как образец, а образцом бывает только то, что
 * сработало. У остальных пункта нет вовсе, а не неактивен: неактивный обещал бы, что
 * когда-нибудь появится, а у прерванной закладки не появится никогда.
 *
 * «В «Моё хозяйство»» — то же, что кнопка в поздравлении, для закладки, завершённой
 * раньше: поздравление закрыли, хозяйство поставили позже, выбор в нём смахнули. Только у
 * доведённой до срока закладки с птенцами и только когда хозяйство на телефоне есть; при
 * старой его версии пункт остаётся и спрашивает, обновить ли, — как поздравление.
 */
@Composable
private fun BatchCardMenu(
    isFinished: Boolean,
    isHidden: Boolean,
    /** Архивный инкубатор: остаются только пункты, что закладку читают, — экспорт и хозяйство. */
    readOnly: Boolean,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    canExport: Boolean,
    onExport: () -> Unit,
    /** «В «Моё хозяйство»»; `null` — пункта нет (хозяйства нет или птенцов нет). */
    onSendToFarm: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Действия с закладкой",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DesignPalette.Surface,
            shape = RoundedCornerShape(16.dp),
        ) {
            val farmItem: @Composable () -> Unit = {
                if (onSendToFarm != null) {
                    BatchMenuItem(
                        text = "В «Моё хозяйство»",
                        icon = R.drawable.baseline_cottage_24,
                    ) {
                        expanded = false
                        onSendToFarm()
                    }
                }
            }

            if (readOnly) {
                if (canExport) {
                    BatchMenuItem(
                        text = "Экспортировать расписание",
                        icon = R.drawable.baseline_share_24,
                    ) {
                        expanded = false
                        onExport()
                    }
                }
                farmItem()
                return@DropdownMenu
            }

            BatchMenuItem(
                text = "Редактировать",
                icon = R.drawable.baseline_create_24,
            ) {
                expanded = false
                onEdit()
            }

            when {
                !isFinished -> BatchMenuItem(
                    text = "Убрать в архив",
                    icon = R.drawable.baseline_archive_24,
                ) {
                    expanded = false
                    onFinish()
                }

                isHidden -> BatchMenuItem(
                    text = "Вернуть в список",
                    icon = R.drawable.baseline_unarchive_24,
                ) {
                    expanded = false
                    onUnhide()
                }

                else -> BatchMenuItem(
                    text = "Убрать в архив",
                    icon = R.drawable.baseline_archive_24,
                ) {
                    expanded = false
                    onHide()
                }
            }

            if (isFinished) {
                BatchMenuItem(
                    text = "Вернуть в инкубацию",
                    icon = R.drawable.baseline_egg_24,
                ) {
                    expanded = false
                    onReopen()
                }
            }

            if (canExport) {
                BatchMenuItem(
                    text = "Экспортировать расписание",
                    icon = R.drawable.baseline_share_24,
                ) {
                    expanded = false
                    onExport()
                }
            }
            farmItem()

            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
            BatchMenuItem(
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
private fun BatchMenuItem(
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

/** «Откуда» у событий «Моего хозяйства»: из поздравления или из меню карточки. */
private const val FARM_FROM_CELEBRATION = "поздравление"
private const val FARM_FROM_CARD = "карточка"

/**
 * «В «Моё хозяйство»» из меню карточки, когда стоящая версия хозяйства птенцов не
 * принимает. Вопрос, а не молчание: пункт виден потому, что хозяйство на телефоне есть, и
 * нажатие без ответа читалось бы как поломка — так уже было с кнопкой в поздравлении.
 */
@Composable
private fun FarmUpdateDialog(
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Обновите «Моё хозяйство»", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = "Ваша версия «Моего хозяйства» ещё не умеет принимать птенцов. " +
                    "Обновите её и снова выберите «В «Моё хозяйство»» в меню закладки.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(text = "Обновить", color = DesignPalette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Позже", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/**
 * Тот же вопрос, что и в форме закладки: удаление уносит расписание по дням, замеры,
 * овоскопирования и напоминания — по внешним ключам с `ON DELETE CASCADE`, и вернуть
 * это нечем.
 */
@Composable
private fun ConfirmDeleteBatchDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Удалить закладку?", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = "«$title» исчезнет вместе с режимом по дням, замерами и " +
                    "напоминаниями. Вернуть её будет нельзя.",
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

@Composable
private fun IconLabel(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Кольцо прогресса инкубации со значком вида птицы по центру (узел 5:1023).
 *
 * Диаметр — параметр, потому что кольцо рисует не только карточка закладки: сводка
 * в шторке закладки ставит его же, но меньше. Обводка и кегль эмодзи считаются от
 * диаметра, так что 64 dp из макета выглядят ровно как раньше. Параметр назван
 * `diameter`, а не `size`: внутри `drawBehind` `size` — это уже размер холста.
 *
 * Цвет дуги — тоже параметр, и считает его [progressRingColor]: зелёная дуга у
 * прерванной закладки говорила бы «идёт как надо» ровно там, где не пошло.
 */
@Composable
internal fun ProgressRing(
    fraction: Float,
    species: String,
    diameter: Dp = 64.dp,
    color: Color = DesignPalette.Accent,
) {
    // Оба цвета читаются здесь: `drawBehind` — не композиция, а [DesignPalette] в ней.
    val track = DesignPalette.ProgressTrack
    val accent = color
    Box(
        modifier = Modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val stroke = diameter.toPx() * 6f / 64f
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
        )
        SpeciesGlyph(bird = species, fontSize = (diameter.value * 22f / 64f).sp)
    }
}

/**
 * Цвет дуги кольца: акцентный зелёный у идущей закладки, тёмно-зелёный у выведенной,
 * красный у прерванной, серый у убранной в архив.
 *
 * Четыре цвета, потому что вопросов к закладке два разных: чем она кончилась (а это
 * `BatchStatus`, три ответа) и лежит ли она уже в архиве. Прерванная берёт `Expense` —
 * тот же красный, что у чипа «Прервано» и у кнопки досрочного завершения: красный
 * в приложении остаётся один. Доведённая до вывода берёт `ProgressDone`, ту же зелень
 * на ступень темнее: полное кольцо цвета «идёт» ничем не отличало записанный вывод от
 * закладки на последнем дне инкубации.
 *
 * **Архив старше исхода.** Убранную закладку человек сам отложил с глаз, и под фильтром
 * «Архив» список должен читаться ровно, а не кричать красным о том, что уже разобрано.
 * Исход при этом не теряется: рядом с чипом «Архив» стоит тот же цветной чип статуса.
 *
 * Серый взят `StatusDoneText`, а не `HeaderSurfaceArchived`:
 * второй — фон под белыми надписями шапки, и в тёмной теме (#4A453E) дуга на карточке
 * почти слилась бы с ней; первый — нейтральный цвет «Завершено», читаемый в обеих.
 */
@Composable
internal fun progressRingColor(status: BatchStatus, archived: Boolean): Color = when {
    archived -> DesignPalette.StatusDoneText
    status == BatchStatus.Stopped -> DesignPalette.Expense
    status == BatchStatus.Hatched -> DesignPalette.ProgressDone
    else -> DesignPalette.Accent
}

/** Пунктирная кнопка «Добавить …» (узлы 5:1110 и 11:3535). */
@Composable
internal fun DashedButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // Плюс по умолчанию: кнопка почти всегда что-то добавляет. «Внести замер» ставит
    // сюда термометр — она не добавляет закладку, и плюс перед ней обещал бы не то.
    @DrawableRes icon: Int = R.drawable.ic_plus_design,
) {
    val enabled = onClick != null
    Row(
        modifier = modifier
            .height(58.dp)
            .dashedSurface()
            .then(if (enabled) Modifier.clickable { onClick.invoke() } else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = DesignPalette.Accent.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = DesignType.ButtonLabel,
            color = DesignPalette.Accent.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/**
 * Квадратный пунктирный «+» — второй сегмент составной кнопки под списком закладок.
 *
 * Тот же пунктир, та же высота и то же скругление, что у [DashedButton] рядом: две
 * кнопки читаются как одна, разрезанная надвое, а не как две разные. Без подписи —
 * плюс на квадрате в этом приложении уже значит «новая закладка» (мини-кнопка в углу
 * рисует ровно его), а подпись отняла бы ширину у «Внести замер», ради которой
 * кнопку и разрезали.
 */
@Composable
private fun DashedIconButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .dashedSurface()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus_design),
            contentDescription = contentDescription,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Пунктирная поверхность кнопок под списком: заливка, скругление 22 dp и штрих 10/7. */
@Composable
private fun Modifier.dashedSurface(): Modifier {
    // Цвет читается здесь, а не внутри `drawBehind`: та лямбда — не композиция.
    val dashedBorder = DesignPalette.DashedBorder
    return this
        .clip(RoundedCornerShape(22.dp))
        .background(DesignPalette.DashedSurface)
        .drawBehind {
            drawRoundRect(
                color = dashedBorder,
                cornerRadius = CornerRadius(22.dp.toPx()),
                style = Stroke(
                    width = 0.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 7.dp.toPx()), 0f
                    ),
                ),
            )
        }
}

// --- Расчёт дат и дней ---------------------------------------------------------------------

/** Подписи карточки закладки, посчитанные один раз на каждую перерисовку списка. */
private data class BatchProgress(
    val fraction: Float,
    val dayLabel: String,
    val hatchLabel: String,
    val trailing: String?,
)

private fun batchProgress(batch: Batch, catalog: SpeciesCatalog): BatchProgress {
    val total = catalog.incubationDays(batch.type)
    val start = parseDate(batch.data)
    val ended = parseDate(batch.dateEnd)

    // Прерванная закладка не дошла до срока, и рисовать ей полный круг с «День 21/21»
    // значило бы показывать инкубацию, которой не было. День берётся из даты завершения:
    // это единственное, что о моменте остановки известно, и оно же — единственное отличие
    // этой ветки от следующей. «Выведено» тут тоже не годится: не вывелось ничего.
    if (batch.status == BatchStatus.Stopped) {
        val day = if (start != null && ended != null) {
            (daysBetween(start, ended) + 1).coerceAtLeast(1)
                .let { if (total != null) it.coerceAtMost(total) else it }
        } else {
            null
        }
        return BatchProgress(
            // `total > 0`, а не только «известен»: срок своего вида — это число строк в
            // базе, и ноль дал бы кольцу `Infinity`.
            fraction = if (day != null && total != null && total > 0) day / total.toFloat() else 0f,
            dayLabel = when {
                day != null && total != null -> "День $day/$total"
                day != null -> "День $day"
                else -> ""
            },
            hatchLabel = if (ended != null) "Прервано ${shortDate(ended)}" else "Прервано",
            trailing = null,
        )
    }

    if (batch.status == BatchStatus.Hatched) {
        return BatchProgress(
            fraction = 1f,
            dayLabel = if (total != null) "День $total/$total" else "",
            hatchLabel = if (ended != null) "Выведено ${shortDate(ended)}" else "Завершено",
            trailing = if (batch.eggAllEND > 0) "+${batch.eggAllEND} ${chicks(batch.eggAllEND)}" else null,
        )
    }

    if (start == null) return BatchProgress(0f, "", "", null)

    // День — от часа закладки, той же функцией, что в шторке (`incubationDay`).
    val day = incubationDay(batchStartMoment(batch), null, Date())
    if (total == null) {
        return BatchProgress(0f, "День $day", "", null)
    }

    val hatchDate = start.plusDays(total)
    // «Через N дн.» — про число вывода, и считается датами: вывод 30-го, сегодня 29-е —
    // «завтра», в каком бы часу ни смотрели.
    val left = daysBetween(today(), hatchDate)
    return BatchProgress(
        fraction = if (total > 0) day.coerceAtMost(total) / total.toFloat() else 0f,
        dayLabel = "День ${day.coerceAtMost(total)}/$total",
        hatchLabel = "Вывод ${shortDate(hatchDate)}",
        trailing = when {
            left > 1 -> "через $left дн."
            left == 1 -> "завтра"
            left == 0 -> "сегодня"
            else -> "срок вышел"
        },
    )
}

/**
 * Склонение «птенец». В макете стоит «+21 птенцов», но это ошибка русского языка,
 * поэтому здесь считаем по правилам.
 */
private fun chicks(count: Int): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return "птенцов"
    return when (count % 10) {
        1 -> "птенец"
        2, 3, 4 -> "птенца"
        else -> "птенцов"
    }
}

// --- Инкубатора нет ------------------------------------------------------------------------

/**
 * Экран вместо инкубатора, которого нет в базе.
 *
 * Два пути сюда. Инкубатор удалили, пока его экран стоял в стеке, — раньше подписка на
 * него падала, теперь это пустой ответ. И QR-код с другого телефона: код несёт номер
 * инкубатора в базе, и на чужом телефоне под этим номером либо ничего, либо другой прибор
 * (второй случай отсюда не отличить, и экран честно говорит только о первом). Одна кнопка
 * — назад к списку: делать здесь больше нечего.
 */
@Composable
private fun MissingIncubator(navigateBack: () -> Unit, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = navigateBack)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Все инкубаторы",
                style = DesignType.FieldValue.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(id = R.drawable.ic_qr_code_design),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Инкубатор не найден",
            style = DesignType.SheetTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Его удалили, или QR-код напечатан с другого телефона: код привязан к " +
                "инкубатору в том приложении, где его сделали.",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = navigateBack,
            colors = accentButtonColors(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("К списку инкубаторов", style = DesignType.ButtonLabel)
        }
        Spacer(Modifier.weight(1.4f))
    }
}
