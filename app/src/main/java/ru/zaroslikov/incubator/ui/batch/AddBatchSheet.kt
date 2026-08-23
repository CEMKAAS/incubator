package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.DatePickerDialogSample
import ru.zaroslikov.incubator.PastOrPresentSelectableDates
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.convertDateStringToMillis
import ru.zaroslikov.incubator.domain.incubation.incubationDays
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.components.FieldLabel
import ru.zaroslikov.incubator.ui.components.FieldHeight
import ru.zaroslikov.incubator.ui.components.FieldRadius
import ru.zaroslikov.incubator.ui.components.FormSpacer
import ru.zaroslikov.incubator.ui.components.SheetDragHandle
import ru.zaroslikov.incubator.ui.components.SheetHeader
import ru.zaroslikov.incubator.ui.components.SheetPadding
import ru.zaroslikov.incubator.ui.components.SheetPickerField
import ru.zaroslikov.incubator.ui.components.SheetTextField
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType

/** Виды в порядке макета; шестой плитки — цесарок — в :domain пока нет. */
private val SpeciesTiles = listOf("Курицы", "Утки", "Гуси", "Индюки", "Перепела")

private val TileRadius = 16.dp
private val TileHeight = 102.dp

/**
 * Форма закладки в нижней шторке — макет
 * [12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555).
 *
 * Шторка, а не экран: у макета скруглённый верх, «ручка» и крестик, значит она должна
 * появляться поверх того, что под ней. Маршрута в навигации у неё нет, идентификаторы
 * приходят параметрами — так же, как в
 * [AddIncubatorSheet][ru.zaroslikov.incubator.ui.incubator.AddIncubatorSheet].
 *
 * Создание и правка — одна форма: отдельного макета для правки в файле нет, а поля
 * совпадают ровно. Меняются заголовок, надпись на кнопке и наличие «завершить» /
 * «удалить» внизу.
 *
 * Отступления от макета, все намеренные:
 * — плитки «Цесарки» нет: режима инкубации для них в :domain не написано;
 * — переключателей «Авто охлаждение» / «Авто переворот» нет, как и в макете: закладка
 *   наследует автоматику от самого инкубатора, ведь это его возможность;
 * — звёздочки у «Названия» и «Количества» макет не рисует, но кнопка без них была бы
 *   недоступна без объяснения причины;
 * — «Завершить закладку» и «Удалить» макет не рисует вовсе, но деть их больше некуда:
 *   отдельного экрана правки, где они жили раньше, больше нет.
 *
 * @param incubatorId инкубатор, которому принадлежит закладка.
 * @param batchId ноль — создание, иначе правка существующей закладки.
 * @param onRemoved закладку завершили или удалили; экрана закладки больше нет смысла
 *        показывать, вызывающий уходит назад.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBatchSheet(
    incubatorId: Long,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    batchId: Long = 0,
    onRemoved: () -> Unit = {},
    viewModel: AddBatchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = viewModel.uiState
    val editing = viewModel.isEditing

    LaunchedEffect(incubatorId, batchId) { viewModel.load(incubatorId, batchId) }

    var openDatePicker by remember { mutableStateOf(false) }
    var openArchiveChoice by remember { mutableStateOf(false) }
    var openDeleteConfirm by remember { mutableStateOf(false) }

    if (openDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = PastOrPresentSelectableDates,
            initialSelectedDateMillis = convertDateStringToMillis(state.data),
        )
        DatePickerDialogSample(datePickerState, state.data) { date ->
            viewModel.update(state.copy(data = date))
            openDatePicker = false
        }
    }

    // Имена событий сохранены с прежних экранов, чтобы аналитика оставалась сравнимой.
    fun report(event: String = if (editing) "Incubator Edit" else "Инкубатор") {
        val eventParameters: MutableMap<String, Any> = HashMap()
        eventParameters["Имя"] = state.title
        eventParameters["Тип"] = state.type
        eventParameters["Порода"] = state.breed
        eventParameters["Кол-во"] = state.eggAll
        eventParameters["Стоимость"] = state.price
        eventParameters["Прим"] = state.note
        eventParameters["АвтоОхл"] = state.airing
        eventParameters["АвтоПрев"] = state.over
        viewModel.reminderList.forEachIndexed { index, time ->
            eventParameters[index.toString()] = time.time
        }
        AppMetrica.reportEvent(event, eventParameters)
    }

    if (openDeleteConfirm) {
        ConfirmDeleteDialog(
            title = state.title,
            onDismiss = { openDeleteConfirm = false },
            onConfirm = {
                openDeleteConfirm = false
                viewModel.delete(onRemoved)
            },
        )
    }

    if (openArchiveChoice) {
        ArchiveScheduleDialog(
            batches = viewModel.archivedBatches,
            onUseDefault = {
                openArchiveChoice = false
                report()
                viewModel.save(onSaved = onSaved)
            },
            onUseArchive = { sourceId ->
                openArchiveChoice = false
                report()
                viewModel.save(archiveSourceId = sourceId, onSaved = onSaved)
            },
        )
    }

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
            SheetHeader(
                title = if (editing) "Закладка" else "Новая закладка",
                onClose = onDismiss,
            )

            FormSpacer(20.dp)
            FieldLabel(text = "Название закладки", required = true)
            SheetTextField(
                value = state.title,
                onValueChange = { viewModel.update(state.copy(title = it)) },
                placeholder = "Например, Весенняя партия",
            )

            FormSpacer(20.dp)
            FieldLabel(text = "Порода / вид птицы")
            SpeciesGrid(
                selected = state.type,
                onSelect = { viewModel.update(state.copy(type = it)) },
            )
            FormSpacer(8.dp)
            SheetTextField(
                value = state.breed,
                onValueChange = { viewModel.update(state.copy(breed = it)) },
                placeholder = "Порода (Ломан Браун, Пекинская…)",
            )

            FormSpacer(20.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Количество яиц", required = true)
                    SheetTextField(
                        value = state.eggAll,
                        onValueChange = {
                            viewModel.update(state.copy(eggAll = it.filter(Char::isDigit)))
                        },
                        placeholder = "24",
                        numeric = true,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Дата закладки")
                    SheetPickerField(
                        value = state.data,
                        placeholder = "—",
                        onClick = { openDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar_design),
                                contentDescription = "Выбрать дату",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            FormSpacer(20.dp)
            FieldLabel(text = "Стоимость, ₽")
            PriceRow(
                price = state.price,
                perEgg = state.pricePerEgg,
                onPriceChange = {
                    viewModel.update(state.copy(price = it.filter(Char::isDigit)))
                },
                onModeChange = { viewModel.update(state.copy(pricePerEgg = it)) },
            )
            FormSpacer(8.dp)
            PriceSummary(
                price = state.price.toIntOrNull() ?: 0,
                perEgg = state.pricePerEgg,
                eggCount = state.eggAll.toIntOrNull() ?: 0,
            )

            FormSpacer(20.dp)
            FieldLabel(text = "Напоминания")
            viewModel.reminderList.forEachIndexed { index, time ->
                if (index > 0) FormSpacer(8.dp)
                ReminderCard(
                    time = time.time,
                    note = time.note,
                    onTimeChange = { viewModel.updateReminder(index, time = it) },
                    onNoteChange = { viewModel.updateReminder(index, note = it) },
                    onRemove = { viewModel.removeReminder(index) },
                )
            }
            if (viewModel.reminderList.isNotEmpty()) FormSpacer(8.dp)
            DashedAddButton(
                text = "Добавить напоминание",
                onClick = viewModel::addReminder,
            )

            FormSpacer(20.dp)
            FieldLabel(text = "Заметка")
            SheetTextField(
                value = state.note,
                // Как и заметка к замеру: фраза, а не значение, и первая буква заглавная
                // даже у текста, вставленного из буфера.
                onValueChange = { viewModel.update(state.copy(note = it.capitalizeFirst())) },
                placeholder = "Особенности партии…",
                minHeight = 70.dp,
                singleLine = false,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            )

            FormSpacer(20.dp)
            Button(
                onClick = {
                    // Диалог всплывает только когда есть из чего выбирать: завершённые
                    // закладки того же вида хранят уже выверенный режим по дням.
                    if (viewModel.archivedBatches.isNotEmpty()) {
                        openArchiveChoice = true
                    } else {
                        report()
                        viewModel.save(onSaved = onSaved)
                    }
                },
                enabled = viewModel.isValid,
                shape = RoundedCornerShape(FieldRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = DesignPalette.Accent.copy(alpha = 0.4f),
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (editing) "Сохранить" else "Заложить яйца",
                    style = DesignType.ButtonLabel,
                )
            }

            if (editing) {
                // Завершать уже завершённую нечего — у неё и так стоит чип «Завершено».
                if (state.arhive == "0") {
                    FormSpacer(12.dp)
                    OutlinedButton(
                        onClick = {
                            report("Incubator Archive")
                            viewModel.archive(onRemoved)
                        },
                        shape = RoundedCornerShape(FieldRadius),
                        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(text = "Завершить закладку", style = DesignType.ButtonLabel)
                    }
                }

                FormSpacer(4.dp)
                TextButton(
                    onClick = { openDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "Удалить закладку",
                        style = DesignType.ToggleLabel,
                        color = DesignPalette.Expense,
                    )
                }
            }
        }
    }
}

/**
 * Удаление уносит с собой расписание по дням, замеры и напоминания — по внешним
 * ключам с `ON DELETE CASCADE`. Отменить это нечем, поэтому спрашиваем.
 */
@Composable
private fun ConfirmDeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Удалить закладку?", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = "«$title» исчезнет вместе с режимом по дням и напоминаниями. " +
                    "Вернуть её будет нельзя.",
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

// --- Вид птицы -------------------------------------------------------------------------------

/** Сетка 3 в ряд из макета (узел 12:4782); пятая плитка оставляет пустое место справа. */
@Composable
private fun SpeciesGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeciesTiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { species ->
                    SpeciesTile(
                        species = species,
                        selected = species == selected,
                        onClick = { onSelect(species) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SpeciesTile(
    species: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = if (selected) DesignPalette.SpeciesTileSelected else Color.White,
        border = BorderStroke(
            0.8.dp,
            if (selected) DesignPalette.Accent else DesignPalette.CardBorder,
        ),
        modifier = modifier.height(TileHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(text = speciesEmoji(species), fontSize = 24.sp, lineHeight = 36.sp)
            Text(
                text = species,
                style = DesignType.CaptionEmphasis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = incubationDays(species)?.let { "$it дн." }.orEmpty(),
                style = DesignType.MonoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Стоимость -------------------------------------------------------------------------------

@Composable
private fun PriceRow(
    price: String,
    perEgg: Boolean,
    onPriceChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SheetTextField(
                value = price,
                onValueChange = onPriceChange,
                placeholder = "—",
                numeric = true,
            )
        }
        // Та же высота, что у поля слева: в макете обе стоят на 48.
        Surface(
            shape = RoundedCornerShape(FieldRadius),
            color = Color.White,
            border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
            modifier = Modifier.height(FieldHeight),
        ) {
            Row(Modifier.padding(4.dp)) {
                PriceModeButton("за яйцо", perEgg) { onModeChange(true) }
                PriceModeButton("за всё", !perEgg) { onModeChange(false) }
            }
        }
    }
}

@Composable
private fun PriceModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DesignPalette.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = DesignType.CaptionEmphasis,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Две плитки-итога под полем: цена за яйцо и за всю партию.
 *
 * Пересчёт «за всё → за яйцо» делится нацело: копеек в базе нет, и показывать
 * дробную цену яйца там, где всё остальное в целых рублях, было бы враньём о точности.
 */
@Composable
private fun PriceSummary(price: Int, perEgg: Boolean, eggCount: Int) {
    val perEggValue = if (perEgg) price else if (eggCount > 0) price / eggCount else 0
    val totalValue = if (perEgg) price * eggCount else price

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PriceSummaryTile(
            label = "За одно яйцо: ",
            value = "$perEggValue ₽",
            modifier = Modifier.weight(1f),
        )
        PriceSummaryTile(
            label = if (eggCount > 0) "За все $eggCount: " else "За всю партию: ",
            value = "$totalValue ₽",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PriceSummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DesignPalette.PriceSummarySurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(label)
                withStyle(
                    SpanStyle(
                        fontFamily = DesignType.MonoEmphasis.fontFamily,
                        fontWeight = DesignType.MonoEmphasis.fontWeight,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                ) { append(value) }
            },
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Напоминания -----------------------------------------------------------------------------

@Composable
private fun ReminderCard(
    time: String,
    note: String,
    onTimeChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    if (showTimePicker) {
        TimePicker(time = time) {
            onTimeChange(it)
            showTimePicker = false
        }
    }

    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = Color.White,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetPickerField(
                    value = time,
                    placeholder = "08:00",
                    onClick = { showTimePicker = true },
                    modifier = Modifier.width(112.dp),
                    height = 39.dp,
                    radius = 12.dp,
                    textStyle = DesignType.MonoField,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DesignPalette.SheetIconButton),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_design),
                        contentDescription = "Удалить напоминание",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            FormSpacer(8.dp)
            SheetTextField(
                value = note,
                // Этот текст уходит в уведомление, поэтому заглавная буква ему нужна
                // не меньше, чем заметкам: он читается с экрана блокировки как фраза.
                onValueChange = { onNoteChange(it.capitalizeFirst()) },
                placeholder = "Текст напоминания (перевернуть яйца…)",
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            )
        }
    }
}

/**
 * Пунктирная кнопка из макета (узел 12:4866).
 *
 * Своя, а не `DashedButton` с экрана инкубатора: там кнопка на 58 dp со скруглением
 * 22 и текстом 16, здесь — 41 dp, 16 и 13, разница видна.
 */
@Composable
private fun DashedAddButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(41.dp)
            .clip(RoundedCornerShape(FieldRadius))
            .background(DesignPalette.DashedSurface)
            .drawBehind {
                drawRoundRect(
                    color = DesignPalette.DashedBorder,
                    cornerRadius = CornerRadius(FieldRadius.toPx()),
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx()), 0f
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus_design),
            contentDescription = null,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text = text, style = DesignType.TabLabel, color = DesignPalette.Accent)
    }
}

// --- Режим из архива -------------------------------------------------------------------------

/**
 * Предложение взять режим по дням из завершённой закладки того же вида.
 *
 * Макета у диалога нет — он всплывает только когда архив непустой, и сохраняет
 * возможность, которая была в прежней двухшаговой форме.
 */
@Composable
private fun ArchiveScheduleDialog(
    batches: List<Batch>,
    onUseDefault: () -> Unit,
    onUseArchive: (Long) -> Unit,
) {
    if (batches.isEmpty()) return
    var selectedId by remember(batches) { mutableLongStateOf(batches.first().id) }

    AlertDialog(
        onDismissRequest = onUseDefault,
        title = { Text(text = "Взять режим из архива?", style = DesignType.SectionTitle) },
        text = {
            Column {
                Text(
                    text = "Введённые данные останутся как есть. Температура, влажность, " +
                        "поворот и проветривание по дням будут скопированы из выбранной закладки.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormSpacer(12.dp)
                batches.forEach { batch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedId = batch.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = batch.id == selectedId,
                            onClick = { selectedId = batch.id },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = DesignPalette.Accent,
                            ),
                        )
                        Text(
                            text = batch.title,
                            style = DesignType.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUseArchive(selectedId) }) {
                Text(text = "Из архива", color = DesignPalette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onUseDefault) {
                Text(
                    text = "По умолчанию",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}
