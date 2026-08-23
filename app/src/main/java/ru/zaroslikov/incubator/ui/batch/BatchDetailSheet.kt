package ru.zaroslikov.incubator.ui.batch

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.components.FormSpacer
import ru.zaroslikov.incubator.ui.components.SheetDragHandle
import ru.zaroslikov.incubator.ui.components.SheetHeader
import ru.zaroslikov.incubator.ui.components.SheetPadding
import ru.zaroslikov.incubator.ui.components.SheetTextField
import ru.zaroslikov.incubator.ui.incubator.shortDate
import ru.zaroslikov.incubator.ui.start.speciesChipColor
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType
import java.util.Locale
import kotlin.math.abs

private val CardRadius = 22.dp
private val TileRadius = 16.dp
private val InnerRadius = 12.dp
private val CardBorderWidth = 0.8.dp

/**
 * Отметка переворота в замере. Переворот делают один раз за замер, поэтому в форме это
 * кнопка, а не счётчик; в базе поле осталось строкой, и нажатая кнопка пишет в неё эту
 * метку. Замеры прежних версий могли хранить там число — история их не теряет.
 */
private const val TurnedMark = "1"

/** Сколько последних замеров видно в свёрнутой истории дня. */
private const val VisibleMeasurements = 3

/** Шкала отклонения из макета: у температуры ±1.5°, у влажности ±10 %. */
private const val TEMP_SCALE = 1.5
private const val DAMP_SCALE = 10.0

/**
 * Шторка одной закладки — макет
 * [14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893).
 *
 * Шторка, а не экран: у макета скруглённый верх, «ручка» и крестик — значит она
 * появляется поверх списка закладок инкубатора. Маршрута в навигации у неё нет,
 * идентификатор приходит параметром и уезжает в [BatchDetailViewModel.load], как
 * и у [AddBatchSheet].
 *
 * Содержимое: сводка по закладке, замеры за сегодня (ввод, отклонения от плана и
 * список записанного) и режим на завтра.
 *
 * Отступления от макета, все намеренные:
 * — «Расписание по дням» макет не рисует, но иначе к [BatchScreen] — списку дней с
 *   овоскопированием и завершением закладки — было бы не добраться;
 * — кнопка «Аналитика» открывает [BatchAnalyticsSheet] поверх этой шторки, а без
 *   единого замера погашена: считать за день было бы нечего;
 * — нижняя кнопка подписана «Готово» и закрывает шторку, а не «Сохранено»: замер
 *   сохраняется сразу по «Записать замер», и вечно неактивная кнопка внизу читалась
 *   бы как поломка;
 * — «Осталось сейчас» равно заложенному: выбраковку на овоскопировании приложение
 *   пока нигде не записывает, придумывать за пользователя нечего.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailSheet(
    batchId: Long,
    onDismiss: () -> Unit,
    navigateToSchedule: (Long) -> Unit,
    viewModel: BatchDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = viewModel.uiState
    val measurements = viewModel.measurements
    val form = viewModel.form
    // Замер удаляется без следа, а строка списка узкая, и крестик соседствует с «Изм.» —
    // спрашиваем, как и при удалении закладки.
    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    // «Аналитика за день» — шторка поверх этой, как и диалог удаления: закладка под ней
    // остаётся, и закрытие аналитики возвращает ровно то, откуда её открыли.
    var analyticsOpen by remember(batchId) { mutableStateOf(false) }

    LaunchedEffect(batchId) { viewModel.load(batchId) }

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
            SheetHeader(title = state.title, onClose = onDismiss)

            FormSpacer(20.dp)
            SummaryCard(state)

            FormSpacer(16.dp)
            InfoGrid(state)

            FormSpacer(16.dp)
            MeasurementsCard(
                state = state,
                measurements = measurements,
                form = form,
                onFormChange = viewModel::update,
                onSave = viewModel::save,
                onCancelEdit = viewModel::cancelEdit,
                onEdit = viewModel::startEdit,
                onDelete = { pendingDelete = it },
                onOpenAnalytics = {
                    AppMetrica.reportEvent("Переход в аналитику")
                    analyticsOpen = true
                },
            )

            state.plannedTomorrow?.let { tomorrow ->
                FormSpacer(16.dp)
                TomorrowCard(day = state.day + 1, plan = tomorrow)
            }

            FormSpacer(16.dp)
            ScheduleLink(onClick = { navigateToSchedule(state.batchId) })

            FormSpacer(16.dp)
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(TileRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = "Готово", style = DesignType.ButtonLabel)
            }
        }
    }

    if (analyticsOpen) {
        BatchAnalyticsSheet(
            measurements = measurements,
            plan = state.plannedToday,
            onDismiss = { analyticsOpen = false },
        )
    }

    pendingDelete?.let { measurement ->
        ConfirmDeleteMeasurementDialog(
            measurement = measurement,
            tempTarget = state.plannedToday?.temp,
            dampTarget = state.plannedToday?.damp,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(measurement)
                pendingDelete = null
            },
        )
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
private fun ConfirmDeleteMeasurementDialog(
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

@Composable
private fun SummaryCard(state: BatchDetailUiState) {
    SheetCard(radius = TileRadius) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(speciesChipColor(state.type)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = speciesEmoji(state.type), fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = state.title,
                    style = DesignType.SectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dayLine(state),
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** «День 15/21 · Инкубация» — вторая строка карточки. */
private fun dayLine(state: BatchDetailUiState): String {
    val status = if (state.finished) "Завершено" else "Инкубация"
    if (!state.loaded) return status
    val day = if (state.totalDays != null) "День ${state.day}/${state.totalDays}"
    else "День ${state.day}"
    return "$day · $status"
}

@Composable
private fun InfoGrid(state: BatchDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(
                label = "Заложено яиц",
                value = state.eggAll.toString(),
                modifier = Modifier.weight(1f),
            )
            InfoTile(
                label = if (state.finished) "Выведено" else "Осталось сейчас",
                value = if (state.finished) state.eggAllEND.toString() else state.eggAll.toString(),
                highlighted = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(
                label = "Заложено",
                value = state.startDate?.let { shortDate(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            InfoTile(
                label = "Вывод",
                value = state.hatchDate?.let { shortDate(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = if (highlighted) DesignPalette.InsightSurface else Color.White,
        border = BorderStroke(
            CardBorderWidth,
            if (highlighted) DesignPalette.HighlightBorder else DesignPalette.CardBorder,
        ),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = DesignType.TileValue,
                color = if (highlighted) DesignPalette.Accent
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// --- Замеры за сегодня ---------------------------------------------------------------------

@Composable
private fun MeasurementsCard(
    state: BatchDetailUiState,
    measurements: List<Measurement>,
    form: MeasurementForm,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (Measurement) -> Unit,
    onDelete: (Measurement) -> Unit,
    onOpenAnalytics: () -> Unit,
) {
    val latest = measurements.firstOrNull()
    val tempTarget = state.plannedToday?.temp
    val dampTarget = state.plannedToday?.damp
    // Свёрнуто — только последние замеры: за день их набирается сколько угодно, и список
    // иначе уводит вниз и форму записи, и «Завтра». Ключ по дню: наступил новый день —
    // история другая, и разворот от старой к ней не относится.
    var historyExpanded by remember(state.batchId, state.day) { mutableStateOf(false) }

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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Замеры за сегодня",
                    style = DesignType.CardHeading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AnalyticsChip(enabled = measurements.isNotEmpty(), onClick = onOpenAnalytics)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasureTile(
                    label = "Температура",
                    value = latest?.temp?.let { "${it.formatTemp()}°" },
                    target = tempTarget?.let { "цель ${it.formatTemp()}°" },
                    delta = tempDelta,
                    unit = "°",
                    decimals = 1,
                    minor = 0.2,
                    major = 0.5,
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
                )
            }

            if (tempDelta != null) {
                Spacer(Modifier.height(12.dp))
                DeviationBar(
                    caption = "ТЕМПЕРАТУРА",
                    delta = tempDelta,
                    scale = TEMP_SCALE,
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

            Spacer(Modifier.height(16.dp))
            MeasurementEntry(
                form = form,
                canRecord = state.canRecord,
                onFormChange = onFormChange,
                onSave = onSave,
                onCancelEdit = onCancelEdit,
            )

            if (measurements.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Пока нет замеров за сегодня. Записывайте показания в течение дня.",
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
                        MeasurementRow(
                            measurement = measurement,
                            tempTarget = tempTarget,
                            dampTarget = dampTarget,
                            onEdit = { onEdit(measurement) },
                            onDelete = { onDelete(measurement) },
                            selected = form.editingId == measurement.id,
                        )
                    }
                }
                if (measurements.size > VisibleMeasurements) {
                    Spacer(Modifier.height(12.dp))
                    HistoryToggle(
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
 * Кнопка под списком замеров: разворачивает историю дня целиком и сворачивает обратно.
 *
 * Показывает общее число замеров — иначе из свёрнутого списка не видно, сколько их там
 * ещё, и кнопку незачем нажимать.
 */
@Composable
private fun HistoryToggle(expanded: Boolean, total: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(InnerRadius),
        color = Color.White,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "Свернуть" else "Показать все ($total)",
                style = DesignType.CaptionEmphasis,
                color = DesignPalette.Accent,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(
                    id = if (expanded) R.drawable.ic_arrow_up_design
                    else R.drawable.ic_arrow_down_design
                ),
                contentDescription = null,
                tint = DesignPalette.Accent,
                modifier = Modifier.size(14.dp),
            )
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
private fun AnalyticsChip(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.White,
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
            )
        }
    }
}

@Composable
private fun MeasureTile(
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

/** Шкала отклонения: дорожка, засечка «цель» по центру и точка замера. */
@Composable
private fun DeviationBar(
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
private fun MeasurementEntry(
    form: MeasurementForm,
    canRecord: Boolean,
    onFormChange: (MeasurementForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = "T°C",
                    value = form.temp,
                    onValueChange = { onFormChange(form.copy(temp = it.filterMeasureInput())) },
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
                    value = form.airing,
                    onValueChange = { onFormChange(form.copy(airing = it.filterCountInput())) },
                    modifier = Modifier.weight(1f),
                )
                ToggleField(
                    label = "ПЕРЕВ.",
                    checked = form.over.isNotBlank(),
                    onCheckedChange = {
                        onFormChange(form.copy(over = if (it) TurnedMark else ""))
                    },
                    modifier = Modifier.weight(1f),
                )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.Accent,
                        contentColor = Color.White,
                        // Макет гасит неактивную кнопку прозрачностью, а не другим цветом.
                        disabledContainerColor = DesignPalette.Accent.copy(alpha = 0.4f),
                        disabledContentColor = Color.White,
                    ),
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
                                containerColor = Color.White,
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

/** Фильтрует ввод вызывающий: у замеров и счётчиков правила разные. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = DesignType.MonoMicro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        SheetTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "",
            numeric = true,
            // Поле шириной в четверть строки: с отступом по умолчанию «36.6» не влезает.
            horizontalPadding = 8.dp,
            minHeight = 40.dp,
            radius = InnerRadius,
        )
    }
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
        Text(
            text = label,
            style = DesignType.MonoMicro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(InnerRadius),
            color = if (checked) DesignPalette.Accent else Color.White,
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
                    tint = if (checked) Color.White else DesignPalette.CardBorder,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    tempTarget: Double?,
    dampTarget: Double?,
    // Без обработчиков строка становится просто карточкой замера — такой её показывает
    // вопрос об удалении, где править и удалять уже нечего.
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selected: Boolean = false,
) {
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
                        painter = painterResource(id = R.drawable.ic_close_design),
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
                                value = "${temp.formatTemp()}°",
                                delta = tempDelta,
                                decimals = 1,
                                unit = "°",
                                minor = 0.2,
                                major = 0.5,
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

            if (measurement.over.isNotBlank() || measurement.airing.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HalfSlot {
                        if (measurement.over.isNotBlank()) {
                            // Отметка говорит только «было»; число из старых замеров
                            // показываем как есть, чтобы не потерять его.
                            DetailText(
                                if (measurement.over == TurnedMark) "Переворачивали"
                                else "перев. ${measurement.over}"
                            )
                        }
                    }
                    HalfSlot {
                        if (measurement.airing.isNotBlank()) {
                            DetailText("провет. ${measurement.airing} мин")
                        }
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
 * Заметка в строке замера: одна строка, а остальные разворачиваются по нажатию.
 *
 * Длинные заметки иначе растягивают список и прятать под ними соседние замеры. Свёрнутый
 * текст обрывается многоточием — это и есть подсказка, что там есть продолжение; строку
 * ровно в одну строку нажимать не на что, поэтому она и не кликается.
 *
 * [key] — идентификатор замера: строки живут в обычной колонке и переиспользуются, а
 * развёрнутым должен остаться тот же замер, а не тот, кто занял его место.
 */
@Composable
private fun ExpandableNote(note: String, key: Long) {
    var expanded by remember(key) { mutableStateOf(false) }
    var truncated by remember(key) { mutableStateOf(false) }
    Text(
        text = note.capitalizeFirst(),
        style = DesignType.Caption,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else 1,
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

@Composable
private fun TomorrowCard(day: Int, plan: Value) {
    SheetCard(radius = CardRadius) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Завтра — День $day",
                style = DesignType.CardHeading,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePill("T°C", plan.temp.formatTempOrDash(), Modifier.weight(1f))
                ModePill("ВЛАЖН.", plan.damp?.let { "${it.formatDamp()}%" } ?: "—", Modifier.weight(1f))
                ModePill("ПЕРЕВ.", plan.over, Modifier.weight(1f))
                ModePill("ПРОВЕТ.", plan.airing, Modifier.weight(1f))
            }
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
            Text(
                text = label,
                style = DesignType.PillLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = compactMode(value),
                style = DesignType.PillValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * «2 раза по 5 минут» в плашку шириной в четверть экрана не влезает, а макет ждёт там
 * число. Берём ведущее число, короткие значения («Авто», «2-3») оставляем как есть.
 */
private fun compactMode(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return "—"
    if (text.length <= 5) return text
    return Regex("^\\d+([.,]\\d+)?(-\\d+)?").find(text)?.value ?: text
}

// --- Переход к расписанию ------------------------------------------------------------------

/**
 * Ссылка на [BatchScreen] — список дней с овоскопированием и завершением закладки.
 * В макете шторки её нет, но без неё туда стало бы не попасть.
 */
@Composable
private fun ScheduleLink(onClick: () -> Unit) {
    SheetCard(radius = CardRadius, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Расписание по дням",
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// --- Общие мелочи --------------------------------------------------------------------------

/** Белая карточка шторки: скругление из макета, рамка 0.8 dp цветом [DesignPalette.CardBorder]. */
@Composable
private fun SheetCard(
    radius: Dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = Color.White,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

// --- Отклонение от плана -------------------------------------------------------------------

/**
 * Насколько замер разошёлся с планом.
 *
 * Пороги — не из макета: он рисует единственный случай, «заметное отклонение».
 * Для температуры десятая доля градуса в инкубации не значит ничего, полградуса —
 * уже много; для влажности так же, но в процентах.
 */
internal enum class Severity { Normal, Minor, Major }

private fun severityOf(delta: Double, minor: Double, major: Double): Severity = when {
    abs(delta) <= minor -> Severity.Normal
    abs(delta) <= major -> Severity.Minor
    else -> Severity.Major
}

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

/** Отклонение факта от плана; нет одного из двух — нет и отклонения. */
private fun deltaOf(actual: Double?, target: Double?): Double? {
    if (actual == null || target == null) return null
    return actual - target
}

/** «−1.2°», «+3%» — со знаком и типографским минусом, как в макете. */
private fun signed(delta: Double, decimals: Int, unit: String): String {
    val body = String.format(Locale("ru"), "%+.${decimals}f", delta)
    return body.replace('-', '−').replace(',', '.') + unit
}
