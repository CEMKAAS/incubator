package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.components.FormSpacer
import ru.zaroslikov.incubator.ui.components.SheetDragHandle
import ru.zaroslikov.incubator.ui.components.SheetHeader
import ru.zaroslikov.incubator.ui.components.SheetPadding
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType
import ru.zaroslikov.incubator.ui.theme.bodyFontFamily
import ru.zaroslikov.incubator.ui.theme.monoFontFamily

private val CardRadius = 22.dp
private val TileRadius = 16.dp
private val CardBorderWidth = 0.8.dp

/** Высота поля графика из макета (узел 16:7846). */
private val ChartHeight = 192.dp

/** Место под подписи оси слева и справа и под время внизу. */
private val AxisGutterLeft = 34.dp
private val AxisGutterRight = 28.dp
private val AxisGutterBottom = 18.dp
private val ChartTopPadding = 8.dp

/** Минимальная ширина окна проветривания: нулевую длительность тоже надо увидеть. */
private val MinAiringWidth = 3.dp

/** Ширина окна графика, когда все замеры пришлись на одну минуту, в минутах. */
private const val MIN_SPAN_MINUTES = 30

/**
 * «Аналитика за день» — макет
 * [16:6632](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=16-6632):
 * график показаний за день, девять плиток-итогов и текст-вывод под ними.
 *
 * Шторка поверх шторки закладки, а не отдельный экран: у макета скруглённый верх, «ручка»
 * и крестик, и открывается она кнопкой «Аналитика» прямо над списком замеров — за ней
 * должна остаться сама закладка. Маршрута в навигации, как и у [BatchDetailSheet], нет.
 *
 * Данные приходят готовыми — теми же, что уже загрузил [BatchDetailViewModel]: своей
 * модели у шторки нет, читать второй раз то же самое незачем.
 *
 * Отступления от макета, все намеренные:
 * — линия температуры красная, а не зелёная: зелёный в приложении означает акцент и
 *   «в норме», и на графике он спорил бы сам с собой;
 * — рядом с ней вторая линия, синяя, — влажность, со своей осью справа;
 * — переворот и заметка отмечены вертикальной чертой через весь график (у заметки —
 *   пунктирной), проветривание закрашивает временную область: у него, в отличие от них,
 *   есть длительность;
 * — линии между замерами прямые, хотя в макете кривая: сглаженная кривая выгибается выше
 *   максимума, а «Максимум» стоит плиткой прямо под графиком.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAnalyticsSheet(
    measurements: List<Measurement>,
    plan: Value?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val analytics = remember(measurements, plan) { analyticsOf(measurements, plan) }

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
            ChartCard(analytics)

            FormSpacer(16.dp)
            StatGrid(analytics)

            FormSpacer(16.dp)
            InsightCard(analytics)
        }
    }
}

// --- График --------------------------------------------------------------------------------

@Composable
private fun ChartCard(analytics: DayAnalytics) {
    AnalyticsCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Показания в течение дня",
                style = DesignType.ChartTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            if (analytics.hasChart) {
                DayChart(analytics)
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
)

@Composable
private fun DayChart(analytics: DayAnalytics) {
    val measurer = rememberTextMeasurer()
    val axisStyle = DesignType.ChartAxisLabel

    val temps = analytics.points.mapNotNull { it.temp }
    val damps = analytics.points.mapNotNull { it.damp }

    val tempSeries = if (temps.isEmpty()) null else ChartSeries(
        axis = niceAxis(temps.min(), temps.max(), TEMP_AXIS_STEPS),
        color = DesignPalette.ChartTemp,
        label = { it.formatTemp() },
        valueOf = { it.temp },
    )
    val dampSeries = if (damps.isEmpty()) null else ChartSeries(
        axis = niceAxis(damps.min(), damps.max(), DAMP_AXIS_STEPS),
        color = DesignPalette.ChartDamp,
        label = { it.formatDamp() },
        valueOf = { it.damp },
    )
    // Температура — главная величина, поэтому подписи её оси слева, как в макете;
    // влажность уходит вправо. Если температуры нет вовсе, влажность занимает её место.
    val left = tempSeries ?: dampSeries ?: return
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
    val span = (to - from).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)
    ) {
        val plotLeft = AxisGutterLeft.toPx()
        val plotRight = size.width - if (right != null) AxisGutterRight.toPx() else 4.dp.toPx()
        val plotTop = ChartTopPadding.toPx()
        val plotBottom = size.height - AxisGutterBottom.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun x(minutes: Int): Float = plotLeft + (minutes - from) / span * plotWidth
        fun y(series: ChartSeries, value: Double): Float =
            plotBottom - series.axis.fraction(value) * plotHeight

        // 1. Проветривания — фоном под всем остальным: это отрезок времени, а не событие.
        analytics.events.filter { it.kind == ChartEventKind.Airing }.forEach { event ->
            val minWidth = MinAiringWidth.toPx().coerceAtMost(plotWidth)
            // Замер в самом конце окна дал бы полоску нулевой ширины — сдвигаем её внутрь,
            // а не растягиваем за правый край.
            val start = x(event.minutes).coerceIn(plotLeft, plotRight - minWidth)
            val end = x(event.minutes + event.durationMinutes).coerceIn(plotLeft, plotRight)
            drawRect(
                color = DesignPalette.ChartAiring,
                topLeft = Offset(start, plotTop),
                size = Size((end - start).coerceAtLeast(minWidth), plotHeight),
            )
        }

        // 2. Сетка — по засечкам левой оси; правая ось на них же и раскладывается,
        // потому что засечек у обеих ровно AXIS_TICKS.
        left.axis.ticks.forEachIndexed { index, _ ->
            val lineY = plotBottom - plotHeight * index / (AXIS_TICKS - 1)
            drawLine(
                color = DesignPalette.ChartGrid,
                start = Offset(plotLeft, lineY),
                end = Offset(plotRight, lineY),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 3. Переворот и заметка — вертикальная черта через весь график. Разница только
        // в цвете и пунктире: событий в одну минуту может быть и два сразу.
        analytics.events.forEach { event ->
            val kindColor = when (event.kind) {
                ChartEventKind.Turn -> DesignPalette.ChartTurn
                ChartEventKind.Note -> DesignPalette.ChartNote
                ChartEventKind.Airing -> return@forEach
            }
            val lineX = x(event.minutes).coerceIn(plotLeft, plotRight)
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

        // 4. Линии показаний. Влажность рисуется первой: температура — главная, ей быть
        // сверху там, где линии пересекаются.
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

        // 5. Подписи осей. Левая прижата к сетке справа, правая — слева от своего края.
        left.axis.ticks.forEachIndexed { index, tick ->
            val layout = measurer.measure(left.label(tick), axisStyle)
            val lineY = plotBottom - plotHeight * index / (AXIS_TICKS - 1)
            drawText(
                textLayoutResult = layout,
                color = DesignPalette.ChartAxisLabel,
                topLeft = Offset(
                    x = plotLeft - 4.dp.toPx() - layout.size.width,
                    y = lineY - layout.size.height / 2f,
                ),
            )
        }
        right?.axis?.ticks?.forEachIndexed { index, tick ->
            val layout = measurer.measure(right.label(tick), axisStyle)
            val lineY = plotBottom - plotHeight * index / (AXIS_TICKS - 1)
            drawText(
                textLayoutResult = layout,
                color = DesignPalette.ChartAxisLabel,
                topLeft = Offset(
                    x = plotRight + 4.dp.toPx(),
                    y = lineY - layout.size.height / 2f,
                ),
            )
        }

        // 6. Время под графиком: начало, середина и конец — три подписи, как в макете.
        // Больше в 300 dp не помещается, и соседние «12:29» слились бы.
        timeLabels(analytics.points).forEach { point ->
            val layout = measurer.measure(point.time, axisStyle)
            val centered = x(point.minutes) - layout.size.width / 2f
            drawText(
                textLayoutResult = layout,
                color = DesignPalette.ChartAxisLabel,
                topLeft = Offset(
                    x = centered.coerceIn(0f, size.width - layout.size.width),
                    y = plotBottom + 2.dp.toPx(),
                ),
            )
        }
    }
}

/** Первый, средний и последний замер — по ним и подписано время. */
private fun timeLabels(points: List<ChartPoint>): List<ChartPoint> = when {
    points.size <= 2 -> points
    else -> listOf(points.first(), points[points.size / 2], points.last())
}

/**
 * Легенда графика. Показания в ней всегда, события — только те, что за день случились:
 * обещать отметку, которой на графике нет, незачем.
 */
@Composable
private fun ChartLegend(analytics: DayAnalytics) {
    val kinds = analytics.events.map { it.kind }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (analytics.hasTemp) LegendItem("температура", DesignPalette.ChartTemp, LegendMark.Line)
            if (analytics.hasDamp) LegendItem("влажность", DesignPalette.ChartDamp, LegendMark.Line)
        }
        if (kinds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (ChartEventKind.Turn in kinds) {
                    LegendItem("переворот", DesignPalette.ChartTurn, LegendMark.Bar)
                }
                if (ChartEventKind.Airing in kinds) {
                    LegendItem("проветривание", DesignPalette.ChartAiring, LegendMark.Band)
                }
                if (ChartEventKind.Note in kinds) {
                    LegendItem("заметка", DesignPalette.ChartNote, LegendMark.Bar)
                }
            }
        }
    }
}

/** Значок в легенде повторяет то, чем величина или событие нарисованы на графике. */
private enum class LegendMark { Line, Bar, Band }

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
        }
        Text(
            text = text,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Плитки-итоги --------------------------------------------------------------------------

@Composable
private fun StatGrid(analytics: DayAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            StatTile("Замеров", analytics.total.toString())
            StatTile("Средняя t", analytics.tempAvg.asTemp())
            StatTile(
                label = "В цель ±${TARGET_BAND_TEMP.formatTemp()}°",
                value = analytics.inTargetPercent?.let { "$it%" } ?: "—",
                // Макет красит эту плитку коричневым независимо от значения; своих
                // порогов «хорошо / плохо» для доли попаданий он не задаёт, и придумывать
                // их здесь не за что.
                valueColor = DesignPalette.DateEmphasis,
            )
        }
        StatRow {
            StatTile("Минимум", analytics.tempMin.asTemp())
            StatTile("Максимум", analytics.tempMax.asTemp())
            StatTile("Разброс", analytics.tempSpread.asTemp())
        }
        StatRow {
            StatTile("Ср. влажность", analytics.dampAvg?.let { "${it.roundedDamp()}%" } ?: "—")
            StatTile("Переворотов", analytics.turns.toString())
            StatTile("Проветриваний", analytics.airings.toString())
        }
    }
}

/**
 * Ряд плиток одной высоты. Подписи вроде «Проветриваний» в треть ширины не всегда влезают
 * в строку, и без выравнивания по высоте соседние плитки в ряду разъезжались бы.
 */
@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.StatTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = Color.White,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 66.dp),
    ) {
        // По горизонтали 10 dp вместо 12: «Проветриваний» — самая длинная подпись сетки,
        // и в треть ширины экрана она иначе не помещается в строку. Перенос ей всё равно
        // разрешён (переносится по слогам), но обычному телефону хватает и одной.
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = DesignType.AnalyticsValue,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private fun Double?.asTemp(): String = this?.let { "${roundTo1(it).formatTemp()}°" } ?: "—"

/** Влажность в плитке — целая: «55%», а не «55.33%». */
private fun Double.roundedDamp(): String = Math.round(this).toDouble().formatDamp()

// --- Вывод ---------------------------------------------------------------------------------

@Composable
private fun InsightCard(analytics: DayAnalytics) {
    val text = remember(analytics) {
        buildAnnotatedString {
            insightParts(analytics).forEach { part ->
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
    }
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
        color = Color.White,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}
