package ru.zaroslikov.incubator.ui.batch

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.components.AutoCellText
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.rememberSheetDraft
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetPickerField
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.ui.components.ShowAllToggle
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.ui.incubator.ProgressRing
import ru.zaroslikov.incubator.ui.incubator.progressRingColor
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.shortDate
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import java.util.Date
import java.util.Locale


private val CardRadius = 22.dp
private val TileRadius = 16.dp
private val InnerRadius = 12.dp
private val CardBorderWidth = 0.8.dp

/**
 * Раскрытие дня в расписании — фиксированные 200 мс, а не пружина.
 *
 * Пружина по умолчанию идёт тем дольше, чем выше блок, и у дня с журналом замеров и
 * редактором режима — самого высокого, что тут бывает, — хвост тянулся заметно дольше,
 * чем у пустого дня. Одно время на любой высоте: день раскрывается одинаково, где бы
 * карточка ни стояла и сколько бы за неё ни было записано.
 */
private const val DayExpandDuration = 200

/**
 * Отметка переворота в замере. Переворот делают один раз за замер, поэтому в форме это
 * кнопка, а не счётчик; в колонку — числовую с девятой версии схемы — нажатая кнопка
 * кладёт эту единицу. Замер, где переворотов записано больше, кнопка не портит:
 * пока её не трогали, в форме лежит исходное число, и сохранится оно же.
 */
private const val TurnedMark = "1"

/** Сколько последних замеров видно в свёрнутой истории дня. */
internal const val VisibleMeasurements = 3

/** Шкала отклонения из макета: у температуры ±1.5°, у влажности ±10 %. */
internal const val TEMP_SCALE = 1.5
internal const val DAMP_SCALE = 10.0

/** Страницы шторки: свайп влево-вправо и переключатель под заголовком. */
private enum class BatchSheetTab(val title: String, @param:DrawableRes val icon: Int) {
    Overview("Обзор", R.drawable.ic_egg_design_16),
    Schedule("Расписание", R.drawable.ic_calendar_design),
}

/** Вкладки в том виде, в каком их принимает [SlidingTabSwitcher]; порядок — страницы пейджера. */
private val sheetTabs = BatchSheetTab.entries.map { SlidingTab(it.title, it.icon) }

/**
 * Шторка одной закладки — макет
 * [14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893).
 *
 * Шторка, а не экран: у макета скруглённый верх, «ручка» и крестик — значит она
 * появляется поверх списка закладок инкубатора. Маршрута в навигации у неё нет,
 * идентификатор приходит параметром и уезжает в [BatchDetailIntent.Load], как
 * и у [AddBatchSheet].
 *
 * Содержимое разложено по двум страницам, которые листаются свайпом (и переключателем
 * над ними): «Обзор» — сводка, замеры за сегодня и режим на завтра; «Расписание» —
 * список дней карточками нового дизайна.
 *
 * Отступления от макета, все намеренные:
 * — вкладок макет не рисует вовсе: расписание в нём отдельный экран. Но день правят
 *   ровно тогда, когда смотрят на замеры, и свайп между двумя страницами дешевле,
 *   чем уход со шторки и возврат. Отдельного экрана расписания в приложении больше
 *   нет вовсе — эта вкладка и есть единственное расписание;
 * — кнопка «Аналитика» открывает [BatchAnalyticsSheet] поверх этой шторки, а без
 *   единого замера погашена: считать за день было бы нечего;
 * — внизу «Обзора» — «Завершить инкубацию» ([FinishIncubationButton]), которой макет
 *   не рисует: закладку надо чем-то заканчивать, а другого пути к этому в приложении
 *   больше нет. Цвет кнопки и есть предупреждение — красная, пока срок не вышел;
 * — «Сохранено» из макета внизу нет вовсе: замер сохраняется сразу по «Записать
 *   замер», и вечно неактивная кнопка читалась бы как поломка. Закрывают шторку
 *   крестиком в шапке или свайпом вниз — как и любую другую;
 * — в день овоскопирования между сводкой и замерами встаёт [TodayCandlingCard]:
 *   овоскопирование выпадает на два-три дня из тридцати, и в такой день оно и есть
 *   то, ради чего закладку открыли. Само овоскопирование — [CandlingSheet], такая же
 *   шторка поверх этой, как и аналитика;
 * — четыре плитки-справки макета собраны в полосу внутри сводки — см. [SummaryCard].
 *
 * [readOnly] — закладка лежит в инкубаторе, убранном в архив. Шторка тогда показывает
 * ровно то же самое, но без единого способа что-либо записать: пропадают форма замера,
 * правка и удаление записанного, кнопки овоскопирования, редактор дня и «Завершить
 * инкубацию». Всё, что остаётся, — сводка, плитки, отклонения, журнал замеров и
 * аналитика, то есть чтение. Не «погашено», а убрано: неактивная форма на весь экран
 * читается как поломка, а объяснение стоит один раз плашкой над списком закладок.
 *
 * Тот же просмотр — и у **завершённой** закладки, откуда бы её ни открыли: у неё есть
 * итог и нет продолжения. Отдельного экрана для таких закладок больше нет — тап по
 * карточке «Завершено» и «Не завершено» открывает эту же шторку, — и запрет поэтому
 * считается внутри ([readOnly] `||` [BatchDetailUiState.finished]), а не приходит
 * параметром: список закладок инкубатора не единственный вход сюда, а статус закладки
 * шторка и так читает из базы сама.
 *
 * @param draft черновик формы замера: набранные показания переживают сворачивание
 * шторки, но не крестик и не уход с экрана. См. [SheetDraft].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchDetailSheet(
    batchId: Long,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    readOnly: Boolean = false,
    viewModel: BatchDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Одно состояние на всю шторку: и сводка, и расписание, и обе формы замера, и
    // посчитанные по ним итоги приходят одним снимком.
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val state = uiState.summary
    val measurements = uiState.measurements
    val form = uiState.form
    // Замер удаляется без следа, а строка списка узкая, и крестик соседствует с «Изм.» —
    // спрашиваем, как и при удалении закладки.
    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    // «Аналитика за день» — шторка поверх этой, как и диалог удаления: закладка под ней
    // остаётся, и закрытие аналитики возвращает ровно то, откуда её открыли. Хранится
    // день, а не «открыта / закрыта»: с вкладки «Расписание» её открывают для любого
    // прошедшего дня, не только для сегодняшнего. Хранится его id в `rememberSaveable`,
    // а не сама строка в `remember`: график аналитики раскрывают на весь экран в том
    // числе ради ландшафта, а поворот пересоздаёт активность — и `remember` закрывал бы
    // аналитику ровно в тот момент, ради которого её раскрыли. Строка находится по id в
    // расписании, когда база ответит; `0` — закрыто.
    var analyticsDayId by rememberSaveable(batchId) { mutableLongStateOf(0L) }
    val resolvedAnalyticsDay = if (analyticsDayId == 0L) null
    else uiState.schedule.firstOrNull { it.id == analyticsDayId }
    // Найденная строка держится, пока id тот же, и не сбрасывается, когда расписание на
    // миг пустеет: `Load` после пересоздания активности обнуляет состояние и читает его
    // заново, и шторка аналитики, привязанная напрямую к результату поиска, за этот кадр
    // успевала уйти из композиции и вернуться — уже без своего `rememberSaveable`
    // (график на весь экран закрывался ровно на повороте, ради которого его раскрыли).
    val analyticsDayState = remember(analyticsDayId) { mutableStateOf(resolvedAnalyticsDay) }
    SideEffect { if (resolvedAnalyticsDay != null) analyticsDayState.value = resolvedAnalyticsDay }
    val analyticsDay = analyticsDayState.value
    // Овоскопирование — тоже шторка поверх этой, а не отдельный экран: уходить с
    // закладки в тот момент, когда яйца из неё держат в руках, было незачем. Хранится
    // день: с «Расписания» его открывают для своей карточки, с «Обзора» — для сегодня.
    var candlingDay by remember(batchId) { mutableStateOf<Int?>(null) }
    // Завершение инкубации: диалог зависит от того, вышел ли срок, и открывается он
    // соседом шторки — как аналитика и овоскопирование.
    var showFinish by remember(batchId) { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { BatchSheetTab.entries.size })
    val scope = rememberCoroutineScope()

    // Закладка перечитывается при каждом открытии — её могли завершить, пока шторку
    // держали свёрнутой, — а форма замера сбрасывается только у отменённого черновика:
    // свёрнутую шторку открывают ровно с тем, что в ней набирали. См. [SheetDraft].
    LaunchedEffect(batchId) {
        send(BatchDetailIntent.Load(batchId, resetForm = draft.claim(batchId)))
    }

    // Крестик — отказ от набранного замера, в отличие от свайпа вниз, которым шторку
    // сворачивают (в том числе случайно, прокручивая её содержимое).
    val close = {
        draft.discard()
        onDismiss()
    }

    // «Завершено» — эффект, а не лямбда в кнопке диалога: закрыть шторку надо один раз
    // и ровно тогда, когда запись состоялась. Оба итога закрывают её: закладки в работе
    // больше нет, а список под шторкой перечитается сам, он на потоке.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            BatchDetailEffect.Finished -> {
                showFinish = false
                close()
            }
        }
    }

    // Черновик овоскопирования живёт в композиции этой шторки: она ему и есть «экран».
    val candlingDraft = rememberSheetDraft()

    // Две разные причины показывать закладку только на просмотр, и внутри шторки они
    // означают ровно одно и то же — писать некуда:
    // — [readOnly] пришёл снаружи: инкубатор убран в архив;
    // — закладка завершена, своим итогом или досрочно. Её дни прошли, итог записан, и
    //   любая запись задним числом переписывала бы то, что уже случилось. Вернуть её к
    //   правке можно там же, где завершали, — «Вернуть в инкубацию» в меню карточки.
    // Пока закладка не прочитана, тоже просмотр: до ответа базы неизвестно, завершена
    // она или нет, а форма, мелькнувшая над завершённой закладкой, — приглашение
    // нажать на то, чего сейчас не станет.
    val viewOnly = readOnly || !state.loaded || state.finished

    // Пока шторка открыта, плавающая кнопка таймера этой закладки не нужна: его
    // карточка стоит здесь же, в форме «Замеры за сегодня». См. [AiringTimerFab].
    if (state.loaded) RegisterTimerForm(uiState.timerTarget)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Шторка занимает всю доступную высоту: у страниц разная длина, а вкладки под
        // заголовком не должны прыгать вверх-вниз при свайпе. Прокручивается не она
        // сама, а каждая страница внутри.
        Column(modifier = Modifier.fillMaxHeight().clearFocusOnTap()) {
            Column(modifier = Modifier.padding(horizontal = SheetPadding)) {
                // Пустой заголовок на время чтения выглядел бы как оборванная шторка;
                // безымянная закладка бывает и прочитанной, так что запасное слово
                // годится в обоих случаях.
                SheetHeader(title = state.title.ifBlank { "Закладка" }, onClose = close)
                FormSpacer(12.dp)
                SlidingTabSwitcher(
                    tabs = sheetTabs,
                    position = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    },
                    onSelect = { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp,
            ) { page ->
                // Пока закладка не прочитана — колесо, а не пустая шторка: «Обзор» без
                // данных это нулевые счётчики и «За сегодня замеров не записано»,
                // то есть готовый ответ вместо его отсутствия.
                if (!state.loaded) {
                    LoadingBox()
                    return@HorizontalPager
                }
                when (BatchSheetTab.entries[page]) {
                    BatchSheetTab.Overview -> OverviewPage(
                        state = state,
                        measurements = measurements,
                        totals = uiState.totals,
                        rejected = uiState.rejectedTotal,
                        candlingToday = uiState.candlingToday,
                        onCandling = {
                            Analytics.report(Events.OPEN_CANDLING, mapOf("Вид" to state.type))
                            candlingDay = state.day
                        },
                        form = form,
                        onFormChange = { send(BatchDetailIntent.UpdateForm(it)) },
                        onSave = { send(BatchDetailIntent.Save) },
                        onCancelEdit = { send(BatchDetailIntent.CancelEdit) },
                        onEdit = { send(BatchDetailIntent.StartEdit(it)) },
                        onDelete = { pendingDelete = it },
                        onOpenAnalytics = {
                            Analytics.report(Events.OPEN_BATCH_ANALYTICS)
                            analyticsDayId = state.plannedToday?.id ?: 0L
                        },
                        onFinish = { showFinish = true },
                        readOnly = viewOnly,
                        timer = uiState.timerSlot,
                        onTimerAction = { send(BatchDetailIntent.AiringTimer(it)) },
                    )

                    BatchSheetTab.Schedule -> SchedulePage(
                        state = state,
                        schedule = uiState.schedule,
                        measurementsByDay = uiState.measurementsByDay,
                        edit = uiState.dayEdit,
                        form = uiState.dayForm,
                        onDayClick = { send(BatchDetailIntent.ToggleDayEdit(it)) },
                        onOpenAnalytics = { plan ->
                            Analytics.report(Events.OPEN_BATCH_ANALYTICS)
                            analyticsDayId = plan.id
                        },
                        onEditChange = { send(BatchDetailIntent.UpdateDayEdit(it)) },
                        onEditSave = { send(BatchDetailIntent.SaveDayEdit) },
                        onEditCancel = { send(BatchDetailIntent.CancelDayEdit) },
                        onFormChange = { send(BatchDetailIntent.UpdateDayForm(it)) },
                        onFormSave = { send(BatchDetailIntent.SaveDayForm) },
                        onFormCancel = { send(BatchDetailIntent.CancelDayForm) },
                        onEditMeasurement = { send(BatchDetailIntent.StartDayEdit(it)) },
                        onDeleteMeasurement = { pendingDelete = it },
                        onCandling = { day ->
                            Analytics.report(Events.OPEN_CANDLING, mapOf("Вид" to state.type))
                            candlingDay = day
                        },
                        readOnly = viewOnly,
                    )
                }
            }
        }
    }

    analyticsDay?.let { plan ->
        BatchAnalyticsSheet(
            measurements = uiState.measurementsByDay[plan.id].orEmpty(),
            plan = plan,
            onDismiss = { analyticsDayId = 0L },
        )
    }

    // Как и аналитика — соседом `ModalBottomSheet`, а не её содержимым: так шторка
    // овоскопирования попадает в собственное окно поверх закладки, и её закрытие
    // возвращает ровно тот день, с которого её открыли.
    candlingDay?.let { day ->
        CandlingSheet(
            batchId = batchId,
            day = day,
            type = state.type,
            stage = state.candlingStage(day),
            draft = candlingDraft,
            onDismiss = { candlingDay = null },
        )
    }

    // Завершение — тоже поверх шторки. Диалоги общие с меню карточки на экране
    // инкубатора, поэтому и живут отдельно. Закрывает шторку не кнопка диалога, а
    // эффект `Finished` выше: пока запись не прошла, закрывать нечего.
    if (showFinish) {
        FinishBatchDialogs(
            state = state,
            rejected = uiState.rejectedTotal,
            onDismiss = { showFinish = false },
            onFinish = { outcome -> send(BatchDetailIntent.Finish(outcome)) },
            onFinishEarly = { reason -> send(BatchDetailIntent.FinishEarly(reason)) },
        )
    }

    pendingDelete?.let { measurement ->
        // Цель — план того дня, которому замер принадлежит, а не сегодняшнего: удалять
        // теперь можно и запись за прошедший день, и отклонение в вопросе должно быть
        // то же самое, что в строке списка.
        val plan = uiState.schedule.firstOrNull { it.id == measurement.idValue }
            ?: state.plannedToday
        ConfirmDeleteMeasurementDialog(
            measurement = measurement,
            tempTarget = plan?.temp,
            dampTarget = plan?.damp,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                send(BatchDetailIntent.Delete(measurement))
                pendingDelete = null
            },
        )
    }
}

// --- Страница «Обзор» ----------------------------------------------------------------------

/** Прежнее содержимое шторки целиком: сводка, замеры за сегодня и режим на завтра. */
@Composable
private fun OverviewPage(
    state: BatchDetailUiState,
    measurements: List<Measurement>,
    /** Итог по всем замерам закладки; на «Обзоре» его показывает только завершённая. */
    totals: BatchTotals,
    rejected: Int,
    candlingToday: Candling?,
    onCandling: () -> Unit,
    form: MeasurementForm,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onOpenAnalytics: () -> Unit,
    onFinish: () -> Unit,
    readOnly: Boolean,
    timer: AiringTimerSlot,
    onTimerAction: (AiringTimerAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SheetPadding)
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        SummaryCard(state, rejected = rejected)

        // Овоскопирование выпадает на два-три дня из тридцати, и в такой день оно —
        // главное, что предстоит сделать с закладкой. Поэтому плашка стоит сразу под
        // сводкой, над замерами: замеры записывают каждый день, а это — сегодня.
        // Завершённой закладки это не касается: там просвечивать уже нечего.
        val stage = if (state.finished) 0 else state.candlingStage(state.day)
        if (stage > 0) {
            FormSpacer(16.dp)
            TodayCandlingCard(
                stage = stage,
                day = state.day,
                done = candlingToday,
                onStart = onCandling,
                readOnly = readOnly,
            )
        }

        // «Замеры за сегодня» — карточка про текущий день, и у завершённой закладки
        // текущего дня нет. Она показывала последний день инкубации: обычно пустой (в
        // день, когда закладку снимают, замеров уже не делают), а если не пустой — то
        // один день из тридцати, выбранный не пользователем, а тем, что он оказался
        // последним. Ни «сегодня», ни итог: весь журнал разложен по дням на
        // «Расписании», и аналитика любого дня открывается оттуда же.
        //
        // Её место занимает то, что о завершённой закладке действительно спрашивают, —
        // почему она кончилась. Ответ есть только у снятой досрочно: пустая
        // `endReason` у завершённой означает «прошла свой срок», и это уже сказано
        // строкой «Завершено» в сводке над карточкой.
        if (state.finished) {
            // Сперва почему кончилась, потом как её вели: причина — про исход, итог по
            // замерам — про путь к нему, и читаются они в этом порядке.
            if (state.endReason.isNotBlank()) {
                FormSpacer(16.dp)
                EndReasonCard(reason = state.endReason)
            }
            FormSpacer(16.dp)
            BatchTotalsCard(totals)
        } else {
            FormSpacer(16.dp)
            MeasurementsCard(
                plan = state.plannedToday,
                // Ключ по дню: наступил новый день — история другая, и разворот от
                // старой к ней не относится.
                historyKey = state.batchId to state.day,
                canRecord = state.canRecord,
                autoTurn = state.autoTurn,
                autoAiring = state.autoAiring,
                measurements = measurements,
                form = form,
                onFormChange = onFormChange,
                onSave = onSave,
                onCancelEdit = onCancelEdit,
                onEdit = onEdit,
                onDelete = onDelete,
                onOpenAnalytics = onOpenAnalytics,
                emptyText = if (readOnly) "За сегодня замеров не записано."
                else "Пока нет замеров за сегодня. Записывайте показания в течение дня.",
                readOnly = readOnly,
                timer = timer,
                onTimerAction = onTimerAction,
            )
        }

        // «Завтра» у завершённой закладки не наступит: режим следующего дня — обещание,
        // а обещать нечего. Весь её режим целиком лежит на «Расписании».
        state.plannedTomorrow?.takeIf { !state.finished }?.let { tomorrow ->
            FormSpacer(16.dp)
            TomorrowCard(
                day = state.day + 1,
                candlingStage = state.candlingStage(state.day + 1),
                plan = tomorrow,
                autoTurn = state.autoTurn,
                autoAiring = state.autoAiring,
            )
        }

        // Завершение — у завершённой закладки завершать нечего, и кнопки нет.
        // В архивном инкубаторе её нет и у идущей: завершение записывает итог,
        // то есть меняет закладку.
        if (!state.finished && !readOnly) {
            FormSpacer(16.dp)
            FinishIncubationButton(state = state, onClick = onFinish)
        }
    }
}

/**
 * «Почему завершена досрочно» — причина остановки под сводкой закладки.
 *
 * Красная пара [DesignPalette.Expense] / [DesignPalette.ExpenseSurface] — та же, что у
 * чипа «Не завершено» на карточке и у кнопки «Завершить досрочно», которой эту причину
 * и вписали: карточка тогда читается как продолжение того нажатия, а не как ещё одна
 * заметка. Причина — свободный текст из диалога завершения, поэтому она не обрезается
 * ни по строкам, ни многоточием: «Отключали свет на двое суток, температура упала до
 * 30» это ровно то, ради чего поле спрашивают.
 *
 * Карточки нет вовсе, когда причина пуста. У закладки, дошедшей до срока, её и не
 * бывает, а плашка «Причина: —» сообщила бы о нормально выведенной партии, что с ней
 * что-то не так.
 */
@Composable
private fun EndReasonCard(reason: String) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = DesignPalette.ExpenseSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Завершена досрочно",
                style = DesignType.CardHeading,
                color = DesignPalette.Expense,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = reason,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * «Итог по замерам» — та же дюжина чисел, что и в «Аналитике за день», но за всю
 * инкубацию. Стоит на «Обзоре» завершённой закладки, на месте карточки «Замеры за
 * сегодня»: у закладки, которая кончилась, сегодня нет, а вопрос «как её вели» есть.
 *
 * Ряды повторяют сетку [BatchAnalyticsSheet] и в том же порядке — счётчики, температура,
 * влажность: это те же вопросы в другом масштабе, и человек, открывавший аналитику дня,
 * читает эту карточку не заново. Само число считает [batchTotals], здесь только вёрстка.
 *
 * Подпись под заголовком — охват: «замеры есть в 26 днях из 28». Без неё средняя
 * температура по трём замерам за месяц выглядит так же уверенно, как по двумстам, а это
 * разные утверждения. Знаменатель — дни, которые закладка **прошла**, а не длина
 * расписания; см. [BatchTotals.daysRun].
 *
 * Плитки — те же `StatTile` и `StatRow`, что и в аналитике дня, только с заливкой
 * [DesignPalette.MeasureTile] вместо белого с обводкой: карточка сама белая. Своя
 * копия тут была бы копией с точностью до двух цветов — а размер шрифта в ней
 * подобран под четверть ширины экрана, и разойдясь однажды, две плитки начали бы
 * обрезать числа каждая по-своему.
 *
 * Замеров нет вовсе — карточка остаётся и говорит об этом словами. Тут она отличается
 * от [EndReasonCard], которая при пустой причине исчезает: пустая причина означает
 * «ничего не случилось», и это уже сказано словом «Завершено» в сводке, а вот
 * «замеров не записали» не сказано больше нигде — и без этой строки «Обзор» выглядел бы
 * так, будто данные потерялись.
 */
@Composable
private fun BatchTotalsCard(totals: BatchTotals) {
    val unit = LocalUnits.current.temperature
    SheetCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Итог по замерам",
                style = DesignType.CardHeading,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (totals.isEmpty) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "За эту закладку замеров не записывали.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@SheetCard
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = coverageLine(totals),
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalsRow {
                    TotalsTile("Замеров", totals.measurements.toString())
                    TotalsTile("Переворотов", totals.turns.toString())
                    TotalsTile("Проветриваний", totals.airings.toString())
                    TotalsTile(
                        // Единица в подписи, а не при числе: «130 мин» вместе с пробелом
                        // не влезает в четверть ширины и обрезается в «130 …», а
                        // подписи перенос по слогам разрешён. Аналитика дня пишет эту
                        // плитку так же и по той же причине — плитка у них одна.
                        label = "Проветрено, мин",
                        value = totals.airingMinutes.toString(),
                        // Единственная коричневая плитка — как и в аналитике дня.
                        valueColor = DesignPalette.DateEmphasis,
                    )
                }
                TotalsRow {
                    TotalsTile("Средняя t", totals.tempAvg.asTemp(unit))
                    TotalsTile("Разброс", totals.tempSpread.asTempDelta(unit))
                    TotalsTile("Минимум", totals.tempMin.asTemp(unit))
                    TotalsTile("Максимум", totals.tempMax.asTemp(unit))
                }
                TotalsRow {
                    TotalsTile("Ср. влажность", totals.dampAvg.asDamp())
                    TotalsTile("Разброс", totals.dampSpread.asDamp())
                    TotalsTile("Минимум", totals.dampMin.asDamp())
                    TotalsTile("Максимум", totals.dampMax.asDamp())
                }
            }

            val insight = remember(totals, unit) { totalsInsightParts(totals, unit) }
            if (insight.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = insightAnnotated(insight),
                    style = DesignType.Body,
                    color = DesignPalette.Accent,
                )
            }
        }
    }
}

/**
 * «Дней с замерами: 26 из 28» — охват сводки.
 *
 * Число стоит после существительного намеренно: «в 26 днях» требует предложного падежа
 * («в 1 дне», «в 2 днях»), то есть ещё одной таблицы склонений рядом с `plural`, а
 * сказать это можно и без неё.
 *
 * Знаменатель пропадает, когда дней закладки не знает никто (расписание пустое): «из 0»
 * хуже, чем ничего.
 */
private fun coverageLine(totals: BatchTotals): String =
    if (totals.daysRun > 0) "Дней с замерами: ${totals.daysMeasured} из ${totals.daysRun}"
    else "Дней с замерами: ${totals.daysMeasured}"

/**
 * Ряд плиток итога — `StatRow` аналитики дня, только с заливкой вместо обводки: карточка
 * белая, и белая плитка на ней не читалась бы.
 */
@Composable
private fun TotalsRow(content: @Composable RowScope.() -> Unit) = StatRow(content)

@Composable
private fun RowScope.TotalsTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) = StatTile(
    label = label,
    value = value,
    valueColor = valueColor,
    surface = DesignPalette.MeasureTile,
    border = null,
)

/**
 * «Завершить инкубацию» внизу «Обзора».
 *
 * Цвет — это и есть предупреждение: пока срок не вышел, кнопка красная
 * ([DesignPalette.Expense]), в последний и предпоследний день — зелёная
 * ([canFinishIncubation]). Кнопка при этом остаётся нажимаемой в обоих случаях:
 * досрочно закладку завершают не по ошибке, а потому что она погибла, и запрещать это
 * приложение не вправе. Подпись под кнопкой говорит словами то же, что цвет.
 *
 * Почему граница «последний или предпоследний», а не «последний»: птенцы наклёвываются
 * не строго по календарю, и человек, у которого лоток уже пищит на день раньше срока,
 * не должен видеть красную кнопку с укором.
 *
 * **Красная — только контуром, залита бывает одна зелёная.** Залиты были обе, и это
 * переворачивало экран вверх ногами: девятнадцать дней из двадцати одного самым крупным
 * и самым ярким на «Обзоре» был сплошной красный прямоугольник во всю ширину — под
 * которым написано, что нажимать его ещё рано, — а «Записать замер», то самое, ради чего
 * закладку открывают каждый день, стояло выше и бледнее. Предупреждение от контура
 * никуда не делось (красный остался красным, и подпись под кнопкой прежняя), но вес
 * залитой кнопки достаётся тому единственному дню, когда нажать её действительно
 * зовут.
 */
@Composable
private fun FinishIncubationButton(state: BatchDetailUiState, onClick: () -> Unit) {
    val ready = state.readyToFinish
    val accent = if (ready) DesignPalette.Accent else DesignPalette.Expense

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(TileRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (ready) accent else Color.Transparent,
            // Надпись идёт за заливкой: на зелёном — [DesignPalette.OnAccent] (в тёмной
            // теме он тёмный), а у контурной кнопки надпись того же красного, что и рамка.
            contentColor = if (ready) DesignPalette.OnAccent else accent,
        ),
        border = if (ready) null else BorderStroke(1.dp, accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text = "Завершить инкубацию", style = DesignType.ButtonLabel)
    }

    FormSpacer(6.dp)
    Text(
        text = finishHint(state),
        style = DesignType.Caption,
        color = accent,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Подпись под кнопкой завершения: почему она такого цвета. */
private fun finishHint(state: BatchDetailUiState): String {
    val total = state.totalDays
    if (!state.readyToFinish) {
        val left = state.daysLeft
        return if (left == null) "Срок инкубации для этого вида неизвестен"
        else "Ещё рано: до конца инкубации ${daysWord(left)}"
    }
    return when {
        total == null -> "Закладку можно завершать"
        state.day >= total -> "Срок вышел: день ${state.day} из $total"
        else -> "Последний день впереди — завершать уже можно"
    }
}

private fun daysWord(days: Int): String = when {
    days % 100 in 11..14 -> "$days дней"
    days % 10 == 1 -> "$days день"
    days % 10 in 2..4 -> "$days дня"
    else -> "$days дней"
}

/**
 * Предупреждение «сегодня овоскопирование» на «Обзоре».
 *
 * Не плашка [CandlingCard] из карточки дня: там это подпись к строке расписания рядом с
 * тридцатью такими же, а здесь — то единственное, что сегодня отличает эту закладку от
 * вчерашней. Отсюда и кнопка во всю ширину.
 *
 * Что на овоскопировании ищут ([stageGoal]), карточка не рассказывает — это абзац текста
 * поверх сводки, а «Обзор» открывают ради замеров за сегодня, и абзац отодвигал бы их
 * вниз каждый второй раз. Объяснение ждёт на самом экране овоскопирования, куда ведёт
 * кнопка; здесь остаются только повод и вход.
 *
 * Проведённое сегодня овоскопирование карточку не убирает: она меняет тон на «уже
 * сделано» и оставляет вход — итог правят, вернувшись тем же путём. Убери её, и
 * исправить опечатку в числе выбракованных было бы неоткуда.
 */
@Composable
private fun TodayCandlingCard(
    stage: Int,
    day: Int,
    done: Candling?,
    onStart: () -> Unit,
    readOnly: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = DesignPalette.SpeciesTileSelected,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DesignPalette.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ovos2),
                        contentDescription = null,
                        tint = DesignPalette.DateEmphasis,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Сегодня — овоскопирование",
                        style = DesignType.CardHeading,
                        color = DesignPalette.DateEmphasis,
                    )
                    Text(
                        text = "${stageTitle(stage)} · День $day",
                        style = DesignType.Caption,
                        color = DesignPalette.DateEmphasis,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            done?.let { result ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Уже проведено: отбраковано ${result.rejected} " +
                        eggsWord(result.rejected) + ".",
                    style = DesignType.CaptionEmphasis,
                    color = DesignPalette.Accent,
                )
            }

            // Кнопки в архивном инкубаторе нет: и «начать», и «открыть» ведут в шторку
            // овоскопирования, а она записывает отбраковку. Сама плашка остаётся —
            // она рассказывает, что за день выпал, и это чтение.
            if (!readOnly) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(TileRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.Accent,
                        contentColor = DesignPalette.OnAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = if (done == null) "Начать овоскопирование"
                        else "Открыть овоскопирование",
                        style = DesignType.ButtonLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// --- Страница «Расписание» -----------------------------------------------------------------

/**
 * Список дней закладки, карточками нового дизайна и прямо в шторке. Другого расписания
 * в приложении нет: экран на весь экран убран за ненадобностью.
 *
 * День правится на месте: карточка раскрывается в форму с четырьмя полями плана и
 * примечанием. Отдельного экрана правки дня в приложении больше нет — уход со шторки
 * ради одной цифры и был тем, от чего избавляет свайп.
 *
 * Рядом с планом дня в раскрытой карточке лежит и факт: записанные за тот день замеры
 * и кнопка аналитики. У прошедшего дня это единственное место, где их видно, — вкладка
 * «Обзор» показывает только сегодняшние.
 */
@Composable
private fun SchedulePage(
    state: BatchDetailUiState,
    schedule: List<Value>,
    measurementsByDay: Map<Long, List<Measurement>>,
    edit: ValueUiState?,
    form: MeasurementForm,
    onDayClick: (Value) -> Unit,
    onOpenAnalytics: (Value) -> Unit,
    onEditChange: (ValueUiState) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
    onFormChange: (MeasurementForm) -> Unit,
    onFormSave: () -> Unit,
    onFormCancel: () -> Unit,
    onEditMeasurement: (Measurement) -> Unit,
    onDeleteMeasurement: (Measurement) -> Unit,
    onCandling: (Int) -> Unit,
    readOnly: Boolean = false,
) {
    val listState = rememberLazyListState()

    // Список открывается на сегодняшнем дне: у гусиной закладки их тридцать, и мотать
    // до текущего руками пришлось бы при каждом открытии. Ключ — длина расписания,
    // поэтому прокрутка случается один раз, когда список доехал из базы, и не отменяет
    // потом ту, что сделал пользователь.
    LaunchedEffect(schedule.size, state.day) {
        if (schedule.isEmpty()) return@LaunchedEffect
        // День 1 лежит нулевым, но над днями есть карточка-шапка — сдвиги гасят друг друга.
        listState.scrollToItem(state.day.coerceIn(0, schedule.size))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SheetPadding,
            end = SheetPadding,
            top = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Режим по дням",
                    style = DesignType.CardHeading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    // Подпись обещает ровно то, что день умеет: в архивном инкубаторе
                    // карточка раскрывается, но правит в ней нечего.
                    text = if (readOnly) "Нажмите на день, чтобы посмотреть замеры"
                    else "Нажмите на день, чтобы изменить режим",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(schedule, key = { it.id }) { plan ->
            ScheduleDayCard(
                plan = plan,
                state = state,
                measurements = measurementsByDay[plan.id].orEmpty(),
                edit = edit?.takeIf { it.id == plan.id },
                form = form,
                onClick = { onDayClick(plan) },
                onOpenAnalytics = { onOpenAnalytics(plan) },
                onEditChange = onEditChange,
                onEditSave = onEditSave,
                onEditCancel = onEditCancel,
                onFormChange = onFormChange,
                onFormSave = onFormSave,
                onFormCancel = onFormCancel,
                onEditMeasurement = onEditMeasurement,
                onDeleteMeasurement = onDeleteMeasurement,
                onCandling = { onCandling(plan.day) },
                readOnly = readOnly,
            )
        }
    }
}

/**
 * Карточка одного дня расписания.
 *
 * Свёрнутая повторяет прежний экран: температура, влажность, перевороты, проветривания
 * и примечание — только плашками, как в «Завтра — День N», а не пятью строками текста.
 * Сегодняшний день обведён зелёным (на старом экране он был обведён чёрным), день
 * овоскопирования получает свою кнопку справа. Число замеров стоит в подписи дня: иначе
 * пришлось бы раскрывать каждый прошедший день подряд, чтобы найти, где что записано.
 *
 * Раскрытая добавляет к плану факт — замеры того дня: форму записи, журнал и кнопку
 * аналитики. Замер отсюда пишется и правится задним числом, и день у него тот, чья
 * карточка раскрыта, а не сегодняшний.
 */
@Composable
private fun ScheduleDayCard(
    plan: Value,
    state: BatchDetailUiState,
    measurements: List<Measurement>,
    edit: ValueUiState?,
    form: MeasurementForm,
    onClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onEditChange: (ValueUiState) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
    onFormChange: (MeasurementForm) -> Unit,
    onFormSave: () -> Unit,
    onFormCancel: () -> Unit,
    onEditMeasurement: (Measurement) -> Unit,
    onDeleteMeasurement: (Measurement) -> Unit,
    onCandling: () -> Unit,
    readOnly: Boolean = false,
) {
    val isToday = plan.day == state.day
    val expanded = edit != null
    val candlingStage = state.candlingStage(plan.day)

    // Раскрывает карточку тап в любое её место, а не только по строке дня. Обработчик —
    // собственный у Card, а не Modifier.clickable поверх: тогда карточка сама рисует
    // нажатие по своей форме, а вложенные кнопки и поля перехватывают тап раньше неё.
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            if (isToday) 1.4.dp else CardBorderWidth,
            if (isToday) DesignPalette.Accent else DesignPalette.CardBorder,
        ),
        // Своего `animateContentSize` у карточки нет: высоту меняют только два блока
        // ниже, и каждый ведёт её сам. Со вторым аниматором поверх карточка гналась за
        // уже едущей высотой — и приезжала заметно позже содержимого.
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DayBadge(day = plan.day, isToday = isToday, isPast = plan.day < state.day)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "День ${plan.day}",
                        style = DesignType.ListItemTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // «14 сент. · сегодня · 3 замера» на крупном кегле не влезает в
                    // строку; обрезанный хвост показывается по нажатию, а не теряется.
                    TruncatedText(
                        text = dayCaption(plan.day, state, measurements.size),
                        style = DesignType.Caption,
                        color = if (isToday) DesignPalette.Accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // Стрелка одна и поворачивается, а не подменяется второй: `ic_arrow_up_design`
                // — это та же стрелка, отражённая на 180°, и поворот показывает, что день
                // раскрылся, ровно тем же движением, каким выезжает его содержимое.
                val chevronTurn by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(durationMillis = DayExpandDuration),
                    label = "day-chevron",
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_down_design),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronTurn },
                )
            }

            Spacer(Modifier.height(10.dp))

            // План и раскрытый день не подменяют друг друга рывком: свёрнутый блок
            // сжимается, раскрытый растёт из нуля, и оба идут через ту же пару
            // expandVertically + fadeIn, что и подписи в форме замера. Высоту карточки
            // они и задают — она просто укладывается по ним каждый кадр.
            AnimatedVisibility(
                visible = edit == null,
                enter = expandVertically(tween(DayExpandDuration)) +
                    fadeIn(tween(DayExpandDuration)),
                exit = shrinkVertically(tween(DayExpandDuration)) +
                    fadeOut(tween(DayExpandDuration)),
            ) {
                Column {
                    val unit = LocalUnits.current.temperature
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModePill(unit.fieldLabel, plan.temp.formatTempOrDash(unit), Modifier.weight(1f))
                        ModePill(
                            "ВЛАЖН.",
                            plan.damp?.let { "${it.formatDamp()}%" } ?: "—",
                            Modifier.weight(1f),
                        )
                        ModePill(
                            "ПЕРЕВ.",
                            planTurnLabel(plan.over, state.autoTurn),
                            Modifier.weight(1f),
                        )
                        ModePill(
                            "ПРОВЕТ.",
                            planAiringPill(plan.airingCount, plan.airingTime, state.autoAiring),
                            Modifier.weight(1f),
                        )
                    }
                    // Плашка овоскопирования — это кнопка в шторку, где записывают
                    // отбраковку: в архивном инкубаторе её нет, как и на «Обзоре».
                    if (!readOnly) {
                        candlingHint(candlingStage)?.let { hint ->
                            Spacer(Modifier.height(8.dp))
                            CandlingCard(hint = hint, onClick = onCandling)
                        }
                    }
                    if (plan.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = plan.note,
                            style = DesignType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Уезжающий блок дорисовывается уже с пустым `edit`, поэтому последнее
            // непустое состояние запоминается: иначе редактор не сжимался бы, а пропадал
            // на первом же кадре сворачивания.
            var lastEdit by remember { mutableStateOf(edit) }
            if (edit != null) lastEdit = edit
            AnimatedVisibility(
                visible = edit != null,
                enter = expandVertically(tween(DayExpandDuration)) +
                    fadeIn(tween(DayExpandDuration)),
                exit = shrinkVertically(tween(DayExpandDuration)) +
                    fadeOut(tween(DayExpandDuration)),
            ) {
                // Факт выше плана: у прошедшего дня смотреть — и дописывать — приходят
                // именно замеры, а режим правят реже. У будущего дня замеров нет и быть
                // не может: блок молча схлопывается, и форма плана оказывается первой.
                val dayCame = plan.day <= state.day
                // Записывать в архивном инкубаторе нечего — ни задним числом, ни
                // сегодняшним: раскрытый день остаётся журналом замеров.
                val canRecord = dayCame && !readOnly
                Column {
                    DayMeasurements(
                        measurements = measurements,
                        plan = plan,
                        canRecord = canRecord,
                        form = form,
                        onFormChange = onFormChange,
                        onSave = onFormSave,
                        onCancelEdit = onFormCancel,
                        onEdit = onEditMeasurement,
                        onDelete = onDeleteMeasurement,
                        onOpenAnalytics = onOpenAnalytics,
                        readOnly = readOnly,
                        autoTurn = state.autoTurn,
                        autoAiring = state.autoAiring,
                    )
                    if (!readOnly) {
                        if (measurements.isNotEmpty() || canRecord) {
                            Spacer(Modifier.height(12.dp))
                        }
                        // Редактор плана дня — единственное, ради чего карточка вообще
                        // раскрывалась в первой версии; в архиве от неё остаётся факт.
                        lastEdit?.let { current ->
                            DayEditor(
                                edit = current,
                                onChange = onEditChange,
                                onSave = onEditSave,
                                onCancel = onEditCancel,
                                autoTurn = state.autoTurn,
                                autoAiring = state.autoAiring,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Замеры одного дня расписания в раскрытой карточке — те же форма, строки и кнопка
 * аналитики, что и в «Замерах за сегодня».
 *
 * Здесь замер и дописывают задним числом: строка дня уже существует, замер привязывается
 * к ней, и день от этого не зависит ни от календарной даты, ни от часов телефона. Поле
 * времени в форме стоит всегда — у прошедшего дня «сейчас» не значит ничего.
 *
 * [canRecord] выключает форму у дней, которые ещё не наступили: замер за послезавтра —
 * это не запись задним числом, а бессмыслица.
 */
@Composable
private fun DayMeasurements(
    measurements: List<Measurement>,
    plan: Value,
    canRecord: Boolean,
    form: MeasurementForm,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onOpenAnalytics: () -> Unit,
    readOnly: Boolean = false,
    autoTurn: Boolean = false,
    autoAiring: Boolean = false,
) {
    // Свёрнуто — только последние: за день замеров набирается сколько угодно, а карточка
    // дня стоит в списке из тридцати таких же. Ключ по дню — свой разворот у каждого.
    var historyExpanded by remember(plan.id) { mutableStateOf(false) }

    // Правку начинают из строки, но открыть замер можно и из хвоста списка: обводка
    // редактируемого не должна остаться за границей свёрнутой истории.
    val editingHidden = form.editingId != 0L &&
        measurements.indexOfFirst { it.id == form.editingId } >= VisibleMeasurements
    LaunchedEffect(form.editingId, editingHidden) {
        if (editingHidden) historyExpanded = true
    }

    // Обычно пустой будущий день сворачивается молча: писать в него нельзя, показывать
    // нечего. В архивном инкубаторе так молчал бы любой день без замеров — карточка
    // раскрывалась бы в пустоту, — поэтому там строка остаётся и говорит об этом словами.
    if (measurements.isEmpty() && !canRecord && !readOnly) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Замеры за день",
            style = DesignType.CardHeading,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnalyticsChip(enabled = measurements.isNotEmpty(), onClick = onOpenAnalytics)
    }

    if (canRecord) {
        Spacer(Modifier.height(12.dp))
        MeasurementEntry(
            form = form,
            canRecord = true,
            onFormChange = onFormChange,
            onSave = onSave,
            onCancelEdit = onCancelEdit,
            alwaysShowTime = true,
            autoTurn = autoTurn,
            autoAiring = autoAiring,
        )
    }

    if (measurements.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "За этот день ничего не записано.",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Column(Modifier.animateContentSize()) {
        val shown =
            if (historyExpanded) measurements else measurements.take(VisibleMeasurements)
        shown.forEach { measurement ->
            Spacer(Modifier.height(12.dp))
            MeasurementRow(
                measurement = measurement,
                tempTarget = plan.temp,
                dampTarget = plan.damp,
                onEdit = if (readOnly) null else ({ onEdit(measurement) }),
                onDelete = if (readOnly) null else ({ onDelete(measurement) }),
                selected = form.editingId == measurement.id,
            )
        }
    }
    if (measurements.size > VisibleMeasurements) {
        Spacer(Modifier.height(12.dp))
        ShowAllToggle(
            expanded = historyExpanded,
            total = measurements.size,
            onClick = { historyExpanded = !historyExpanded },
        )
    }
}

/** Номер дня квадратиком слева; сегодняшний залит акцентом. */
@Composable
private fun DayBadge(day: Int, isToday: Boolean, isPast: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(InnerRadius))
            .background(if (isToday) DesignPalette.Accent else DesignPalette.PillSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = DesignType.PillValue,
            color = when {
                isToday -> DesignPalette.OnAccent
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Плашка овоскопирования в карточке дня: что это за овоскопирование и переход к нему.
 *
 * Заменила круглую кнопку в строке дня. Текст сам по себе висел в карточке ни к чему не
 * прикреплённым, а кнопка сама по себе не говорила, какое это овоскопирование по счёту;
 * вместе они — одна цель нажатия. Свой `onClick` у Card перехватывает тап раньше
 * карточки дня, поэтому день от него не раскрывается.
 */
@Composable
private fun CandlingCard(hint: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.SpeciesTileSelected),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ovos2),
                contentDescription = null,
                tint = DesignPalette.DateEmphasis,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = hint,
                style = DesignType.Caption,
                color = DesignPalette.DateEmphasis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_design),
                contentDescription = "Овоскопирование",
                tint = DesignPalette.DateEmphasis,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Что это за овоскопирование — подпись под режимом дня, у которого оно есть.
 *
 * Номер приходит из `SpeciesCatalog.candlingStage`, поэтому у перепелов, где
 * овоскопирований два, третья строка не появляется. `null` — в этот день
 * овоскопирования нет и подписи тоже. У своего вида их может быть и больше трёх: всё,
 * что после второго, — та же финальная проверка, только повторённая.
 */
private fun candlingHint(stage: Int): String? = when {
    stage == 1 -> "Первое овоскопирование — проверка развития эмбриона"
    stage == 2 -> "Второе овоскопирование — контроль роста"
    stage >= 3 -> "${stageTitle(stage)} — финальная проверка"
    else -> null
}

/**
 * «8 авг. · прошёл · 3 замера» — дата дня, его положение относительно текущего и сколько
 * за него записано.
 *
 * Дата считается от начала закладки, а не хранится: в расписании её нет, там только
 * номер дня. Даты нет вовсе, если дата закладки не разобралась, — тогда остаётся статус.
 */
private fun dayCaption(day: Int, state: BatchDetailUiState, measurements: Int): String {
    // У завершённой закладки «сегодня» стоит на дне, которым она кончилась, а дни после
    // него уже не наступят. «Завтра» и «через 5 дн.» о партии, снятой неделю назад,
    // обещали бы инкубацию, которой не будет; на них и держится разница двух веток.
    val status = when {
        state.finished -> when {
            day < state.day -> "прошёл"
            day == state.day && state.stoppedEarly -> "день остановки"
            day == state.day -> "последний день"
            else -> "не наступил"
        }

        day == state.day -> "сегодня"
        day == state.day + 1 -> "завтра"
        day < state.day -> "прошёл"
        else -> "через ${day - state.day} дн."
    }
    val date = state.startDate?.plusDays(day - 1)?.let { shortDate(it) }
    return listOfNotNull(
        date,
        status,
        if (measurements > 0) "$measurements ${measurementsWord(measurements)}" else null,
    ).joinToString(" · ")
}

/** «1 замер», «3 замера», «12 замеров» — иначе подпись дня читается как машинный вывод. */
private fun measurementsWord(count: Int): String {
    if (count % 100 in 11..14) return "замеров"
    return when (count % 10) {
        1 -> "замер"
        2, 3, 4 -> "замера"
        else -> "замеров"
    }
}

/**
 * Правка плана дня прямо в карточке — единственное место, где план дня меняют.
 *
 * Правится план, а не факт: замеры висят на идентификаторе строки дня, он при
 * сохранении не меняется, и записанные показания остаются при своём дне.
 */
@Composable
private fun DayEditor(
    edit: ValueUiState,
    onChange: (ValueUiState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    autoTurn: Boolean = false,
    autoAiring: Boolean = false,
) {
    val unit = LocalUnits.current.temperature
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = DesignPalette.MeasureForm,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Над формой лежит журнал замеров, и без подписи два блока цифр читались бы
            // как один: тут план, там факт.
            Text(
                text = "План на день",
                style = DesignType.CaptionEmphasis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                NumberField(
                    label = unit.fieldLabel,
                    value = edit.temp,
                    onValueChange = { onChange(edit.copy(temp = it.filterMeasureInput(unit.integerDigits))) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "ВЛАЖ.%",
                    value = edit.damp,
                    onValueChange = { onChange(edit.copy(damp = it.filterMeasureInput())) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))
            // Норма дня — счётчики, а не текст: «сколько раз переворачивать» и «сколько
            // раз по сколько минут проветривать». Пустое поле значит «нормы нет»; ноль
            // значит «не делать».
            //
            // Столбец, отданный автоматике инкубатора (`Batch.over` / `Batch.airing`),
            // не правится вовсе и пишет «Авто» — то же, что и в таблице формы закладки,
            // и по той же причине: норма — это работа для человека, а на автоматике её
            // нет, поэтому заполняемое поле обещало бы противоположное тому, что тут же
            // сказано плиткой «на автомате» над журналом.
            //
            // Проветривание — та же клетка с маской, что в таблице расписания формы
            // закладки: «2×15». Один и тот же режим правят в двух местах, и вводить его
            // там и тут по-разному было бы обманом — тем более что обе цифры и
            // показываются везде вместе.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                NumberField(
                    label = "ПЕРЕВ., РАЗ",
                    value = edit.over,
                    onValueChange = { onChange(edit.copy(over = it.filterCountInput())) },
                    modifier = Modifier.weight(1f),
                    enabled = !autoTurn,
                    placeholder = if (autoTurn) AutoCellText else "",
                )
                NumberField(
                    label = "ПРОВЕТ., РАЗ×МИН",
                    value = airingCellText(edit.airingCount, edit.airingTime),
                    onValueChange = { raw ->
                        val (count, minutes) = parseAiringCell(raw.filterAiringInput())
                        onChange(edit.copy(airingCount = count, airingTime = minutes))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !autoAiring,
                    placeholder = if (autoAiring) AutoCellText else "",
                )
            }

            Spacer(Modifier.height(8.dp))
            SheetTextField(
                value = edit.note,
                onValueChange = { onChange(edit.copy(note = it.capitalizeFirst())) },
                placeholder = "Примечание ко дню…",
                singleLine = false,
                maxLines = 3,
                minHeight = 40.dp,
                verticalPadding = 9.dp,
                capitalization = KeyboardCapitalization.Sentences,
                radius = InnerRadius,
                modifier = Modifier.animateContentSize(),
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(InnerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.Accent,
                        contentColor = DesignPalette.OnAccent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Сохранить", style = DesignType.ButtonLabel, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(InnerRadius),
                    border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DesignPalette.Surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(text = "Отмена", style = DesignType.ButtonLabel, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Удалённый замер не восстановить: он существует только в этой строке, нигде больше
 * показания дня не хранятся.
 *
 * Вместо пересказа показаний в вопросе стоит сама карточка замера — та же, что в списке,
 * только без «Изм.» и крестика. За день замеров много, и увидеть удаляемый целиком
 * надёжнее, чем узнать его по времени.
 */
@Composable
internal fun ConfirmDeleteMeasurementDialog(
    measurement: Measurement,
    tempTarget: Double?,
    dampTarget: Double?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Удалить замер?", style = DesignType.SectionTitle) },
        text = {
            Column {
                Text(
                    text = "Эта запись исчезнет без возможности вернуть.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormSpacer(12.dp)
                MeasurementRow(
                    measurement = measurement,
                    tempTarget = tempTarget,
                    dampTarget = dampTarget,
                )
            }
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


// --- Сводка --------------------------------------------------------------------------------

/**
 * Сводка закладки: какая птица и порода, какой день и сколько осталось, сколько яиц,
 * на какие числа — и примечание, если его писали.
 *
 * Названия закладки здесь нет — оно в [SheetHeader] над карточкой, и повторять его
 * строкой ниже незачем. Вместо него вид («Курицы»), напротив — [StatusChip] тем же
 * цветом, что на карточке в списке; под ним порода своей строкой, когда она есть, и
 * день с остатком срока в часах ([dayLine]). Примечание к закладке — последним, под
 * полосой чисел, в две строки с раскрытием по тапу ([ExpandableNote]).
 *
 * Четыре плитки-справки («Заложено яиц», «Осталось сейчас», «Заложено», «Вывод»)
 * из макета лежат здесь же, полосой под заголовком, а не сеткой 2×2 под карточкой.
 * Сетка занимала два ряда высотой в полторы плитки каждый и отодвигала «Замеры за
 * сегодня» — то, ради чего шторку и открывают, — ниже сгиба. В строке те же четыре
 * числа стоят в один ряд, а даты сведены в одну ячейку «8 авг → 29 авг»: показывать
 * их порознь незачем, это один срок с двумя концами.
 *
 * Эмодзи в кружке заменено кольцом прогресса ([ProgressRing] с экрана инкубатора,
 * тем же самым): день N из M уже написан строкой ниже, и кольцо повторяет его
 * взглядом, ничего не занимая сверх круга, который тут и так был.
 */
@Composable
private fun SummaryCard(state: BatchDetailUiState, rejected: Int) {
    SheetCard(radius = TileRadius) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = incubationFraction(state),
                    emoji = speciesEmoji(state.type),
                    diameter = 52.dp,
                    // Тот же цвет, что у кольца на карточке, с которой шторку открыли:
                    // одно и то же кольцо про одну и ту же закладку.
                    color = progressRingColor(status = state.status, archived = state.hidden),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Первой строкой — вид, а не название: название уже стоит в
                        // шапке шторки, и второй раз в пяти сантиметрах ниже оно ничего
                        // не добавляло, а вид до этого был только эмодзи в кольце.
                        Text(
                            text = state.type.ifBlank { "Закладка" },
                            style = DesignType.SectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Статус — напротив вида, тем же чипом и тем же цветом, что на
                        // карточке: словом в строке дня он был серым и терялся, а
                        // «Не завершено» обязано бросаться в глаза и здесь.
                        if (state.loaded) {
                            Spacer(Modifier.width(8.dp))
                            StatusChip(state.status)
                        }
                    }
                    // Порода — своей строкой и своим кеглем, а не хвостом вида через
                    // точку: вид задаёт режим, порода — чьи яйца в лотке, и в одной
                    // строке одного начертания они читались как одно длинное название.
                    // Только когда она есть: пустая строка под видом читалась бы как
                    // незаполненное поле.
                    if (state.breed.isNotBlank()) {
                        Text(
                            text = state.breed,
                            style = DesignType.CaptionEmphasis,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = dayLine(state),
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(
                thickness = CardBorderWidth,
                color = DesignPalette.CardBorder,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                SummaryFigure(
                    label = "Заложено",
                    value = formatCount(state.eggAll),
                    modifier = Modifier.weight(1f),
                )
                FigureDivider()
                SummaryFigure(
                    // «Осталось» — заложенное минус выбракованное на овоскопированиях.
                    // Пока выбраковку было негде записать, это число равнялось
                    // заложенному; теперь оно настоящее.
                    label = if (state.finished) "Выведено" else "Осталось",
                    value = if (state.finished) formatCount(state.eggAllEND)
                    else formatCount((state.eggAll - rejected).coerceAtLeast(0)),
                    color = DesignPalette.Accent,
                    modifier = Modifier.weight(1f),
                )
                FigureDivider()
                SummaryFigure(
                    label = "Сроки",
                    value = datesLine(state),
                    // Даты длиннее двух-трёх цифр рядом, поэтому кегль меньше, а ячейка
                    // шире остальных: «8 авг → 29 авг» не должно обрезаться многоточием.
                    style = DesignType.AnalyticsValue.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    // И при крупном системном шрифте оно переносится, а не обрезается:
                    // на полуторном кегле ячейка кончалась на «9 сент. → 30 …», то есть
                    // теряла ровно ту дату, ради которой в ней стоят обе. Ряд выровнен
                    // по низу, поэтому вторая строка растит ячейку вверх и подписи всех
                    // трёх остаются на одной линии.
                    valueMaxLines = 2,
                    color = DesignPalette.DateEmphasis,
                    modifier = Modifier.weight(1.8f),
                )
            }

            // Примечание — под числами, а не под видом: оно про закладку целиком, и
            // между видом и цифрами разбивало бы сводку пополам. Две строки, тап
            // разворачивает целиком — та же складка, что у заметки к замеру.
            if (state.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = CardBorderWidth,
                    color = DesignPalette.CardBorder,
                )
                Spacer(Modifier.height(10.dp))
                ExpandableNote(state.note, key = state.batchId, collapsedLines = 2)
            }
        }
    }
}

/** Доля пройденной инкубации для кольца; без известного срока кольцо остаётся пустым. */
private fun incubationFraction(state: BatchDetailUiState): Float {
    // Полное кольцо — только у закладки, дошедшей до срока. Прерванная свой срок не
    // прошла, и её доля считается общим правилом от дня остановки: то же, что рисует
    // `batchProgress` на карточке этой закладки.
    if (state.finished && !state.stoppedEarly) return 1f
    val total = state.totalDays ?: return 0f
    if (total <= 0) return 0f
    return (state.day.toFloat() / total).coerceIn(0f, 1f)
}

/** «8 авг → 29 авг» — одна ячейка вместо двух плиток с датами. */
private fun datesLine(state: BatchDetailUiState): String {
    val start = state.startDate?.let { shortDate(it) }
    val hatch = state.hatchDate?.let { shortDate(it) }
    return when {
        start != null && hatch != null -> "$start → $hatch"
        start != null -> start
        hatch != null -> hatch
        else -> "—"
    }
}

/**
 * Ячейка полосы: число сверху, подпись под ним.
 *
 * Числа в полосе разного кегля, поэтому [Row] выравнивает ячейки по низу, а высота
 * числа зафиксирована — иначе подписи вставали бы уступом.
 */
@Composable
private fun SummaryFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DesignType.AnalyticsValue,
    valueMaxLines: Int = 1,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier.heightIn(min = 24.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = value,
                style = style,
                color = color,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            // Вторая строка с переносом по слогам — то же, что делают плитки аналитики,
            // и по той же причине: на полуторном системном кегле «Заложено» не влезало
            // в свою треть ряда и кончалось на «Заложе…». Перенос обязан быть по
            // слогам, а не по букве, иначе выходит «Заложен / о» — ровно то, что чинится
            // в шапке инкубатора.
            style = DesignType.Micro.copy(hyphens = Hyphens.Auto),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Волосяная черта между ячейками полосы — по центру строки, а не по её низу. */
@Composable
private fun RowScope.FigureDivider() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(horizontal = 8.dp)
            .width(CardBorderWidth)
            .height(28.dp)
            .background(DesignPalette.CardBorder)
    )
}

/**
 * «День 15/21 · осталось 6 дней», а в последние сутки — «осталось 5 часов»: строка
 * дня в карточке.
 *
 * Статуса здесь больше нет — он ушёл в чип напротив вида. Вместо него остаток срока.
 * Считается от [BatchDetailUiState.finishesAt] — того же момента, в который экран
 * инкубатора предлагает записать итог (`batchFinishMoment`: день вывода в час
 * закладки), так что «срок вышел» и «Инкубация завершена» наступают одновременно.
 * Пока до него сутки и больше — целые дни (полные, без округления вверх: за 25 часов
 * «остался 1 день», а не два, иначе «2 дня» сменялись бы «23 часами» скачком); когда
 * меньше суток — часы, и начатый час считается за целый: за пять минут до срока
 * остался ещё час, а не ноль. Часы только в последний день, потому что раньше они
 * ничего не говорят — «363 часа» никто не переводит в уме, а «15 дней» и так
 * читается; в последний же день «1 день» слишком грубо, когда птенцы наклёвываются
 * к вечеру. У завершённой закладки хвоста нет: ей ждать нечего, а «срок вышел» под
 * «Не завершено» читалось бы как ещё одно замечание.
 *
 * [now] — параметром, а не `Date()` внутри: строка должна поддаваться проверке на
 * заданный момент, а не на тот, в который запущен тест.
 */
internal fun dayLine(state: BatchDetailUiState, now: Date = Date()): String {
    if (!state.loaded) return ""
    val day = if (state.totalDays != null) "День ${state.day}/${state.totalDays}"
    else "День ${state.day}"
    if (state.finished) return day
    val finishesAt = state.finishesAt ?: return day
    return "$day · ${timeLeftPhrase(hoursLeft(finishesAt, now))}"
}

/**
 * «остался 1 день», «осталось 2 дня», «осталось 23 часа», «срок вышел» — остаток по
 * часам до срока: дни, пока их есть хотя бы один целый, иначе часы.
 */
internal fun timeLeftPhrase(hours: Long): String = when {
    hours <= 0 -> "срок вышел"
    hours < HOURS_PER_DAY -> hoursLeftPhrase(hours)
    else -> daysLeftPhrase(hours / HOURS_PER_DAY)
}

private const val HOURS_PER_DAY = 24L

/** Целых часов до [finishesAt], с округлением вверх; ноль, когда момент уже позади. */
internal fun hoursLeft(finishesAt: Date, now: Date): Long {
    val millis = finishesAt.time - now.time
    if (millis <= 0) return 0
    return (millis + HOUR_MILLIS - 1) / HOUR_MILLIS
}

private const val HOUR_MILLIS = 60L * 60 * 1000

/** «остался 1 час», «осталось 2 часа», «остался 21 час» — глагол согласован с числом. */
internal fun hoursLeftPhrase(hours: Long): String =
    leftVerb(hours) + " " + hoursWord(hours)

/** «остался 1 день», «осталось 2 дня», «остался 21 день» — то же для дней. */
internal fun daysLeftPhrase(days: Long): String =
    leftVerb(days) + " " + daysWord(days.toInt())

/** «остался» при числе, оканчивающемся на один (кроме одиннадцати), иначе «осталось». */
private fun leftVerb(count: Long): String =
    if (count % 10 == 1L && count % 100 != 11L) "остался" else "осталось"

/** «1 час», «2 часа», «5 часов», «21 час», «111 часов». */
internal fun hoursWord(hours: Long): String = when {
    hours % 100 in 11..14 -> "$hours часов"
    hours % 10 == 1L -> "$hours час"
    hours % 10 in 2..4 -> "$hours часа"
    else -> "$hours часов"
}

// --- Замеры за сегодня ---------------------------------------------------------------------

/**
 * Карточка «Замеры за сегодня». Общая на две шторки — закладки (здесь) и инкубатора
 * (`IncubatorMeasurementSheet`), поэтому спрашивает не состояние закладки, а ровно то,
 * что рисует: план дня для целей и норм, флаги автоматики, можно ли записывать, журнал
 * и форму. Второй экземпляр той же карточки разошёлся бы с первым при первой правке.
 *
 * [historyKey] — по чему помнить раскрытость журнала: у закладки это она сама и день
 * (наступил новый день — история другая), у инкубатора — сам инкубатор.
 * [emptyText] — что сказать, когда журнал пуст: у прибора и у закладки это разные фразы.
 */
@Composable
internal fun MeasurementsCard(
    plan: Value?,
    historyKey: Any,
    canRecord: Boolean,
    autoTurn: Boolean,
    autoAiring: Boolean,
    measurements: List<Measurement>,
    form: MeasurementForm,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onOpenAnalytics: () -> Unit,
    emptyText: String,
    readOnly: Boolean = false,
    // Таймер проветривания — только у формы «сегодня»: форма дня расписания пишет
    // задним числом, и таймер там не о чем. `null` — карточки нет. См. [AiringTimerCard].
    timer: AiringTimerSlot? = null,
    onTimerAction: (AiringTimerAction) -> Unit = {},
) {
    val unit = LocalUnits.current.temperature
    val latest = measurements.firstOrNull()
    val tempTarget = plan?.temp
    val dampTarget = plan?.damp
    // Свёрнуто — только последние замеры: за день их набирается сколько угодно, и список
    // иначе уводит вниз и форму записи, и «Завтра».
    var historyExpanded by remember(historyKey) { mutableStateOf(false) }

    // Правку начинают из строки, но список под ней можно свернуть, а замер — открыть из
    // хвоста: обводка редактируемого не должна оставаться за границей свёрнутого списка.
    // Разворачиваем один раз на замер, поэтому свернуть вручную по-прежнему можно.
    val editingHidden = form.editingId != 0L &&
        measurements.indexOfFirst { it.id == form.editingId } >= VisibleMeasurements
    LaunchedEffect(form.editingId, editingHidden) {
        if (editingHidden) historyExpanded = true
    }
    val tempDelta = deltaOf(latest?.temp, tempTarget)
    val dampDelta = deltaOf(latest?.damp, dampTarget)

    SheetCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // «Сегодня» здесь буквально: у завершённой закладки этой карточки
                    // нет вовсе — см. `OverviewPage`.
                    text = "Замеры за сегодня",
                    style = DesignType.CardHeading.copy(hyphens = Hyphens.Auto),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Вес — заголовку, а не пустоте между ним и чипом. С `SpaceBetween`
                    // оба меряются по своему тексту, и на крупном системном кегле они
                    // просто встречались посередине: «Аналитика» ломалась внутри чипа
                    // на две строки и вплотную упиралась в заголовок. Взвешенный
                    // заголовок уступает место первым — чип берёт своё и остаётся цел.
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                AnalyticsChip(enabled = measurements.isNotEmpty(), onClick = onOpenAnalytics)
            }

            Spacer(Modifier.height(12.dp))
            // [TileRow]: у влажности подпись цели короче температурной, а «нет замеров»
            // и «−0.4° · заметное отклонение» переносятся по-разному — без выравнивания
            // одна плитка стояла бы ниже другой.
            TileRow {
                MeasureTile(
                    label = "Температура",
                    value = latest?.temp?.let { "${it.formatTemp(unit)}°" },
                    target = tempTarget?.let { "цель ${it.formatTemp(unit)}°" },
                    delta = tempDelta?.let(unit::scale),
                    unit = "°",
                    decimals = 1,
                    minor = unit.scale(0.2),
                    major = unit.scale(0.5),
                    modifier = tileWeight(),
                )
                MeasureTile(
                    label = "Влажность",
                    value = latest?.damp?.let { "${it.formatDamp()}%" },
                    target = dampTarget?.let { "цель ${it.formatDamp()}%" },
                    delta = dampDelta,
                    unit = "%",
                    decimals = 0,
                    minor = 3.0,
                    major = 7.0,
                    modifier = tileWeight(),
                )
            }

            if (tempDelta != null) {
                Spacer(Modifier.height(12.dp))
                DeviationBar(
                    caption = "ТЕМПЕРАТУРА",
                    delta = unit.scale(tempDelta),
                    scale = unit.scale(TEMP_SCALE),
                    unit = "°",
                    decimals = 1,
                    severity = severityOf(tempDelta, 0.2, 0.5),
                )
            }
            if (dampDelta != null) {
                Spacer(Modifier.height(12.dp))
                DeviationBar(
                    caption = "ВЛАЖНОСТЬ",
                    delta = dampDelta,
                    scale = DAMP_SCALE,
                    unit = "%",
                    decimals = 0,
                    severity = severityOf(dampDelta, 3.0, 7.0),
                )
            }

            Spacer(Modifier.height(12.dp))
            ActionCounters(
                measurements = measurements,
                plan = plan,
                autoTurn = autoTurn,
                autoAiring = autoAiring,
            )

            // Формы записи в архивном инкубаторе нет вовсе — не «погашена»: поле, в
            // которое можно печатать, но нельзя сохранить, читается как поломка.
            if (!readOnly) {
                Spacer(Modifier.height(16.dp))
                MeasurementEntry(
                    form = form,
                    canRecord = canRecord,
                    onFormChange = onFormChange,
                    onSave = onSave,
                    onCancelEdit = onCancelEdit,
                    autoTurn = autoTurn,
                    autoAiring = autoAiring,
                    timer = timer,
                    onTimerAction = onTimerAction,
                )
            }

            if (measurements.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = emptyText,
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(Modifier.animateContentSize()) {
                    val shown =
                        if (historyExpanded) measurements
                        else measurements.take(VisibleMeasurements)
                    shown.forEach { measurement ->
                        Spacer(Modifier.height(12.dp))
                        // Без обработчиков строка становится просто карточкой замера —
                        // ровно то, что нужно архивному инкубатору: показания видно,
                        // «Изм.» и крестика у них нет.
                        MeasurementRow(
                            measurement = measurement,
                            tempTarget = tempTarget,
                            dampTarget = dampTarget,
                            onEdit = if (readOnly) null else ({ onEdit(measurement) }),
                            onDelete = if (readOnly) null else ({ onDelete(measurement) }),
                            selected = form.editingId == measurement.id,
                        )
                    }
                }
                if (measurements.size > VisibleMeasurements) {
                    Spacer(Modifier.height(12.dp))
                    ShowAllToggle(
                        expanded = historyExpanded,
                        total = measurements.size,
                        onClick = { historyExpanded = !historyExpanded },
                    )
                }
            }
        }
    }
}

/**
 * Кнопка «Аналитика» из макета — открывает [BatchAnalyticsSheet].
 *
 * Без единого замера она погашена и не нажимается: считать за день нечего, и шторка
 * открылась бы пустой.
 */
@Composable
internal fun AnalyticsChip(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = DesignPalette.Surface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chart_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Аналитика",
                style = DesignType.CaptionEmphasis,
                color = MaterialTheme.colorScheme.onSurface,
                // Чип не переносится и не сжимается: он и говорит ряду выше, сколько
                // места забирает у взвешенного заголовка.
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun MeasureTile(
    label: String,
    value: String?,
    target: String?,
    delta: Double?,
    unit: String,
    decimals: Int,
    minor: Double,
    major: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = DesignPalette.MeasureTile,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value ?: "—",
                style = DesignType.MeasureValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = target ?: "цель не задана",
                style = DesignType.MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            val severity = delta?.let { severityOf(it, minor, major) }
            Text(
                text = if (delta == null || severity == null) "нет замеров"
                else "${signed(delta, decimals, unit)} · ${severity.label()}",
                style = DesignType.MonoSmallEmphasis,
                color = severity.color(),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Что за день сделали руками: сколько раз перевернули и сколько проветривали.
 *
 * Плитки температуры и влажности показывают последний замер, а перевороты и
 * проветривания так не читаются: важен не последний, а весь день — «сколько уже» и
 * «сколько ещё». Числа те же, что в шторке аналитики ([actionCountsOf] считает их для
 * обеих), но здесь они на виду, без открытия аналитики: по ним и решают, идти ли
 * переворачивать.
 *
 * Норма берётся прямо из плана дня — это число, и «осталось N» считается вычитанием.
 * Пустая норма (`null`) значит, что требовать нечего: у дня на автоматике инкубатора
 * ([BatchDetailUiState.autoTurn], [BatchDetailUiState.autoAiring]) плитка так и пишет —
 * «на автомате», у остальных «без нормы».
 */
@Composable
internal fun ActionCounters(
    measurements: List<Measurement>,
    plan: Value?,
    autoTurn: Boolean,
    autoAiring: Boolean,
) {
    val counts = remember(measurements) { actionCountsOf(measurements) }
    TileRow {
        ActionTile(
            label = "Перевороты",
            done = counts.turns,
            plan = plan?.over,
            planLabel = plan?.let { planTurnLabel(it.over, autoTurn) },
            auto = plan != null && plan.over == null && autoTurn,
            modifier = tileWeight(),
        )
        ActionTile(
            label = "Проветривания",
            done = counts.airings,
            plan = plan?.airingCount,
            planLabel = plan?.let { planAiringLabel(it.airingCount, it.airingTime, autoAiring) },
            auto = plan != null && plan.airingCount == null && autoAiring,
            // Замер хранит разы и длительность порознь, поэтому минуты — отдельная
            // цифра, а не то же самое число другими словами.
            extra = if (counts.airingMinutes > 0) "${counts.airingMinutes} мин" else null,
            modifier = tileWeight(),
        )
    }
}

/**
 * Плитка счётчика: сделано за день, план на день и что осталось.
 *
 * [plan] — норма числом, [planLabel] — она же для показа: у проветривания это «2×5 мин»,
 * то есть разы вместе с длительностью, а в остаток идут только разы.
 */
@Composable
private fun ActionTile(
    label: String,
    done: Int,
    plan: Int?,
    planLabel: String?,
    auto: Boolean,
    modifier: Modifier = Modifier,
    extra: String? = null,
) {
    val target = if (auto) null else plan
    val complete = target != null && done >= target
    val status = when {
        auto -> "на автомате"
        target == null -> if (done > 0) "без нормы" else "не записано"
        complete -> "план выполнен"
        else -> "осталось ${target - done}"
    }
    // Обе строки под числом — ровно в одну строку каждая. Высоту плиток это больше не
    // решает (её выравнивает [TileRow]), но перенос здесь и не нужен: строка описывает
    // норму, и вторая её половина читается и в обрезанном виде. Минуты идут первыми и вытесняют
    // многоточием хвост плана — тот целиком виден и на плашках «Завтра», и в расписании.
    val detail = listOfNotNull(
        extra,
        planLabel?.let { "план $it" } ?: "плана нет",
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = DesignPalette.MeasureTile,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = done.toString(),
                style = DesignType.MeasureValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            // «22 мин · план 2×5 мин» в полплитки не помещается, и обрезается как раз
            // план; нажатие показывает строку целиком — как у любой плитки в приложении.
            TruncatedText(
                text = detail,
                style = DesignType.MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            TruncatedText(
                text = status,
                style = DesignType.MonoSmallEmphasis,
                maxLines = 1,
                // Невыполненный план середины дня — не отклонение: до вечера ещё
                // успеется, поэтому серым, а не красным. Зелёным — только выполненный.
                color = if (complete) DesignPalette.Accent
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Шкала отклонения: дорожка, засечка «цель» по центру и точка замера. */
@Composable
internal fun DeviationBar(
    caption: String,
    delta: Double,
    scale: Double,
    unit: String,
    decimals: Int,
    severity: Severity,
) {
    Column {
        Text(
            text = caption,
            style = DesignType.PillLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val track = maxWidth
            val fraction = (0.5 + delta / (2 * scale)).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(DesignPalette.PillSurface)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = track / 2 - 1.dp)
                    .size(width = 2.dp, height = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DesignPalette.CardBorder)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (track - 12.dp) * fraction)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(severity.color())
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScaleLabel(signed(-scale, decimals, unit))
            ScaleLabel("цель")
            ScaleLabel(signed(scale, decimals, unit))
        }
    }
}

@Composable
private fun ScaleLabel(text: String) {
    Text(
        text = text,
        style = DesignType.MonoMicro,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MeasurementEntry(
    form: MeasurementForm,
    canRecord: Boolean,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    // В дне расписания замер пишут задним числом, и «сейчас» там ничего не значит:
    // поле времени стоит в форме всегда, а не только когда правят записанное.
    alwaysShowTime: Boolean = false,
    // Закладка на автоматике инкубатора: переворот и проветривание делает он сам, и
    // записывать их как факт нечего — клетка заперта и пишет «Авто», как в плане дня.
    autoTurn: Boolean = false,
    autoAiring: Boolean = false,
    // Таймер проветривания под полями. Нет под автопроветриванием — клетка заперта, и
    // записывать минуты некуда, — и нет в форме дня расписания (см. [MeasurementsCard]).
    timer: AiringTimerSlot? = null,
    onTimerAction: (AiringTimerAction) -> Unit = {},
) {
    val unit = LocalUnits.current.temperature
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = DesignPalette.MeasureForm,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            val editing = form.editingId != 0L
            // Макет подписывает форму, только когда правят записанный замер: поля те же,
            // и без подписи не видно, что кнопка теперь меняет старую запись, а не пишет новую.
            // Подпись въезжает вместе с высотой карточки, а не возникает рывком.
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Text(
                        text = "Редактирование замера",
                        style = DesignType.CaptionEmphasis,
                        color = DesignPalette.Accent,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            AnimatedVisibility(
                visible = editing || alwaysShowTime,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    MeasurementTimeField(
                        time = form.time,
                        onTimeChange = { onFormChange(form.copy(time = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            // По низу — чтобы подпись, занявшая на крупном шрифте вторую строку, растила
            // свою колонку вверх, а сами поля оставались на одной линии. См. [FieldCaption].
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                NumberField(
                    label = unit.fieldLabel,
                    value = form.temp,
                    onValueChange = { onFormChange(form.copy(temp = it.filterMeasureInput(unit.integerDigits))) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "ВЛАЖ.%",
                    value = form.damp,
                    onValueChange = { onFormChange(form.copy(damp = it.filterMeasureInput())) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    // Колонки равной ширины, поэтому единицы влезают только в сокращении:
                    // «ПРОВЕТ., МИН» в четверть строки уже не помещается.
                    label = "ПРОВ., МИН",
                    value = if (autoAiring) "" else form.airingTime,
                    onValueChange = {
                        onFormChange(form.copy(airingTime = it.filterCountInput()))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !autoAiring,
                    placeholder = if (autoAiring) AutoCellText else "",
                )
                // Под автопереворотом на месте кнопки-отметки стоит та же запертая
                // клетка, что и у проветривания рядом: две колонки отданы автоматике
                // одинаково, и выглядеть это должно одинаково, а не как отжатая галочка.
                if (autoTurn) {
                    NumberField(
                        label = "ПЕРЕВ.",
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.weight(1f),
                        enabled = false,
                        placeholder = AutoCellText,
                    )
                } else {
                    ToggleField(
                        label = "ПЕРЕВ.",
                        checked = form.over.isNotBlank(),
                        onCheckedChange = {
                            onFormChange(form.copy(over = if (it) TurnedMark else ""))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (timer != null && !autoAiring && canRecord) {
                Spacer(Modifier.height(8.dp))
                AiringTimerCard(slot = timer, onAction = onTimerAction)
            }

            Spacer(Modifier.height(8.dp))
            SheetTextField(
                value = form.note,
                // Заметка — фраза, а не значение: первая буква всегда заглавная, даже
                // если текст вставили из буфера, где её не было.
                onValueChange = { onFormChange(form.copy(note = it.capitalizeFirst())) },
                placeholder = "Заметка к замеру…",
                // Одна строка ровно на 40 dp, как раньше; заполнив её, поле подрастает
                // до второй — `animateContentSize` превращает скачок в движение.
                singleLine = false,
                maxLines = 2,
                minHeight = 40.dp,
                verticalPadding = 9.dp,
                capitalization = KeyboardCapitalization.Sentences,
                radius = InnerRadius,
                modifier = Modifier.animateContentSize(),
            )

            Spacer(Modifier.height(8.dp))
            // Промежутка в самом ряду нет: 8 dp уезжают вместе с «Отменой» внутрь её
            // анимации, иначе на последнем кадре зелёная кнопка прыгала бы на них вбок.
            Row {
                Button(
                    onClick = onSave,
                    enabled = canRecord && form.isValid,
                    shape = RoundedCornerShape(InnerRadius),
                    colors = accentButtonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus_design),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    // Обе подписи лежат друг на друге и переключаются прозрачностью, а не
                    // подменой: ширина места под них всегда равна более длинной из двух,
                    // поэтому содержимое кнопки не пересчитывает середину. `Crossfade`
                    // здесь не годился — он меняет и размер, и текст в кнопке доезжал до
                    // центра уже после того, как сама кнопка встала на место.
                    val editAlpha by animateFloatAsState(
                        targetValue = if (editing) 1f else 0f,
                        label = "measurement-save-label",
                    )
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Сохранить замер",
                            style = DesignType.ButtonLabel,
                            maxLines = 1,
                            modifier = Modifier.alpha(editAlpha),
                        )
                        Text(
                            text = "Записать замер",
                            style = DesignType.ButtonLabel,
                            maxLines = 1,
                            modifier = Modifier.alpha(1f - editAlpha),
                        )
                    }
                }
                // «Отмена» в макете — узкая кнопка справа от зелёной, по ширине текста,
                // и только в правке: при новой записи бросать нечего. Она выезжает
                // справа, а зелёная — с весом, поэтому та же анимация её и ужимает.
                AnimatedVisibility(
                    visible = editing,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancelEdit,
                            shape = RoundedCornerShape(InnerRadius),
                            border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DesignPalette.Surface,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text(
                                text = "Отмена",
                                style = DesignType.ButtonLabel,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Время замера — поле только в правке, и оно стоит над всеми остальными во всю ширину
 * карточки.
 *
 * Новому замеру время не выбирают: он записывается «сейчас», и спрашивать про это перед
 * каждой записью значило бы мешать. А вот записанный замер уже имеет своё время, и когда
 * его открыли на правку, оно такая же часть записи, как показания: замер, записанный
 * задним числом, без этого поля остался бы с временем, когда его *записали*.
 *
 * Ввод — тот же диалог, что у напоминаний в форме закладки: «HH:mm» руками легко набрать
 * так, что потом ни график, ни сортировка это время не разберут.
 */
@Composable
private fun MeasurementTimeField(
    time: String,
    onTimeChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        TimePicker(time = time.clockOrNow()) {
            onTimeChange(it)
            showPicker = false
        }
    }

    Column {
        Text(
            text = "ВРЕМЯ",
            style = DesignType.MonoMicro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        SheetPickerField(
            value = time,
            placeholder = "--:--",
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            // Та же высота и то же скругление, что у полей показаний под ним.
            height = 40.dp,
            radius = InnerRadius,
            textStyle = DesignType.MonoField,
            // Часы справа — как календарь у «Даты закладки»: во всю ширину поле иначе
            // не отличить от нередактируемой строчки со временем.
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.baseline_access_time_24),
                    contentDescription = "Выбрать время",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

/**
 * Время для диалога: он разбирает строку как «HH:mm» и на пустой или испорченной упал бы.
 *
 * Такого времени в базе взяться неоткуда — его пишет только `BatchDetailViewModel.save()`,
 * — но страховка здесь в одну строку, а без неё цена ошибки это падение прямо в форме.
 */
private fun String.clockOrNow(): String =
    if (parseClock(this) != null) this
    else clockText()

/**
 * Фильтрует ввод вызывающий: у замеров и счётчиков правила разные.
 *
 * [numeric] выключается для полей плана «перевороты» и «проветривания»: там не числа,
 * а «2-3», «Авто», «2 раза по 5 минут» — им нужна обычная клавиатура.
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = true,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FieldCaption(label)
        Spacer(Modifier.height(4.dp))
        SheetTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            numeric = numeric,
            // Поле шириной в четверть строки: с отступом по умолчанию «36.6» не влезает.
            horizontalPadding = 8.dp,
            minHeight = 40.dp,
            radius = InnerRadius,
            enabled = enabled,
        )
    }
}

/**
 * Подпись над полем замера: «T°C», «ПРОВ., МИН», «ПРОВЕТ., РАЗ×МИН».
 *
 * **Две строки, а не многоточие.** Подписи и так сокращены до предела — колонок четыре,
 * и единицы в них влезают только в таком виде, — но на крупном системном кегле и этого
 * мало: «ПРОВ., МИН» кончалось на «ПРОВ., …», то есть поле переставало говорить, в чём
 * его заполняют. Перенос ложится по пробелу после запятой, а ряды, где стоят такие
 * поля, выровнены по низу, поэтому сами поля остаются на одной линии, даже когда одна
 * подпись стала выше соседних.
 */
@Composable
private fun FieldCaption(label: String) {
    Text(
        text = label,
        style = DesignType.MonoMicro,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Кнопка-отметка рядом с полями замера: переворот за один замер бывает один, считать нечего.
 * Нажатая горит акцентным зелёным — «переворачивали», отжатая выглядит как пустое поле.
 */
@Composable
private fun ToggleField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FieldCaption(label)
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(InnerRadius),
            color = if (checked) DesignPalette.Accent else DesignPalette.Surface,
            border = BorderStroke(
                CardBorderWidth,
                if (checked) DesignPalette.Accent else DesignPalette.CardBorder,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // Та же высота, что у соседних полей: кнопка стоит с ними в одну линию.
                .height(40.dp)
                .clip(RoundedCornerShape(InnerRadius))
                .clickable { onCheckedChange(!checked) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = if (checked) "Переворачивали" else "Отметить переворот",
                    tint = if (checked) DesignPalette.OnAccent else DesignPalette.CardBorder,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun MeasurementRow(
    measurement: Measurement,
    tempTarget: Double?,
    dampTarget: Double?,
    // Без обработчиков строка становится просто карточкой замера — такой её показывает
    // вопрос об удалении, где править и удалять уже нечего.
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val unit = LocalUnits.current.temperature
    val tempDelta = deltaOf(measurement.temp, tempTarget)
    val dampDelta = deltaOf(measurement.damp, dampTarget)
    // Правится замер сверху, в форме, а строка его остаётся в списке ниже; в макете
    // редактируемую отмечает зелёная заливка — та же, что у чипа «Инкубация». Цвет
    // перетекает, а не переключается: строка и форма меняются одним движением.
    val rowColor by animateColorAsState(
        targetValue = if (selected) DesignPalette.StatusActiveSurface else DesignPalette.MeasureRow,
        label = "measurement-row-surface",
    )
    Surface(
        shape = RoundedCornerShape(InnerRadius),
        color = rowColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = measurement.time,
                    style = DesignType.MonoRow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                onEdit?.let { edit ->
                    Text(
                        text = "Изм.",
                        style = DesignType.CaptionEmphasis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = edit)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                onDelete?.let { delete ->
                    Icon(
                        painter = painterResource(id = ru.zaroslikov.incubator.design.R.drawable.ic_close_design),
                        contentDescription = "Удалить замер",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = delete)
                            .padding(5.dp),
                    )
                }
            }

            // Показания со своими отклонениями — отдельной строкой: рядом со временем и
            // кнопками четыре значения уже не помещаются. Каждая величина занимает свою
            // половину строки, поэтому пустой слот остаётся пустым, а не сдвигает соседа.
            if (measurement.temp != null || measurement.damp != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HalfSlot {
                        measurement.temp?.let { temp ->
                            ReadingWithDelta(
                                icon = R.drawable.ic_temperature_design,
                                iconDescription = "Температура",
                                value = "${temp.formatTemp(unit)}°",
                                delta = tempDelta?.let(unit::scale),
                                decimals = 1,
                                unit = "°",
                                minor = unit.scale(0.2),
                                major = unit.scale(0.5),
                            )
                        }
                    }
                    HalfSlot {
                        measurement.damp?.let { damp ->
                            ReadingWithDelta(
                                icon = R.drawable.ic_humidity_design,
                                iconDescription = "Влажность",
                                value = "${damp.formatDamp()}%",
                                delta = dampDelta,
                                decimals = 0,
                                unit = "%",
                                minor = 3.0,
                                major = 7.0,
                            )
                        }
                    }
                }
            }

            val airingText = factAiringLabel(measurement.airingCount, measurement.airingTime)
            if (measurement.over != null || airingText != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HalfSlot {
                        // Отметка кладёт единицу и говорит только «было»; замер, где
                        // переворотов записано больше, показывает их число.
                        measurement.over?.let {
                            DetailText(if (it == 1) "Переворачивали" else "перев. $it")
                        }
                    }
                    HalfSlot {
                        airingText?.let { DetailText("провет. $it") }
                    }
                }
            }

            if (measurement.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ExpandableNote(measurement.note, measurement.id)
            }
        }
    }
}

/**
 * Заметка, свёрнутая до [collapsedLines] строк; остальные разворачиваются по нажатию.
 *
 * Одна на две заметки: в строке замера — одна строка, в сводке закладки — две
 * ([SummaryCard]). Складка одна и та же, и вести её в двух местах незачем.
 *
 * Длинные заметки иначе растягивают список и прятать под ними соседние замеры. Свёрнутый
 * текст обрывается многоточием — это и есть подсказка, что там есть продолжение; строку
 * ровно в одну строку нажимать не на что, поэтому она и не кликается.
 *
 * [key] — идентификатор замера или закладки: строки живут в обычной колонке и
 * переиспользуются, а развёрнутой должна остаться та же запись, а не та, что заняла
 * её место.
 */
@Composable
private fun ExpandableNote(note: String, key: Long, collapsedLines: Int = 1) {
    var expanded by remember(key) { mutableStateOf(false) }
    var truncated by remember(key) { mutableStateOf(false) }
    Text(
        text = note.capitalizeFirst(),
        style = DesignType.Caption,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        // В развёрнутом виде переполнения нет по определению — иначе признак сбрасывался
        // бы сам собой и заметку было бы не свернуть обратно.
        onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(
                if (truncated) Modifier.clickable { expanded = !expanded }
                else Modifier
            ),
    )
}

/** Половина строки замера: пустой слот держит место, чтобы правая величина не съезжала влево. */
@Composable
private fun RowScope.HalfSlot(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f)) { content() }
}

@Composable
private fun DetailText(text: String) {
    Text(
        text = text,
        style = DesignType.Mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Показание и его отклонение от плана — как температура в макете, но для обеих величин.
 *
 * Значок вместо подписи: «37.5° +0.3°» и «58% −2%» сами по себе не говорят, где что,
 * а на «t°C» / «влаж.» в половине строки места уже не остаётся.
 */
@Composable
private fun ReadingWithDelta(
    @DrawableRes icon: Int,
    iconDescription: String,
    value: String,
    delta: Double?,
    decimals: Int,
    unit: String,
    minor: Double,
    major: Double,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = iconDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            style = DesignType.MonoAccent,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (delta != null) {
            Text(
                text = signed(delta, decimals, unit),
                style = DesignType.Mono,
                color = severityOf(delta, minor, major).color(),
            )
        }
    }
}

// --- Завтрашний режим ----------------------------------------------------------------------

/**
 * Режим на завтра, а заодно и предупреждение, если завтра овоскопирование.
 *
 * Предупреждение стоит под плашками режима — там же, где оно стоит в карточке дня
 * расписания, чтобы у одного и того же дня оно не переезжало с места на место.
 * Овоскопирование требует подготовки (тёмная комната, овоскоп, свободные десять минут),
 * и узнать о нём накануне полезнее, чем утром того же дня.
 *
 * Плашка [CandlingCard] тут не годится: она ведёт на экран овоскопирования, а завтрашнее
 * овоскопирование сегодня не проводят — кнопка обещала бы то, чего делать не нужно.
 * Поэтому это просто надпись, без стрелки и без нажатия.
 */
@Composable
private fun TomorrowCard(
    day: Int,
    /** Какое по счёту овоскопирование завтра; 0 — никакого. Считает состояние шторки. */
    candlingStage: Int,
    plan: Value,
    autoTurn: Boolean,
    autoAiring: Boolean,
) {
    SheetCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Завтра — День $day",
                style = DesignType.CardHeading,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            val unit = LocalUnits.current.temperature
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePill(unit.fieldLabel, plan.temp.formatTempOrDash(unit), Modifier.weight(1f))
                ModePill("ВЛАЖН.", plan.damp?.let { "${it.formatDamp()}%" } ?: "—", Modifier.weight(1f))
                ModePill("ПЕРЕВ.", planTurnLabel(plan.over, autoTurn), Modifier.weight(1f))
                ModePill(
                    "ПРОВЕТ.",
                    planAiringPill(plan.airingCount, plan.airingTime, autoAiring),
                    Modifier.weight(1f),
                )
            }
            if (candlingStage > 0) {
                Spacer(Modifier.height(10.dp))
                CandlingPlannedNotice(candlingStage)
            }
        }
    }
}

/** «Завтра планируется первое овоскопирование» — надпись, а не кнопка: см. [TomorrowCard]. */
@Composable
private fun CandlingPlannedNotice(stage: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DesignPalette.SpeciesTileSelected,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ovos2),
                contentDescription = null,
                tint = DesignPalette.DateEmphasis,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Планируется ${stageTitle(stage).lowercase()}",
                style = DesignType.Caption,
                color = DesignPalette.DateEmphasis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModePill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(InnerRadius),
        color = DesignPalette.PillSurface,
        modifier = modifier.height(63.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Плитка — четверть строки, и что в неё не влезло, показывается по нажатию:
            // «100.22» по Фаренгейту или «ПРОВЕТ.» на крупном кегле обрезаются ровно
            // там, где стоит смысл. Правило то же, что у цифр в плитках «Финансов».
            TruncatedText(
                text = label,
                style = DesignType.PillLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            TruncatedText(
                text = value,
                style = DesignType.PillValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// --- Общие мелочи --------------------------------------------------------------------------

/** Белая карточка шторки: скругление из макета, рамка 0.8 dp цветом [DesignPalette.CardBorder]. */
@Composable
internal fun SheetCard(
    radius: Dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = DesignPalette.Surface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

// --- Отклонение от плана -------------------------------------------------------------------

// [Severity], [severityOf] и [deltaOf] живут в `ValueFormat.kt`: они без Compose, и на
// них стоит `ScheduleOverlap.kt`, который гоняется JVM-тестом.

private fun Severity.label(): String = when (this) {
    Severity.Normal -> "в норме"
    Severity.Minor -> "небольшое отклонение"
    Severity.Major -> "заметное отклонение"
}

@Composable
private fun Severity?.color(): Color = when (this) {
    Severity.Normal -> DesignPalette.Accent
    Severity.Major -> DesignPalette.Expense
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}



/** «−1.2°», «+3%» — со знаком и типографским минусом, как в макете. */
private fun signed(delta: Double, decimals: Int, unit: String): String {
    val body = String.format(Locale("ru"), "%+.${decimals}f", delta)
    return body.replace('-', '−').replace(',', '.') + unit
}
