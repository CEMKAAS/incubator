package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.BorderStroke
import ru.zaroslikov.incubator.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.airing.AiringTimerTarget
import ru.zaroslikov.incubator.ui.batch.BatchAnalyticsSheet
import ru.zaroslikov.incubator.ui.batch.ConfirmDeleteMeasurementDialog
import ru.zaroslikov.incubator.ui.batch.MeasurementsCard
import ru.zaroslikov.incubator.ui.batch.RegisterTimerForm
import ru.zaroslikov.incubator.ui.start.SpeciesGlyph

private val CardRadius = 22.dp

/** Диаметр плавающей кнопки сканера — тот же, что у мини-«+» над списком закладок. */
private val FabDiameter = 48.dp

/** Запас снизу под неё: диаметр, отступ от края и воздух над ней. */
private val FabReserve = 96.dp

/** Порог в пикселях, ниже которого движение пальца не считается листанием. */
private const val FabScrollThreshold = 3f

/** Появление и исчезновение кнопки: коротко, чтобы она не тянулась за пальцем. */
private const val FabFadeMillis = 160


/**
 * Шторка «Замеры за сегодня» по инкубатору — кнопка «Внести замер» под списком закладок.
 *
 * Макета нет: это карточка из шторки закладки (узел 14:4893), поднятая на уровень прибора, — та же
 * `MeasurementsCard` из `BatchDetailSheet.kt` с общим планом, флагами автоматики и журналом группы.
 * Над ней — карточка «куда попадёт запись», потому что запись идёт в каждую закладку.
 *
 * Цель у плиток — [averagePlan]; «Аналитика» открывает ту же `BatchAnalyticsSheet` на журнале
 * группы. Черновик — на экране инкубатора, как у остальных шторок (см. `SheetDraft`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncubatorMeasurementSheet(
    incubatorId: Long,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    /** Плавающая кнопка сканера: перейти к другому инкубатору по его наклейке. */
    onScan: () -> Unit = {},
    viewModel: IncubatorMeasurementViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent

    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    // `rememberSaveable`: график аналитики раскрывают на весь экран и в ландшафте, а поворот
    // пересоздаёт активность — `remember` закрыл бы аналитику на полпути.
    var analyticsOpen by rememberSaveable(incubatorId) { mutableStateOf(false) }

    LaunchedEffect(incubatorId) {
        send(IncubatorMeasurementIntent.Load(incubatorId, resetForm = draft.claim(incubatorId)))
    }

    val close = {
        draft.discard()
        onDismiss()
    }

    // Пока шторка открыта, плавающая кнопка таймера этого инкубатора не нужна: его
    // карточка стоит здесь же. См. [AiringTimerFab].
    RegisterTimerForm(AiringTimerTarget(incubatorId))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Бокс ради плавающей кнопки сканера: колонка прокручивается внутри него, кнопка
        // парит над ней в правом нижнем углу. Куда она девается, чтобы не мешать, — см.
        // [ScanFab]; нижний отступ колонки — запас под неё, чтобы «Записать замер» и
        // последняя строка журнала докручивались выше кнопки, а не оставались под ней.
        var scrollingDown by remember { mutableStateOf(false) }
        val fabScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    when {
                        available.y < -FabScrollThreshold -> scrollingDown = true
                        available.y > FabScrollThreshold -> scrollingDown = false
                    }
                    return Offset.Zero
                }
            }
        }
        Box {
            Column(
                modifier = Modifier
                    .clearFocusOnTap()
                    .nestedScroll(fabScroll)
                    .padding(horizontal = SheetPadding)
                    .verticalScroll(rememberScrollState())
                    // После `verticalScroll`, а не до: запас должен быть частью прокручиваемого
                    // содержимого — хвостом, до которого докручивают, — а не полосой, которая
                    // укорачивает окно прокрутки и оставляет под ним пустоту.
                    .padding(bottom = FabReserve)
            ) {
                SheetHeader(title = "Замеры за сегодня", onClose = close)
    
                if (!state.loaded) {
                    FormSpacer(20.dp)
                    LoadingBox()
                    return@Column
                }
    
                FormSpacer(16.dp)
                TargetsCard(
                    targets = state.targets,
                    deselected = state.deselected,
                    onToggle = { send(IncubatorMeasurementIntent.ToggleTarget(it)) },
                    onSelectAll = { send(IncubatorMeasurementIntent.SelectAll) },
                    onSelectNone = { send(IncubatorMeasurementIntent.SelectNone) },
                )
    
                FormSpacer(16.dp)
                // Та же карточка, что «Замеры за сегодня» у закладки: план — средний по
                // выбранным ([averagePlan]), автоматика — когда на ней все цели, журнал — по группе.
                MeasurementsCard(
                    plan = state.plan,
                    historyKey = incubatorId,
                    canRecord = state.canRecord,
                    autoTurn = state.autoTurn,
                    autoAiring = state.autoAiring,
                    measurements = state.measurements,
                    form = state.form,
                    onFormChange = { send(IncubatorMeasurementIntent.UpdateForm(it)) },
                    onSave = { send(IncubatorMeasurementIntent.Save) },
                    onCancelEdit = { send(IncubatorMeasurementIntent.CancelEdit) },
                    onEdit = { send(IncubatorMeasurementIntent.StartEdit(it)) },
                    onDelete = { pendingDelete = it },
                    onOpenAnalytics = { analyticsOpen = true },
                    emptyText = "Пока нет замеров по инкубатору за сегодня. Записанные внутри " +
                        "закладок остаются в них.",
                    readOnly = state.archived,
                    timer = state.timerSlot,
                    onTimerAction = { send(IncubatorMeasurementIntent.AiringTimer(it)) },
                    dayStart = state.dayStart,
                )
            }
            ScanFab(
                visible = !scrollingDown && !WindowInsets.isImeVisible,
                onClick = onScan,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = SheetPadding, bottom = 20.dp),
            )
        }
    }

    // Соседом шторки, а не её содержимым, — как у закладки: аналитика и вопрос об
    // удалении ложатся своим окном поверх, и их закрытие возвращает ровно эту шторку.
    if (analyticsOpen) {
        // Планы закладок — линиями на графике: показание здесь одно на весь прибор, и
        // видно, к чьей норме оно ближе. Из тех же целей, что и общая цель `state.plan`.
        val planLines = remember(state.recipients) { planLinesOf(state.recipients) }
        BatchAnalyticsSheet(
            measurements = state.measurements,
            plan = state.plan,
            planLines = planLines,
            onDismiss = { analyticsOpen = false },
        )
    }

    pendingDelete?.let { measurement ->
        ConfirmDeleteMeasurementDialog(
            measurement = measurement,
            tempTarget = state.plan?.temp,
            dampTarget = state.plan?.damp,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                send(IncubatorMeasurementIntent.Delete(measurement))
                pendingDelete = null
            },
        )
    }
}

/**
 * Куда ляжет запись: идущие закладки инкубатора и их сегодняшний день. Свёрнута по умолчанию
 * («Справка», `rememberSaveable`) — иначе отодвигала бы плитки и форму ниже сгиба.
 *
 * Развёрнутая, ещё и выбирает, куда писать: галочка у каждой закладки (по умолчанию все), «Выбрать
 * все» / «Снять все». Строка показывает то же, что карточка в списке — вид, породы, яйца, «День N/M».
 *
 * Закладка без строки дня (дата не разобралась или день вышел за расписание) остаётся в списке с
 * погашенной галочкой и пометкой, а не пропадает — иначе человек не поймёт, почему запись есть
 * не везде.
 */
@Composable
private fun TargetsCard(
    targets: List<MeasurementTarget>,
    deselected: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "targets-card-chevron",
    )
    // Нажатие — собственное `onClick` карточки, как у карточек закладок и инкубаторов,
    // а не `Modifier.clickable` на `Surface`: рябь тогда повторяет скругление карточки,
    // а не прямоугольник вокруг, и жест разбирает сама карточка, без спора с
    // `clearFocusOnTap()` колонки шторки. Тот же цвет, рамка и радиус, что у `SheetCard`.
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "Запись попадёт в каждую идущую закладку" else "Справка",
                    style = DesignType.CardHeading.copy(hyphens = Hyphens.Auto),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_down_design),
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevron),
                )
            }
            if (!expanded) return@Column

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Термометр один на весь инкубатор — показание относится ко всем, " +
                    "кто в нём лежит. В каждой закладке замер появится в её «Замерах за " +
                    "сегодня», и править его можно и оттуда, и отсюда.",
                style = DesignType.Note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (targets.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Идущих закладок нет — записывать некуда.",
                    style = DesignType.Placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            // Между закладками — волосяная линия цветом рамки карточки: строки с двумя
            // подписями сливались бы в один столбец, а галочки справа — в одну колонку
            // без привязки к строке.
            targets.forEachIndexed { index, target ->
                if (index > 0) {
                    HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                }
                TargetRow(
                    target = target,
                    selected = target.canReceive && target.batch.id !in deselected,
                    onToggle = { onToggle(target.batch.id) },
                )
            }
            // Две кнопки, а не одна-переключатель «все / ничего»: у переключателя два
            // состояния, а у списка с галочками три — все, ничего и часть, — и из третьего
            // одна кнопка не знает, куда вести. Погашены, когда нажимать незачем.
            if (targets.any { it.canReceive }) {
                Spacer(Modifier.height(4.dp))
                // Ровно по половине ширины каждая (`weight(1f)`), а не по ширине подписи:
                // две одинаковые кнопки на равных половинах читаются как пара, а
                // прижатые вправо — как хвост списка.
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onSelectAll,
                        enabled = targets.any { it.canReceive && it.batch.id in deselected },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Выбрать все", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
                    }
                    TextButton(
                        onClick = onSelectNone,
                        enabled = targets.any { it.canReceive && it.batch.id !in deselected },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Снять все", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetRow(
    target: MeasurementTarget,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val batch = target.batch
    // Строка — переключатель целиком (`toggleable`, роль чекбокса), а не галочка с
    // подписью рядом: попасть пальцем в квадратик 20 dp труднее, чем в строку, а
    // читалка экрана тогда объявляет закладку и её состояние одной фразой. Дочерний
    // жест забирает тап у карточки, так что нажатие по строке не сворачивает «Справку».
    // Цель без строки дня переключать нечего: галочка снята и погашена, копия ей не
    // ляжет, что бы ни выбрали.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = selected,
                enabled = target.canReceive,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesGlyph(bird = batch.type, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            TruncatedText(
                text = batch.title.ifBlank { batch.type },
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            // Та же строка, что под названием на карточке закладки: вид и породы.
            val subtitle = listOfNotNull(
                batch.type.takeIf { batch.title.isNotBlank() },
                batch.breed.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                TruncatedText(
                    text = subtitle,
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val dayLabel = target.totalDays?.let { "День ${target.day}/$it" } ?: "День ${target.day}"
            Text(
                text = if (target.canReceive) {
                    "${plural(batch.eggAll, "яйцо", "яйца", "яиц")} · $dayLabel"
                } else {
                    "${plural(batch.eggAll, "яйцо", "яйца", "яиц")} · $dayLabel · " +
                        "в расписании нет такого дня, замер сюда не ляжет"
                },
                style = DesignType.Caption,
                color = if (target.canReceive) MaterialTheme.colorScheme.onSurfaceVariant
                else DesignPalette.Expense,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Галочка справа, как в списках настроек: слева читают, справа отвечают, и
        // столбик галочек у правого края не ломает выравнивание названий под смайликом.
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = target.canReceive,
            colors = CheckboxDefaults.colors(checkedColor = DesignPalette.Accent),
        )
    }
}

/**
 * Плавающая кнопка сканера над содержимым шторки — «подойти к другому инкубатору» при обходе
 * птичника. Найденный инкубатор открывается со своей шторкой замеров; эта уходит из стека.
 *
 * Парит, а не стоит в полосе, чтобы не отнимать высоту. Правила: **маленькая** (48 dp, как мини-«+»
 * над списком закладок), **уходит при листании вниз** и возвращается на первом движении вверх
 * (`NestedScrollConnection`, порог как у ряда фильтров экрана инкубатора), **прячется вместе с
 * клавиатурой** — иначе висела бы над полем ввода.
 */
@Composable
private fun ScanFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(FabFadeMillis)) + scaleIn(tween(FabFadeMillis), initialScale = 0.7f),
        exit = fadeOut(tween(FabFadeMillis)) + scaleOut(tween(FabFadeMillis), targetScale = 0.7f),
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = DesignPalette.Accent,
            contentColor = DesignPalette.OnAccent,
            modifier = Modifier.size(FabDiameter),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_qr_scanner_design),
                contentDescription = "Сканировать QR-код инкубатора",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
