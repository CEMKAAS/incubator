package ru.zaroslikov.incubator.ui.incubator

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.incubation.incubationDays
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.batch.AddBatchSheet
import ru.zaroslikov.incubator.ui.batch.BatchDetailSheet
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.ui.start.modelLine
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


object IncubatorDestination : NavigationDestination {
    override val route = "Incubator"
    override val titleRes = R.string.app_name
    const val itemIdArg = "incubatorId"
    val routeWithArgs = "$route/{$itemIdArg}"
}

internal val ScreenPadding = 20.dp

/** Насколько кремовая «шторка» с вкладками наезжает на зелёную шапку — из макета (200 − 183). */
private val SheetOverlap = 17.dp

/** Скругление верхних углов шторки, из-за которого по краям видно зелёный фон шапки. */
private val SheetCorner = 24.dp

private enum class IncubatorTab(val title: String, @param:DrawableRes val icon: Int) {
    Batches("Закладки", R.drawable.ic_egg_design_16),
    Stats("Статистика", R.drawable.ic_chart_design),
    Finance("Финансы", R.drawable.ic_wallet_design),
}

/**
 * Один инкубатор — экран `IncubatorDetail` из макета
 * ([узел 5:547](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=5-547)).
 *
 * Тап по активной закладке открывает шторку [BatchDetailSheet] (макет 14:4893), а не
 * отдельный экран; к списку дней ведёт ссылка «Расписание по дням» внутри неё.
 *
 * Зелёная шапка с показателями, поверх неё кремовая шторка с тремя вкладками —
 * «Закладки», «Статистика», «Финансы». Вкладки листаются свайпом.
 *
 * Два отступления от макета, оба намеренные:
 * третий показатель в шапке — «Выведено», а не «Прибыль»: денег в базе пока нет,
 * и вечный ноль в шапке выглядел бы поломкой; шестерёнка справа от «Все инкубаторы»
 * в макете отсутствует, но без неё некуда деться к редактированию инкубатора.
 */
@Composable
fun IncubatorScreen(
    navigateBack: () -> Unit,
    navigateToBatch: (Long) -> Unit,
    navigateToArchivedBatch: (Long) -> Unit,
    viewModel: IncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditSheet by remember { mutableStateOf(false) }
    var showAddBatchSheet by remember { mutableStateOf(false) }
    // Ноль — шторка закрыта; иначе закладка, которую она показывает (макет 14:4893).
    var detailBatchId by remember { mutableLongStateOf(0L) }
    val pagerState = rememberPagerState(pageCount = { IncubatorTab.entries.size })
    val scope = rememberCoroutineScope()

    if (showEditSheet) {
        AddIncubatorSheet(
            incubatorId = viewModel.incubatorId,
            onDismiss = { showEditSheet = false },
            onSaved = { showEditSheet = false },
        )
    }

    // Форма закладки — тоже шторка (макет 12:3555), поэтому её хозяин экран, а не навигация.
    if (showAddBatchSheet) {
        AddBatchSheet(
            incubatorId = viewModel.incubatorId,
            onDismiss = { showAddBatchSheet = false },
            onSaved = { batchId ->
                showAddBatchSheet = false
                AppMetrica.reportEvent("Переход в Добавление")
                detailBatchId = batchId
            },
        )
    }

    // Шторка закладки — макет 14:4893. Маршрута у неё нет, как и у форм выше.
    if (detailBatchId != 0L) {
        BatchDetailSheet(
            batchId = detailBatchId,
            onDismiss = { detailBatchId = 0L },
            navigateToSchedule = { id ->
                detailBatchId = 0L
                navigateToBatch(id)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
    ) {
        IncubatorHeader(
            uiState = uiState,
            navigateBack = navigateBack,
            onEdit = { showEditSheet = true },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = -SheetOverlap)
                .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabSwitcher(
                selected = IncubatorTab.entries[pagerState.currentPage],
                onSelect = { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
            )
            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp,
            ) { page ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = ScreenPadding)
                        .padding(top = 20.dp, bottom = 32.dp)
                ) {
                    when (IncubatorTab.entries[page]) {
                        IncubatorTab.Batches -> BatchesTab(
                            uiState = uiState,
                            onBatchClick = { batch ->
                                if (batch.arhive == "0") {
                                    AppMetrica.reportEvent("Переход в закладку")
                                    detailBatchId = batch.id
                                } else navigateToArchivedBatch(batch.id)
                            },
                            onAddBatch = { showAddBatchSheet = true },
                        )

                        IncubatorTab.Stats -> StatsTab(uiState)
                        IncubatorTab.Finance -> FinanceTab()
                    }
                }
            }
        }
    }
}

// --- Шапка ---------------------------------------------------------------------------------

@Composable
private fun IncubatorHeader(
    uiState: IncubatorUiState,
    navigateBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val incubator = uiState.incubator
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignPalette.HeaderSurface)
            .padding(start = ScreenPadding, end = 8.dp, top = 8.dp, bottom = 32.dp)
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
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_create_24),
                    contentDescription = "Настройки инкубатора",
                    tint = DesignPalette.HeaderIcon,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = if (incubator == null) "" else headerEyebrow(incubator.brand, incubator.model, incubator.capacity),
            style = DesignType.HeaderEyebrow,
            color = DesignPalette.HeaderMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = incubator?.name.orEmpty(),
            style = DesignType.HeaderTitle,
            color = DesignPalette.HeaderTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HeaderStat(uiState.activeCount.toString(), "Активных закладок")
            HeaderStat(uiState.eggsInWork.toString(), "Яиц в работе")
            HeaderStat(uiState.hatched.toString(), "Выведено")
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = DesignType.HeaderStatValue,
            color = DesignPalette.HeaderTitle,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = DesignType.Micro,
            color = DesignPalette.HeaderMuted,
        )
    }
}

/** «BLITZ 72 TURBO · 72 МЕСТ» — надзаголовок в шапке; вместимость 0 значит «не указана». */
private fun headerEyebrow(brand: String, model: String, capacity: Int): String {
    val line = modelLine(brand, model)
    return (if (capacity > 0) "$line · $capacity мест" else line).uppercase(Locale("ru"))
}

// --- Переключатель вкладок -----------------------------------------------------------------

@Composable
private fun TabSwitcher(selected: IncubatorTab, onSelect: (IncubatorTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 12.dp, bottom = 12.dp)
            .clip(CircleShape)
            .background(DesignPalette.TabTrack)
            .padding(4.dp)
            .height(39.5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IncubatorTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (isSelected) Modifier.shadow(2.dp, CircleShape) else Modifier
                    )
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint =
                    if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    painter = painterResource(id = tab.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = tab.title,
                    style = DesignType.TabLabel,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- Вкладка «Закладки» --------------------------------------------------------------------

@Composable
private fun BatchesTab(
    uiState: IncubatorUiState,
    onBatchClick: (Batch) -> Unit,
    onAddBatch: () -> Unit,
) {
    if (uiState.batches.isEmpty()) {
        Text(
            text = "В этом инкубаторе пока нет закладок.",
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
    uiState.batches.forEach { batch ->
        BatchCard(
            batch = batch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable { onBatchClick(batch) },
        )
    }
    DashedButton(
        text = "Добавить закладку",
        onClick = onAddBatch,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BatchCard(batch: Batch, modifier: Modifier = Modifier) {
    val progress = remember(batch) { batchProgress(batch) }
    val isFinished = batch.arhive != "0"

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                fraction = progress.fraction,
                emoji = speciesEmoji(batch.type),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = batch.title.ifBlank { batch.type },
                        style = DesignType.BatchTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(8.dp))
                    StatusChip(isFinished)
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconLabel(
                        icon = R.drawable.ic_egg_design,
                        text = "${batch.eggAll} яиц",
                    )
                    Text(
                        text = progress.dayLabel,
                        style = DesignType.Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconLabel(
                        icon = R.drawable.ic_calendar_design,
                        text = progress.hatchLabel,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (progress.trailing != null) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = progress.trailing,
                            style = DesignType.MonoAccent,
                            color = if (isFinished) DesignPalette.Accent else DesignPalette.DateEmphasis,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(isFinished: Boolean) {
    Text(
        text = if (isFinished) "Завершено" else "Инкубация",
        style = DesignType.ChipLabel,
        color = if (isFinished) DesignPalette.StatusDoneText else DesignPalette.StatusActiveText,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isFinished) DesignPalette.StatusDoneSurface else DesignPalette.StatusActiveSurface
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
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

/** Кольцо прогресса инкубации 64 dp с эмодзи вида птицы по центру (узел 5:1023). */
@Composable
private fun ProgressRing(fraction: Float, emoji: String) {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = DesignPalette.ProgressTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = DesignPalette.Accent,
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
        Text(text = emoji, fontSize = 22.sp)
    }
}

/** Пунктирная кнопка «Добавить …» (узлы 5:1110 и 11:3535). */
@Composable
internal fun DashedButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val enabled = onClick != null
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DesignPalette.DashedSurface)
            .drawBehind {
                drawRoundRect(
                    color = DesignPalette.DashedBorder,
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx()), 0f
                        ),
                    ),
                )
            }
            .then(if (enabled) Modifier.clickable { onClick.invoke() } else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus_design),
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

// --- Расчёт дат и дней ---------------------------------------------------------------------

/** Подписи карточки закладки, посчитанные один раз на каждую перерисовку списка. */
private data class BatchProgress(
    val fraction: Float,
    val dayLabel: String,
    val hatchLabel: String,
    val trailing: String?,
)

internal const val DATE_PATTERN = "dd.MM.yyyy"

private fun batchProgress(batch: Batch): BatchProgress {
    val total = incubationDays(batch.type)
    val finished = batch.arhive != "0"
    val start = parseDate(batch.data)

    if (finished) {
        val hatched = parseDate(batch.dateEnd)
        return BatchProgress(
            fraction = 1f,
            dayLabel = if (total != null) "День $total/$total" else "",
            hatchLabel = if (hatched != null) "Выведено ${shortDate(hatched)}" else "Завершено",
            trailing = if (batch.eggAllEND > 0) "+${batch.eggAllEND} ${chicks(batch.eggAllEND)}" else null,
        )
    }

    if (start == null) return BatchProgress(0f, "", "", null)

    val elapsed = daysBetween(start, today())
    val day = (elapsed + 1).coerceAtLeast(1)
    if (total == null) {
        return BatchProgress(0f, "День $day", "", null)
    }

    val hatchDate = start.plusDays(total)
    val left = total - elapsed
    return BatchProgress(
        fraction = day.coerceAtMost(total) / total.toFloat(),
        dayLabel = "День ${day.coerceAtMost(total)}/$total",
        hatchLabel = "Вывод ${shortDate(hatchDate)}",
        trailing = when {
            left > 0 -> "через $left дн."
            left == 0 -> "сегодня"
            else -> "срок вышел"
        },
    )
}

internal fun parseDate(text: String): Date? = try {
    if (text.isBlank()) null else SimpleDateFormat(DATE_PATTERN, Locale("ru")).parse(text)
} catch (e: Exception) {
    null
}

internal fun today(): Date {
    val format = SimpleDateFormat(DATE_PATTERN, Locale("ru"))
    return format.parse(format.format(Date())) ?: Date()
}

internal fun daysBetween(from: Date, to: Date): Int =
    TimeUnit.DAYS.convert(to.time - from.time, TimeUnit.MILLISECONDS).toInt()

internal fun Date.plusDays(days: Int): Date = Calendar.getInstance().let {
    it.time = this
    it.add(Calendar.DAY_OF_YEAR, days)
    it.time
}

/** «10 авг.» — как в макете. */
internal fun shortDate(date: Date): String =
    SimpleDateFormat("d MMM", Locale("ru")).format(date)

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
