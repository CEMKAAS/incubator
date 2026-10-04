package ru.zaroslikov.incubator.ui.batch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.rememberBannerAdHost
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.design.components.DialogStatusBar
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.design.theme.bodyFontFamily
import ru.zaroslikov.incubator.design.theme.monoFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardRadius = 22.dp
private val TileRadius = 16.dp
private val CardBorderWidth = 0.8.dp

/** Высота поля графика из макета (узел 21:9106). */
private val ChartHeight = 192.dp

/** Место под подписи оси слева и справа и под время внизу. */
private val AxisGutterLeft = 34.dp
private val AxisGutterRight = 28.dp
private val AxisGutterBottom = 18.dp
private val ChartTopPadding = 8.dp

/** Минимальная ширина окна проветривания: нулевую длительность тоже надо увидеть. */
private val MinAiringWidth = 3.dp

/** Ряд иконок заметок под областью графика — только когда заметки за день есть. */
private val NoteRowHeight = 18.dp
private val NoteIconSize = 13.dp

/** Насколько палец может промахнуться мимо иконки заметки и всё же попасть в неё. */
private val NoteTouchSlop = 8.dp

/** Во сколько раз меняет масштаб одно нажатие «+» или «−» на весь экран. */
private const val ZOOM_STEP = 1.5f

/** Ширина окна графика, когда все замеры пришлись на одну минуту, в минутах. */
private const val MIN_SPAN_MINUTES = 30

/**
 * «Аналитика за день» — макет
 * [21:9088](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=21-9088):
 * график показаний за день, двенадцать плиток-итогов и текст-вывод.
 *
 * Шторка поверх шторки закладки, без маршрута. Данные приходят готовыми из
 * [BatchDetailViewModel]; своей модели у шторки нет.
 *
 * Отступления от макета, все намеренные:
 * — температура красная, а не зелёная: зелёный в приложении — акцент и «в норме»;
 * — вторая линия, синяя, — влажность, со своей осью справа (отсюда заголовок «Показания в
 *   течение дня»);
 * — переворот и заметка — вертикальная черта через весь график (у заметки пунктирная),
 *   проветривание закрашивает временную область: у него есть длительность;
 * — линии прямые: сглаженная кривая выгибается выше максимума, а «Максимум» — плитка под графиком;
 * — на весь экран график раскрывается ([FullscreenChartDialog]) и только там масштабируется:
 *   в карточке жест спорил бы с прокруткой и закрытием шторки;
 * — под пунктиром заметки иконка, по нажатию показывающая текст ([NotePopup]);
 * — открытая из шторки инкубатора, аналитика рисует план каждой закладки пунктирной
 *   горизонталью своего цвета ([ChartPlanLine]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchAnalyticsSheet(
    measurements: List<Measurement>,
    plan: Value?,
    onDismiss: () -> Unit,
    /**
     * Планы закладок инкубатора — линии на графике. Передаёт только шторка замеров по
     * инкубатору: там показание одно на несколько закладок и сравнивать его есть с чем;
     * аналитика одной закладки оставляет список пустым и получает график макета.
     */
    planLines: List<ChartPlanLine> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val analytics = remember(measurements, plan, planLines) {
        analyticsOf(measurements, plan, planLines)
    }
    // Владелец объявления живёт со шторкой: она открывается по нажатию и закрывается
    // целиком, а прокрутка её композицию не рушит — переживать ему нечего.
    val adHost = rememberBannerAdHost()
    // График на весь экран — `rememberSaveable`: раскрывают его в том числе ради
    // ландшафта, а поворот пересоздаёт активность, и `remember` закрыл бы его на полпути.
    var expanded by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = SheetPadding)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SheetHeader(title = "Аналитика за день", onClose = onDismiss)

            FormSpacer(20.dp)
            ChartCard(
                analytics = analytics,
                onExpand = {
                    Analytics.report(Events.CHART_EXPANDED)
                    expanded = true
                },
            )

            // Реклама — после графика, а не в конце шторки: ниже сетка из двенадцати
            // плиток и карточка вывода, и до низа доходят не все.
            FormSpacer(16.dp)
            AdBanner(adHost)

            FormSpacer(16.dp)
            StatGrid(analytics)

            FormSpacer(16.dp)
            InsightCard(analytics)
        }
    }

    // Соседом шторки, а не её содержимым: полный экран — своё окно поверх, и его закрытие
    // возвращает ровно эту шторку.
    if (expanded) {
        FullscreenChartDialog(analytics = analytics, onDismiss = { expanded = false })
    }
}

// --- График --------------------------------------------------------------------------------

@Composable
private fun ChartCard(
    analytics: DayAnalytics,
    onExpand: () -> Unit,
) {
    AnalyticsCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Показания в течение дня",
                    style = DesignType.ChartTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Кнопка «на весь экран» — только когда есть что раскрывать: пустая
                // карточка с надписью «замеров нет» на весь экран обещала бы график.
                if (analytics.hasChart) {
                    IconButton(
                        onClick = onExpand,
                        // 32 dp вместо 48: кнопка стоит в строке заголовка карточки, и
                        // стандартный размер раздвинул бы её на треть выше текста.
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_design),
                            contentDescription = "График на весь экран",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (analytics.hasChart) {
                DayChart(
                    analytics = analytics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                )
                Spacer(Modifier.height(12.dp))
                ChartLegend(analytics)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (analytics.total == 0) "За сегодня замеров ещё нет"
                        else "В замерах за сегодня нет ни температуры, ни влажности",
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Одна из двух величин на графике: своя ось, свой цвет и свой способ подписать засечку.
 *
 * Величины две, а места под подписи оси — по одному с каждой стороны, поэтому ось и
 * цвет ходят парой: подпись «55» слева и справа означала бы разное.
 */
private class ChartSeries(
    val axis: ChartAxis,
    val color: Color,
    val label: (Double) -> String,
    val valueOf: (ChartPoint) -> Double?,
    /** То же значение у плана закладки — уже в единицах экрана, как и [valueOf]. */
    val planValueOf: (ChartPlanLine) -> Double?,
    /** Подпись линии плана — с единицей («37.8°», «55%»): она и различает две оси. */
    val planLabel: (Double) -> String,
    /**
     * Сколько знаков печатает подпись оси — два у температуры, один у влажности. Мельче
     * этого сетку не дробят: засечка 37.025 показалась бы как 37.03 — не там, где линия.
     */
    val labelDecimals: Int,
)

/**
 * Всё, что графику нужно знать помимо размера: оси, окно по времени, планы закладок с их
 * цветами. Собирается в композиции — единицы и палитра читаются только там, — и дальше
 * одинаково служит и рисованию, и разбору нажатий.
 *
 * [left] — ось у левого края, [right] — у правого или `null`; [tempSeries] / [dampSeries] —
 * те же две величины по имени, для линий плана: температура плана ложится на ось
 * температуры, где бы та ни стояла.
 */
private class ChartModel(
    val left: ChartSeries,
    val right: ChartSeries?,
    val tempSeries: ChartSeries?,
    val dampSeries: ChartSeries?,
    val from: Int,
    val span: Float,
    val planColors: List<Color>,
) {
    fun fraction(minutes: Int): Float = (minutes - from) / span
}

/**
 * Геометрия графика при данном размере: область под кривые и поля под оси, ряд иконок
 * заметок и подписи времени. Считается заново на каждый кадр из [Density] — и в
 * `Canvas`, и в `pointerInput`, — чтобы иконка рисовалась и нажималась в одной точке.
 *
 * Ряд иконок стоит между областью и подписями времени и только когда заметки есть:
 * иначе он отнимал бы у графика высоту ради пустого места.
 */
private class ChartFrame(
    val plotLeft: Float,
    val plotRight: Float,
    val plotTop: Float,
    val plotBottom: Float,
    val noteRowTop: Float,
    val noteRowHeight: Float,
    val timeLabelTop: Float,
) {
    val plotWidth: Float get() = plotRight - plotLeft
    val plotHeight: Float get() = plotBottom - plotTop
    val valid: Boolean get() = plotWidth > 0f && plotHeight > 0f
}

private fun Density.chartFrame(size: Size, hasRightAxis: Boolean, hasNotes: Boolean): ChartFrame {
    val noteRow = if (hasNotes) NoteRowHeight.toPx() else 0f
    val plotBottom = size.height - AxisGutterBottom.toPx() - noteRow
    return ChartFrame(
        plotLeft = AxisGutterLeft.toPx(),
        plotRight = size.width - if (hasRightAxis) AxisGutterRight.toPx() else 4.dp.toPx(),
        plotTop = ChartTopPadding.toPx(),
        plotBottom = plotBottom,
        noteRowTop = plotBottom,
        noteRowHeight = noteRow,
        timeLabelTop = plotBottom + noteRow + 2.dp.toPx(),
    )
}

@Composable
private fun rememberChartModel(analytics: DayAnalytics): ChartModel? {
    val unit = LocalUnits.current.temperature
    val palette = DesignPalette
    return remember(analytics, unit, palette) {
        // Ось строится уже по градусам экрана: «красивые» деления в Цельсиях, переведённые
        // подписью, в Фаренгейтах красивыми быть перестают.
        val pointTemps = analytics.points.mapNotNull { it.temp?.let(unit::fromCelsius) }
        val pointDamps = analytics.points.mapNotNull { it.damp }
        // Линии плана входят в размах оси — иначе план закладки, лежащий выше всех
        // замеров, оказался бы за верхней линией сетки и невидимым. Но ось есть только
        // у величины, которую замеряли: план без единого показания рисовать не на чем.
        val temps = if (pointTemps.isEmpty()) pointTemps
        else pointTemps + analytics.planLines.mapNotNull { it.temp?.let(unit::fromCelsius) }
        val damps = if (pointDamps.isEmpty()) pointDamps
        else pointDamps + analytics.planLines.mapNotNull { it.damp }

        val tempSeries = if (temps.isEmpty()) null else ChartSeries(
            axis = niceAxis(temps.min(), temps.max(), TEMP_AXIS_STEPS),
            color = palette.ChartTemp,
            label = { it.formatTemp() },
            valueOf = { it.temp?.let(unit::fromCelsius) },
            planValueOf = { it.temp?.let(unit::fromCelsius) },
            planLabel = { "${it.formatTemp()}°" },
            labelDecimals = 2,
        )
        val dampSeries = if (damps.isEmpty()) null else ChartSeries(
            axis = niceAxis(damps.min(), damps.max(), DAMP_AXIS_STEPS),
            color = palette.ChartDamp,
            label = { it.formatDamp() },
            valueOf = { it.damp },
            planValueOf = { it.damp },
            planLabel = { "${it.formatDamp()}%" },
            labelDecimals = 1,
        )
        // Температура — главная величина, поэтому подписи её оси слева, как в макете;
        // влажность уходит вправо. Если температуры нет вовсе, влажность занимает её место.
        val left = tempSeries ?: dampSeries ?: return@remember null
        val right = if (tempSeries != null) dampSeries else null

        // Окно графика по времени: и точки, и события, включая конец проветривания —
        // закрашенная область не должна упираться в правый край и обрываться там.
        val stamps = buildList {
            analytics.points.forEach { add(it.minutes) }
            analytics.events.forEach {
                add(it.minutes)
                add(it.minutes + it.durationMinutes)
            }
        }
        var from = stamps.min()
        var to = stamps.max()
        if (to - from < MIN_SPAN_MINUTES) {
            val middle = (from + to) / 2
            from = middle - MIN_SPAN_MINUTES / 2
            to = middle + MIN_SPAN_MINUTES / 2
        }

        ChartModel(
            left = left,
            right = right,
            tempSeries = tempSeries,
            dampSeries = dampSeries,
            from = from,
            span = (to - from).toFloat(),
            // Цвет закладке — по позиции, как столбикам видов на «Статистике».
            planColors = analytics.planLines.indices.map { palette.ChartBars[it % palette.ChartBars.size] },
        )
    }
}

/**
 * График показаний за день.
 *
 * В карточке шторки он неподвижен ([viewport] — [ChartViewport.IDENTITY], [onViewportChange]
 * не передаётся): жест масштаба там спорил бы с прокруткой шторки и её закрытием. На весь
 * экран ([FullscreenChartDialog]) тот же график получает жесты — щипок меняет масштаб
 * вокруг пальцев, одним пальцем содержимое возят во все стороны, двойное нажатие
 * возвращает как было, — и рисует то же самое, только через [ChartViewport]: оси остаются у
 * краёв, сетка и подписи едут вместе с содержимым, а при увеличении шаг сетки дробится.
 *
 * Нажатие на иконку заметки под графиком показывает её текст (или несколько заметок,
 * стоящих в одной точке) подсказкой над иконкой ([NotePopup]); повторное нажатие или
 * нажатие мимо закрывает её, сдвиг и масштаб — тоже, потому что иконка уезжает из-под
 * подсказки. Иконки и попадание считаются из одной [ChartFrame].
 */
@Composable
private fun DayChart(
    analytics: DayAnalytics,
    modifier: Modifier = Modifier,
    viewport: ChartViewport = ChartViewport.IDENTITY,
    onViewportChange: ((ChartViewport) -> Unit)? = null,
    /** Размер области под кривые при текущем размере холста — кнопкам масштаба диалога. */
    onPlotSize: ((Size) -> Unit)? = null,
) {
    val model = rememberChartModel(analytics) ?: return
    val density = LocalDensity.current
    // Кэш побольше стандартных восьми: при увеличении на холсте до семнадцати подписей
    // сетки на каждой оси, и во время жеста они измеряются каждый кадр.
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val axisStyle = DesignType.ChartAxisLabel
    // Весь набор цветов берётся здесь одним значением: тело `Canvas` — не композиция,
    // а [DesignPalette] читается только из неё. [DesignColors] — `@Immutable`, так что
    // держать его в локальной переменной ничего не стоит.
    val palette = DesignPalette
    val notes = analytics.notes
    val hasNotes = notes.isNotEmpty()
    val unit = LocalUnits.current.temperature

    // `viewport` читается в лямбдах рисования и жестов через замыкание — там он всегда
    // текущий, и на каждое его изменение перерисовывается только холст.
    val viewportNow by rememberUpdatedState(viewport)
    val onViewportChangeNow by rememberUpdatedState(onViewportChange)

    // Открытая подсказка заметки — группа под иконкой; `null` — закрыта. Размер холста
    // нужен, чтобы поставить подсказку над иконкой и найти иконку для действия TalkBack.
    var shownNote by remember { mutableStateOf<NoteGroup?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Сдвиг или масштаб уводят иконку из-под подсказки — подсказка закрывается.
    LaunchedEffect(viewport) { shownNote = null }

    // Иконки заметок при текущем размере и сдвиге — одна функция и для рисования, и для
    // попадания пальца, чтобы иконка нажималась ровно там, где нарисована.
    fun Density.visibleNoteGroups(frame: ChartFrame, view: ChartViewport): List<NoteGroup> {
        val iconSize = NoteIconSize.toPx()
        // Сначала — какие заметки вообще в области, потом группировка: иначе группа,
        // чья первая заметка уехала за левый край, утянула бы за собой и видимых соседей.
        val visible = notes.mapNotNull { note ->
            val x = frame.plotLeft + view.x(model.fraction(note.minutes), frame.plotWidth)
            if (x < frame.plotLeft || x > frame.plotRight) null else note to x
        }
        return noteGroups(visible.map { it.first }, visible.map { it.second }, iconSize).map { group ->
            // Иконка у самого края — целиком в области, а не наполовину в поле оси.
            group.copy(x = group.x.coerceIn(frame.plotLeft + iconSize / 2, frame.plotRight - iconSize / 2))
        }
    }

    // Для тех, кто график не видит: что на нём и какие заметки можно открыть без жеста.
    val description = remember(analytics, unit) { chartDescription(analytics, unit) }
    val semantics = Modifier.semantics {
        contentDescription = description
        if (hasNotes) {
            customActions = notes.map { note ->
                CustomAccessibilityAction("Заметка ${note.time}") {
                    val frame = with(density) { chartFrame(canvasSize.toSize(), model.right != null, hasNotes) }
                    val group = with(density) { visibleNoteGroups(frame, viewportNow) }
                        .firstOrNull { note in it.notes }
                    shownNote = group ?: NoteGroup(x = (frame.plotLeft + frame.plotRight) / 2f, notes = listOf(note))
                    true
                }
            }
        }
    }

    val gestures = if (onViewportChange == null) Modifier else Modifier.pointerInput(model, hasNotes) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val frame = chartFrame(size.toSize(), model.right != null, hasNotes)
            if (!frame.valid) return@detectTransformGestures
            onViewportChangeNow?.invoke(
                viewportNow.transformed(
                    zoom = zoom,
                    cx = centroid.x - frame.plotLeft,
                    cy = centroid.y - frame.plotBottom,
                    panX = pan.x,
                    panY = pan.y,
                    plotWidth = frame.plotWidth,
                    plotHeight = frame.plotHeight,
                )
            )
        }
    }
    // Что было открыто в момент нажатия — читается на `press`, а не на `tap`: подсказка не
    // глотает касания снаружи себя, и второе нажатие по той же иконке приходит сразу в
    // два окна — подсказка закрывается по `ACTION_OUTSIDE`, а холст получает обычный тап,
    // и в каком порядке — не определено. На `press` подсказка ещё точно на экране (см.
    // тот же приём в `TruncatedText`), и «нажали по открытой — закрыть» не зависит от гонки.
    var pressedOn: NoteGroup? = null
    val taps = Modifier.pointerInput(model, hasNotes) {
        detectTapGestures(
            onPress = { pressedOn = shownNote },
            onDoubleTap = if (onViewportChange == null) null else {
                { onViewportChangeNow?.invoke(ChartViewport.IDENTITY) }
            },
            onTap = { tap ->
                if (!hasNotes) return@detectTapGestures
                val frame = chartFrame(size.toSize(), model.right != null, hasNotes)
                if (!frame.valid) return@detectTapGestures
                val groups = visibleNoteGroups(frame, viewportNow)
                val slop = NoteTouchSlop.toPx()
                val rowTop = frame.noteRowTop - slop
                val rowBottom = frame.noteRowTop + frame.noteRowHeight + slop
                if (tap.y !in rowTop..rowBottom) return@detectTapGestures
                val hit = groups.minByOrNull { abs(it.x - tap.x) }
                    ?.takeIf { abs(it.x - tap.x) <= slop + NoteIconSize.toPx() / 2 }
                    ?: return@detectTapGestures
                shownNote = if (pressedOn?.notes == hit.notes) null else hit
            },
        )
    }

    val sizeReport = Modifier.onSizeChanged { px ->
        canvasSize = px
        if (onPlotSize != null) {
            val frame = with(density) { chartFrame(px.toSize(), model.right != null, hasNotes) }
            onPlotSize(Size(frame.plotWidth, frame.plotHeight))
        }
    }

    Box(modifier) {
    shownNote?.let { group ->
        val frame = with(density) { chartFrame(canvasSize.toSize(), model.right != null, hasNotes) }
        NotePopup(
            anchor = Offset(group.x, frame.noteRowTop + frame.noteRowHeight / 2f),
            notes = group.notes,
            onDismiss = { shownNote = null },
        )
    }
    Canvas(modifier = Modifier.fillMaxSize().then(sizeReport).then(semantics).then(gestures).then(taps)) {
        val frame = chartFrame(size, model.right != null, hasNotes)
        if (!frame.valid) return@Canvas
        val view = viewportNow
        val left = model.left
        val right = model.right
        val plotLeft = frame.plotLeft
        val plotRight = frame.plotRight
        val plotTop = frame.plotTop
        val plotBottom = frame.plotBottom
        val plotWidth = frame.plotWidth
        val plotHeight = frame.plotHeight

        fun x(minutes: Int): Float = plotLeft + view.x(model.fraction(minutes), plotWidth)
        fun y(series: ChartSeries, value: Double): Float =
            plotBottom + view.yFromBottom(series.axis.fraction(value), plotHeight)
        fun insidePlotY(lineY: Float): Boolean = lineY >= plotTop - 0.5f && lineY <= plotBottom + 0.5f

        // Засечки с дроблением шага при увеличении; у обеих осей их поровну, так что
        // правая раскладывается по тем же линиям, что и левая. Линии сетки стоят на всех
        // засечках, подпись — только на тех, что печатаются точно (`printable`): иначе
        // «60.13» на влажности рядом с линией на 60.125.
        val leftTicks = left.axis.ticks(view.subdivisions)
        val rightTicks = right?.axis?.ticks(view.subdivisions)

        // Содержимое — в пределах области: при сдвиге кривые уезжают за поля осей, и
        // без обрезки они легли бы поверх подписей.
        clipRect(plotLeft, plotTop, plotRight, plotBottom) {
            // 1. Проветривания — фоном под всем остальным: это отрезок времени, а не событие.
            analytics.events.filter { it.kind == ChartEventKind.Airing }.forEach { event ->
                val minWidth = MinAiringWidth.toPx().coerceAtMost(plotWidth)
                val rawStart = x(event.minutes)
                val end = x(event.minutes + event.durationMinutes)
                if (rawStart > plotRight || end < plotLeft) return@forEach
                // Замер в самом конце окна дал бы полоску нулевой ширины — сдвигаем её
                // внутрь, а не растягиваем за правый край.
                val start = rawStart.coerceAtMost(plotRight - minWidth)
                drawRect(
                    color = palette.ChartAiring,
                    topLeft = Offset(start, plotTop),
                    size = Size((end - start).coerceAtLeast(minWidth), plotHeight),
                )
            }

            // 2. Сетка — по засечкам левой оси.
            leftTicks.forEach { tick ->
                val lineY = y(left, tick)
                if (!insidePlotY(lineY)) return@forEach
                drawLine(
                    color = palette.ChartGrid,
                    start = Offset(plotLeft, lineY),
                    end = Offset(plotRight, lineY),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // 3. Переворот и заметка — вертикальная черта через весь график. Разница
            // только в цвете и пунктире: событий в одну минуту может быть и два сразу.
            analytics.events.forEach { event ->
                val kindColor = when (event.kind) {
                    ChartEventKind.Turn -> palette.ChartTurn
                    ChartEventKind.Note -> palette.ChartNote
                    ChartEventKind.Airing -> return@forEach
                }
                val lineX = x(event.minutes)
                if (lineX < plotLeft || lineX > plotRight) return@forEach
                drawLine(
                    color = kindColor,
                    start = Offset(lineX, plotTop),
                    end = Offset(lineX, plotBottom),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = if (event.kind == ChartEventKind.Note) {
                        PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    } else null,
                )
            }

            // 4. Планы закладок — пунктирные горизонтали цветом закладки, каждая на оси
            // своей величины. Под линиями показаний: план — фон, к которому их читают.
            val planDash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            // Подпись значения — одна на значение, а не на закладку: две закладки одного
            // вида на одном дне просят одно и то же, их линии совпадают, и две подписи
            // двух цветов друг на друге не читались бы ни одна.
            val labelled = mutableSetOf<Pair<ChartSeries, Double>>()
            analytics.planLines.forEachIndexed { index, line ->
                val color = model.planColors[index]
                listOfNotNull(model.tempSeries, model.dampSeries).forEach { series ->
                    val value = series.planValueOf(line) ?: return@forEach
                    val lineY = y(series, value)
                    if (!insidePlotY(lineY)) return@forEach
                    drawLine(
                        color = color,
                        start = Offset(plotLeft, lineY),
                        end = Offset(plotRight, lineY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = planDash,
                    )
                    if (!labelled.add(series to round2(value))) return@forEach
                    // Значение у правого края над линией — оно и говорит, температура
                    // это или влажность: оси у них разные, а цвет один на закладку.
                    val layout = measurer.measure(series.planLabel(value), axisStyle)
                    drawText(
                        textLayoutResult = layout,
                        color = color,
                        topLeft = Offset(
                            x = plotRight - 4.dp.toPx() - layout.size.width,
                            y = lineY - layout.size.height - 1.dp.toPx(),
                        ),
                    )
                }
            }

            // 5. Линии показаний. Влажность рисуется первой: температура — главная, ей
            // быть сверху там, где линии пересекаются.
            listOfNotNull(right, left).forEach { series ->
                val plotted = analytics.points.mapNotNull { point ->
                    series.valueOf(point)?.let { Offset(x(point.minutes), y(series, it)) }
                }
                if (plotted.size >= 2) {
                    val path = Path().apply {
                        moveTo(plotted.first().x, plotted.first().y)
                        plotted.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = series.color,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                plotted.forEach { drawCircle(series.color, radius = 3.dp.toPx(), center = it) }
            }
        }

        // 6. Подписи осей. Левая прижата к сетке справа, правая — слева от своего края.
        // Уехавшие за область не рисуются — подпись без линии сетки ни о чём.
        leftTicks.forEachIndexed { index, tick ->
            val lineY = y(left, tick)
            if (!insidePlotY(lineY)) return@forEachIndexed
            if (left.axis.printable(tick, left.labelDecimals)) {
                val layout = measurer.measure(left.label(tick), axisStyle)
                drawText(
                    textLayoutResult = layout,
                    color = palette.ChartAxisLabel,
                    topLeft = Offset(
                        x = plotLeft - 4.dp.toPx() - layout.size.width,
                        y = lineY - layout.size.height / 2f,
                    ),
                )
            }
            val rightTick = rightTicks?.get(index)
            if (right != null && rightTick != null && right.axis.printable(rightTick, right.labelDecimals)) {
                val rightLayout = measurer.measure(right.label(rightTick), axisStyle)
                drawText(
                    textLayoutResult = rightLayout,
                    color = palette.ChartAxisLabel,
                    topLeft = Offset(
                        x = plotRight + 4.dp.toPx(),
                        y = lineY - rightLayout.size.height / 2f,
                    ),
                )
            }
        }

        // 7. Иконки заметок — под концом пунктира каждой, в своём ряду между областью и
        // временем. Заметки в одну точку — одна иконка (и один список по нажатию).
        if (hasNotes) {
            val iconSize = NoteIconSize.toPx()
            val centerY = frame.noteRowTop + frame.noteRowHeight / 2f
            visibleNoteGroups(frame, view).forEach { group ->
                drawNoteIcon(
                    center = Offset(group.x, centerY),
                    size = iconSize,
                    color = palette.ChartNote,
                    fill = palette.Surface,
                    stacked = group.notes.size > 1,
                )
            }
        }

        // 8. Время под графиком. Подписей столько, сколько поместится, не налезая друг на
        // друга: в карточке это две-три, как в макете, при увеличении — больше.
        val visible = analytics.points.map { it to x(it.minutes) }
            .filter { (_, px) -> px >= plotLeft - 0.5f && px <= plotRight + 0.5f }
        // Ширина подписи — по самой широкой из возможных и измеренная здесь же, а не в
        // композиции: шрифт приезжает из Google Fonts после первого измерения, и
        // запомненная ширина осталась бы шириной запасного.
        val timeLabelWidth = measurer.measure("00:00", axisStyle).size.width.toFloat()
        timeLabelIndices(visible.map { it.second }, timeLabelWidth + 8.dp.toPx()).forEach { index ->
            val (point, px) = visible[index]
            val layout = measurer.measure(point.time, axisStyle)
            val centered = px - layout.size.width / 2f
            drawText(
                textLayoutResult = layout,
                color = palette.ChartAxisLabel,
                topLeft = Offset(
                    x = centered.coerceIn(0f, size.width - layout.size.width),
                    y = frame.timeLabelTop,
                ),
            )
        }
    }
    }
}

/**
 * Значок заметки: листок со строчками. Рисуется здесь, а не берётся картинкой, чтобы его
 * положение считалось из той же геометрии, что и попадание пальца. [stacked] — под
 * иконкой несколько заметок: сзади выглядывает второй листок.
 */
private fun DrawScope.drawNoteIcon(
    center: Offset,
    size: Float,
    color: Color,
    fill: Color,
    stacked: Boolean,
) {
    val w = size * 0.78f
    val h = size
    val stroke = 1.2.dp.toPx()
    val corner = CornerRadius(1.5.dp.toPx())
    if (stacked) {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - w / 2 + 2.dp.toPx(), center.y - h / 2 - 2.dp.toPx()),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
    }
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    // Заливка — чтобы у стопки передний листок закрывал строчки заднего.
    drawRoundRect(color = fill, topLeft = topLeft, size = Size(w, h), cornerRadius = corner)
    drawRoundRect(color = color, topLeft = topLeft, size = Size(w, h), cornerRadius = corner, style = Stroke(stroke))
    val inset = w * 0.22f
    listOf(0.38f, 0.58f).forEach { fraction ->
        val lineY = topLeft.y + h * fraction
        drawLine(
            color = color,
            start = Offset(topLeft.x + inset, lineY),
            end = Offset(topLeft.x + w - inset, lineY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Легенда графика. Показания в ней всегда, события — только те, что за день случились:
 * обещать отметку, которой на графике нет, незачем.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(analytics: DayAnalytics, singleLine: Boolean = false) {
    val kinds = analytics.events.map { it.kind }.toSet()
    val palette = DesignPalette
    val series: @Composable () -> Unit = {
        if (analytics.hasTemp) LegendItem("температура", palette.ChartTemp, LegendMark.Line)
        if (analytics.hasDamp) LegendItem("влажность", palette.ChartDamp, LegendMark.Line)
    }
    val events: @Composable () -> Unit = {
        if (ChartEventKind.Turn in kinds) LegendItem("переворот", palette.ChartTurn, LegendMark.Bar)
        if (ChartEventKind.Airing in kinds) LegendItem("проветривание", palette.ChartAiring, LegendMark.Band)
        if (ChartEventKind.Note in kinds) LegendItem("заметка", palette.ChartNote, LegendMark.Bar)
    }
    val plans: @Composable () -> Unit = {
        Text(
            text = "план:",
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        analytics.planLines.forEachIndexed { index, line ->
            LegendItem(
                text = line.label,
                color = palette.ChartBars[index % palette.ChartBars.size],
                mark = LegendMark.Dash,
            )
        }
    }
    if (singleLine) {
        // В ландшафте высота — самое дорогое, и легенда идёт одной строкой; что не
        // поместилось, доступно прокруткой вбок, а не переносом на вторую.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            series()
            events()
            if (analytics.planLines.isNotEmpty()) plans()
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { series() }
        if (kinds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { events() }
        }
        // Планы закладок — третьим рядом и `FlowRow`: закладок может быть пять, их
        // названия — любой длины, и в одну строку они не встанут.
        if (analytics.planLines.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { plans() }
        }
    }
}

/** Значок в легенде повторяет то, чем величина или событие нарисованы на графике. */
private enum class LegendMark { Line, Bar, Band, Dash }

@Composable
private fun LegendItem(text: String, color: Color, mark: LegendMark) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (mark) {
            LegendMark.Line -> Box(
                Modifier
                    .size(width = 14.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            LegendMark.Bar -> Box(
                Modifier
                    .size(width = 2.dp, height = 12.dp)
                    .background(color)
            )

            LegendMark.Band -> Box(
                Modifier
                    .size(width = 14.dp, height = 12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )

            // Пунктир — как линия плана на графике: три чёрточки с просветами.
            LegendMark.Dash -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 2.dp)
                            .background(color)
                    )
                }
            }
        }
        Text(
            text = text,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Название закладки пишет человек; в легенде ему отведено не больше половины
            // карточки, остальное — многоточием. Целиком оно есть в «Справке» шторки.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
    }
}

// --- На весь экран -------------------------------------------------------------------------

/**
 * Тот же график на весь экран: заголовок, строка кнопок (поворот и масштаб), карточка с
 * графиком на всю оставшуюся высоту и легенда. `Dialog` без платформенной ширины — окно
 * на весь экран в любой ориентации.
 *
 * **Ориентацию меняет только кнопка, датчик — нет.** Пока диалог открыт, активность
 * заперта в текущей ориентации (`SCREEN_ORIENTATION_LOCKED`), а кнопка поворота просит
 * другую (`PORTRAIT` / `LANDSCAPE`). Так на график смотрят, наклонив телефон как удобно,
 * и он не переворачивается под рукой; поворот — намеренное действие, а не следствие
 * того, как лежит устройство. Смена ориентации пересоздаёт активность, поэтому и
 * «раскрыт», и запрошенная ориентация лежат в `rememberSaveable`; масштаб при этом
 * сбрасывается — область графика стала другой, и прежний сдвиг показывал бы не то место.
 * Уходя, диалог возвращает `UNSPECIFIED`, но не в момент пересоздания
 * (`isChangingConfigurations`): иначе он отпускал бы ориентацию ровно тогда, когда сам её
 * попросил.
 *
 * `decorFitsSystemWindows = false` и `safeDrawingPadding` — иначе на Android 15+ окно
 * диалога само рисуется под системные полосы, и заголовок уходит под статус-бар.
 */
@Composable
private fun FullscreenChartDialog(
    analytics: DayAnalytics,
    onDismiss: () -> Unit,
) {
    var viewport by remember { mutableStateOf(ChartViewport.IDENTITY) }
    // Размер области графика — для кнопок масштаба: щипок знает, где пальцы, а кнопке
    // нужен центр области, и его сообщает сам график.
    var plotSize by remember { mutableStateOf(Size.Zero) }

    val activity = LocalContext.current.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var requestedOrientation by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
    }
    LaunchedEffect(activity, requestedOrientation) {
        activity?.requestedOrientation = requestedOrientation
    }
    DisposableEffect(activity) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Окно диалога лежит под статус-баром своим окном, и значки на полосе берутся из
        // его флагов: без этого над кремовым фоном оставались белые часы экрана с зелёной
        // шапкой, с которого аналитику открыли.
        DialogStatusBar()
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = SheetPadding)
                    .padding(bottom = 16.dp),
            ) {
                SheetHeader(title = "Показания в течение дня", onClose = onDismiss)
                Spacer(Modifier.height(if (landscape) 4.dp else 8.dp))
                // Поворот слева, масштаб справа. Кнопки масштаба — тот же щипок без щипка,
                // для тех, кому жест недоступен (TalkBack, одна рука, стилус); они меняют его
                // вокруг середины области, как два пальца в центре.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // С подписью, а не одной иконкой: круглая стрелка без слова читалась как
                    // «обновить». Иконка — телефон со стрелкой поворота, слово говорит остальное.
                    ChartToolButton(
                        icon = painterResource(R.drawable.ic_rotate_design),
                        label = "Повернуть",
                        description = if (landscape) "Повернуть вертикально" else "Повернуть горизонтально",
                    ) {
                        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    Spacer(Modifier.weight(1f))
                    fun zoomBy(factor: Float) {
                        if (plotSize.width <= 0f || plotSize.height <= 0f) return
                        viewport = viewport.transformed(
                            zoom = factor,
                            cx = plotSize.width / 2f,
                            cy = -plotSize.height / 2f,
                            panX = 0f,
                            panY = 0f,
                            plotWidth = plotSize.width,
                            plotHeight = plotSize.height,
                        )
                    }
                    ChartToolButton(label = "−", description = "Мельче") { zoomBy(1f / ZOOM_STEP) }
                    Spacer(Modifier.width(6.dp))
                    ChartToolButton(label = "+", description = "Крупнее") { zoomBy(ZOOM_STEP) }
                    Spacer(Modifier.width(6.dp))
                    ChartToolButton(
                        label = "1:1",
                        description = "Как было",
                        enabled = viewport.zoomed,
                    ) { viewport = ChartViewport.IDENTITY }
                }
                // В ландшафте высоты мало: зазоры вокруг графика вдвое меньше, легенда одной
                // строкой с прокруткой вбок.
                val gap = if (landscape) 6.dp else 12.dp
                Spacer(Modifier.height(gap))
                Surface(
                    shape = RoundedCornerShape(CardRadius),
                    color = DesignPalette.Surface,
                    border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    DayChart(
                        analytics = analytics,
                        viewport = viewport,
                        onViewportChange = { viewport = it },
                        onPlotSize = { plotSize = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = if (landscape) 8.dp else 12.dp),
                    )
                }
                Spacer(Modifier.height(gap))
                ChartLegend(analytics, singleLine = landscape)
            }
        }
    }
}

// --- Заметка -------------------------------------------------------------------------------

/** Подсказка заметки не шире карточки телефона: длинная заметка переносится, а не тянется. */
private val NotePopupWidth = 280.dp

/**
 * Текст заметки подсказкой над её иконкой — как у обрезанного названия в `TruncatedText`,
 * и по той же причине не диалог: заметка читается на месте, рядом с точкой, к которой
 * относится, и убирается нажатием куда угодно. Несколько заметок — когда они записаны в
 * одну минуту и стоят под одной иконкой; каждая подписана своим временем.
 *
 * [anchor] — центр иконки в координатах холста; подсказка встаёт над ним, а если сверху
 * места нет (иконка у самого верха окна не бывает, но ландшафт мелкий) — под ним. Окно
 * подсказки не перехватывает касания снаружи (`focusable = false`), только закрывается
 * по ним — поэтому нажатие по другой иконке сразу открывает другую заметку.
 */
@Composable
private fun NotePopup(anchor: Offset, notes: List<ChartEvent>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val provider = remember(anchor, density) {
        val gap = with(density) { (NoteIconSize / 2 + 6.dp).roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchorBounds.left + anchor.x - popupContentSize.width / 2f).roundToInt()
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = (anchorBounds.top + anchor.y).roundToInt() - gap - popupContentSize.height
                val y = if (above >= 0) above else (anchorBounds.top + anchor.y).roundToInt() + gap
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DesignPalette.Surface,
            border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = NotePopupWidth),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                notes.forEach { note ->
                    Column {
                        Text(
                            text = note.time,
                            style = DesignType.MonoEmphasis,
                            color = DesignPalette.Accent,
                        )
                        Text(
                            text = note.note,
                            style = DesignType.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// --- Плитки-итоги --------------------------------------------------------------------------

/**
 * Двенадцать плиток тремя рядами по четыре: сперва счётчики за день, затем температура и
 * влажность одинаковым набором «среднее — разброс — крайние».
 *
 * Ряд читается по своим значениям: во втором все они с градусом, в третьем — с процентом,
 * поэтому «Минимум» и «Максимум» повторяются подписью, не уточняя, чего именно.
 *
 * Плитка «В цель ±0.3°» прежнего макета сюда не переехала — её место заняли крайние
 * значения влажности, и доля попаданий больше нигде не показывается.
 */
@Composable
private fun StatGrid(analytics: DayAnalytics) {
    val unit = LocalUnits.current.temperature
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            StatTile("Замеров", analytics.total.toString())
            StatTile("Переворотов", analytics.turns.toString())
            StatTile("Проветриваний", analytics.airings.toString())
            StatTile(
                // Единица в подписи, а не при числе. Макет пишет «Проветрено» и «130
                // мин», но плиток в ряду четыре, и «130 мин» в четверть ширины экрана
                // обрезается в «130 …» — то есть теряет ровно то, ради чего плитка
                // существует. Подписи перенос по слогам разрешён, и «Проветрено, мин»
                // просто встаёт в три строки, как соседнее «Провет-ри-ваний».
                label = "Проветрено, мин",
                value = analytics.airingMinutes.toString(),
                // Единственная плитка сетки, которую макет красит коричневым.
                valueColor = DesignPalette.DateEmphasis,
            )
        }
        StatRow {
            StatTile("Средняя t", analytics.tempAvg.asTemp(unit))
            StatTile("Разброс", analytics.tempSpread.asTempDelta(unit))
            StatTile("Минимум", analytics.tempMin.asTemp(unit))
            StatTile("Максимум", analytics.tempMax.asTemp(unit))
        }
        StatRow {
            StatTile("Ср. влажность", analytics.dampAvg.asDamp())
            StatTile("Разброс", analytics.dampSpread.asDamp())
            StatTile("Минимум", analytics.dampMin.asDamp())
            StatTile("Максимум", analytics.dampMax.asDamp())
        }
    }
}

/**
 * Ряд плиток одной высоты. Подписи вроде «Проветриваний» в четверть ширины не всегда
 * влезают в строку, и без выравнивания по высоте соседние плитки в ряду разъезжались бы.
 *
 * `internal`, потому что этой же сеткой набрана карточка «Итог по замерам» в
 * [BatchDetailSheet]: там те же вопросы, только за всю инкубацию.
 */
@Composable
internal fun StatRow(content: @Composable RowScope.() -> Unit) {
    TileRow(content = content)
}

/**
 * [surface] и [border] — единственное, чем плитка итога отличается от плитки аналитики:
 * здесь она белая с обводкой, потому что лежит на кремовом фоне шторки, а внутри белой
 * карточки «Итога» нужна заливка [DesignPalette.MeasureTile] и никакой обводки. Всё
 * остальное — размер шрифта, переносы, выравнивание по высоте ряда — у них общее, и
 * заводить ради двух цветов вторую такую плитку значило бы развести их при первой же
 * правке ширины.
 */
@Composable
internal fun RowScope.StatTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    surface: Color = DesignPalette.Surface,
    border: BorderStroke? = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = surface,
        border = border,
        modifier = tileWeight().heightIn(min = 66.dp),
    ) {
        // Справа 6 dp вместо макетных 12: плиток в ряду четыре, и «Проветриваний» с
        // «Ср. влажность» в четверть ширины экрана иначе не помещаются в строку. Перенос
        // им всё равно разрешён (по слогам), но обычному телефону хватает и одной строки.
        Column(Modifier.padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            // Плитка в четверть ширины, и на крупном кегле «100.4°» или «1 240» в неё не
            // влезают; обрезанная цифра показывается целиком по нажатию — правило
            // плиток «Финансов» и дней «Расписания», то же самое и здесь.
            TruncatedText(
                text = value,
                style = DesignType.AnalyticsValue,
                color = valueColor,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = DesignType.Micro.copy(hyphens = Hyphens.Auto),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Температура в плитке — с одним знаком: «36.3°», «1.0°». */
internal fun Double?.asTemp(unit: TemperatureUnit): String =
    this?.let { "${roundTo1(unit.fromCelsius(it)).formatTemp()}°" } ?: "—"

/** Разница температур в плитке — разброс: без сдвига шкалы, только длина градуса. */
internal fun Double?.asTempDelta(unit: TemperatureUnit): String =
    this?.let { "${it.formatTempDelta(unit)}°" } ?: "—"

/** Влажность в плитке — целая: «55%», а не «55.33%». */
internal fun Double?.asDamp(): String =
    this?.let { "${Math.round(it).toDouble().formatDamp()}%" } ?: "—"

// --- Вывод ---------------------------------------------------------------------------------

/**
 * Начертания для кусков [insightParts] / [totalsInsightParts]: обычный текст, смысловое
 * слово полужирным и цифра моноширинной.
 *
 * Здесь, а не у каждого, кто это рисует: карточка «Итог по замерам» в
 * [BatchDetailSheet] показывает такую же строку, и два разбора одной и той же модели
 * разошлись бы при первой же правке.
 */
internal fun insightAnnotated(parts: List<InsightPart>): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        when (part.emphasis) {
            InsightEmphasis.Plain -> append(part.text)
            InsightEmphasis.Strong -> withStyle(
                SpanStyle(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold)
            ) { append(part.text) }

            InsightEmphasis.Figure -> withStyle(
                SpanStyle(fontFamily = monoFontFamily, fontWeight = FontWeight.SemiBold)
            ) { append(part.text) }
        }
    }
}

@Composable
private fun InsightCard(analytics: DayAnalytics) {
    val unit = LocalUnits.current.temperature
    val text = remember(analytics, unit) { insightAnnotated(insightParts(analytics, unit)) }
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = DesignPalette.InsightSurface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = DesignType.Body,
            color = DesignPalette.Accent,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// --- Общее ---------------------------------------------------------------------------------

/** Белая карточка шторки — та же, что в [BatchDetailSheet]. */
@Composable
private fun AnalyticsCard(radius: Dp, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = DesignPalette.Surface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/**
 * Кнопка в строке над графиком на весь экран — поворот, «−», «+», «1:1». Маленькая и
 * обведённая, как кнопки над таблицей расписания: это вспомогательный орган управления,
 * а не главное действие экрана. [icon] и / или [label]; [description] — что кнопка
 * делает, для TalkBack.
 */
@Composable
private fun ChartToolButton(
    description: String,
    label: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // `Surface`, а не `OutlinedButton`: у того минимальная ширина 58 dp, и четыре таких
    // кнопки не встали бы в строку на узком экране.
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = DesignPalette.Surface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 36.dp)
                .padding(horizontal = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (label != null) {
                    Text(text = label, style = DesignType.MonoEmphasis, color = tint)
                }
            }
        }
    }
}

/** Активность, в окне которой живёт композиция; `null` — превью и тесты. */
private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
