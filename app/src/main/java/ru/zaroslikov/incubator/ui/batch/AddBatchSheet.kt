package ru.zaroslikov.incubator.ui.batch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.DatePickerDialogSample
import ru.zaroslikov.incubator.PastOrPresentSelectableDates
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.transfer.ScheduleExport
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.dateToPickerMillis
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FieldLabelWithHint
import ru.zaroslikov.incubator.design.components.FieldHeight
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.ui.components.CellGap
import ru.zaroslikov.incubator.ui.components.DashedAddButton
import ru.zaroslikov.incubator.ui.components.SchedulePadding
import ru.zaroslikov.incubator.ui.components.ScheduleActionButton
import ru.zaroslikov.incubator.ui.components.ScheduleHeaderRow
import ru.zaroslikov.incubator.ui.components.ScheduleRow
import ru.zaroslikov.incubator.design.components.SheetSaveFooter
import ru.zaroslikov.incubator.design.components.PriceRow
import ru.zaroslikov.incubator.design.components.PriceSummaryTile
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.rememberSheetDraft
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetPickerField
import ru.zaroslikov.incubator.design.components.SheetRemoveButton
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.SuggestingSheetTextField
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.ToggleRow
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.ui.incubator.CapacityBlock
import ru.zaroslikov.incubator.ui.incubator.formatMoney
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.species.CustomSpeciesSheet
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignType

private val TileRadius = 16.dp
private val TileHeight = 102.dp

/**
 * Страницы формы. Порядок — порядок страниц пейджера: свайп влево с полей открывает
 * таблицу режима, свайп вправо возвращает к полям.
 */
private enum class BatchFormTab(val title: String, @param:DrawableRes val icon: Int) {
    Fields("Закладка", R.drawable.ic_egg_design_16),
    Schedule("Расписание", R.drawable.ic_calendar_design),
}

private val formTabs = BatchFormTab.entries.map { SlidingTab(it.title, it.icon) }

/**
 * Потолок высоты списка закладок в диалоге «Взять из архива».
 *
 * Строка выбора — это `RadioButton` (48 dp) плюс её отступы, около 56 dp, так что сюда
 * помещается три с половиной строки: половина четвёртой снизу и есть указание, что
 * список прокручивается. Всё, что ниже него — выбор «план или замеры» и кнопка
 * «Заменить», — обязано остаться на экране при любом количестве архивных закладок.
 */
private val ArchiveListMaxHeight = 200.dp

/** Ширина подписи в сводке о выбранной закладке: под самое длинное — «Досрочно». */
private val SummaryLabelWidth = 68.dp

/**
 * Смена предупреждения на сводку об архивной закладке и обратно.
 *
 * Уходящее гаснет быстрее, чем проявляется приходящее: иначе на середине перехода два
 * полупрозрачных текста накладываются друг на друга и читаются оба сразу. Высота идёт
 * дольше обоих — она тянет за собой таблицу, и резкий её скачок заметнее, чем сама
 * смена надписи.
 */
private const val HintFadeInMillis = 220
private const val HintFadeOutMillis = 120
private const val HintSizeMillis = 280

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
 * совпадают ровно. Меняются заголовок и надпись на кнопке.
 *
 * Страниц две, и они листаются вбок, как в шторке самой закладки: «Закладка» — поля из
 * макета, «Расписание» — таблица режима по дням. Раньше режим правили только уже внутри
 * созданной закладки, и увидеть, что именно закладывается, до нажатия «Заложить яйца»
 * было нельзя; таблица к тому же меняется вместе с видом птицы, а вид выбирают на
 * соседней странице — свайп между ними дешевле, чем выход из шторки и возвращение.
 *
 * Отступления от макета, все намеренные:
 * — плитки «Цесарки» нет, а есть плитка «Свой вид»: режима для цесарок в :domain не
 *   написано, и вместо одной недостающей птицы форма даёт описать любую — конструктором
 *   ([CustomSpeciesSheet]); свои виды встают в сетку за встроенными;
 * — переключателей «Авто охлаждение» / «Авто переворот» нет, как и в макете: закладка
 *   наследует автоматику от самого инкубатора, ведь это его возможность;
 * — звёздочки у «Названия» и «Количества» макет не рисует, но кнопка без них была бы
 *   недоступна без объяснения причины;
 * — второй страницы и переключателя над ней в макете тоже нет;
 * — породу макет спрашивает одну, а форма принимает сколько угодно — и при сохранении
 *   заводит по закладке на каждую (`BatchUiState.toBatches`): у закладки одна порода,
 *   и лоток на две породы — это две закладки под одним расписанием.
 *
 * @param incubatorId инкубатор, которому принадлежит закладка.
 * @param batchId ноль — создание, иначе правка существующей закладки.
 * @param draft черновик формы, общий у создания и правки: ViewModel у них одна, и
 *   двум черновикам над одним состоянием пришлось бы делить его пополам. См. [SheetDraft].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBatchSheet(
    incubatorId: Long,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    onSaved: (List<Long>) -> Unit,
    batchId: Long = 0,
    viewModel: AddBatchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val state = uiState.form
    val editing = uiState.isEditing
    // Ссылка на метод запоминается: она уходит параметром в страницу полей, и новая
    // лямбда на каждую рекомпозицию перерисовывала бы её целиком без всякой причины.
    val send = remember(viewModel) { viewModel::onIntent }
    val pagerState = rememberPagerState(pageCount = { BatchFormTab.entries.size })
    val scope = rememberCoroutineScope()

    // Заново форма заполняется только с отменённым черновиком: свёрнутую шторку
    // открывают ровно тем, что в ней набрали, — см. [SheetDraft].
    LaunchedEffect(incubatorId, batchId) {
        if (draft.claim(incubatorId, batchId)) send(AddBatchIntent.Load(incubatorId, batchId))
    }

    // «Сохранено» — эффект, а не callback: шторка закрывается один раз, ровно тогда,
    // когда запись состоялась, и не зависит от того, какая лямбда стояла в кнопке.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is AddBatchEffect.Saved -> {
                draft.discard()
                onSaved(effect.ids)
            }
        }
    }

    // Крестик — отказ от ввода, в отличие от свайпа вниз, которым шторку сворачивают
    // (в том числе случайно, прокручивая форму).
    val close = {
        draft.discard()
        onDismiss()
    }

    // Черновик конструктора вида живёт в композиции этой шторки: она ему и есть
    // «экран». Свернули конструктор — вид дописывают дальше, закрыли форму закладки —
    // закрыли и то, что стояло поверх неё.
    val speciesDraft = rememberSheetDraft()

    var openDatePicker by remember { mutableStateOf(false) }
    var openTimePicker by remember { mutableStateOf(false) }
    var openArchiveChoice by remember { mutableStateOf(false) }
    var openCustomSpecies by remember { mutableStateOf(false) }

    if (openDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = PastOrPresentSelectableDates,
            // Дата в базе может быть пустой или обрезанной — форма закладки открывается
            // и на такой, поэтому неразобранная дата означает «сегодня», а не падение.
            initialSelectedDateMillis = dateToPickerMillis(state.data)
                ?: System.currentTimeMillis(),
        )
        DatePickerDialogSample(datePickerState, state.data) { date ->
            send(AddBatchIntent.Update(state.copy(data = date)))
            openDatePicker = false
        }
    }

    // Тот же диалог, что и у напоминаний: время закладки записывается теми же «ЧЧ:ММ»,
    // и второй способ его набрать был бы лишним. У закладок, созданных до появления
    // поля, время пустое — диалог это переживает, см. TimePickerDialog.kt.
    if (openTimePicker) {
        TimePicker(time = state.time) { picked ->
            send(AddBatchIntent.Update(state.copy(time = picked)))
            openTimePicker = false
        }
    }

    // Имена событий сохранены с прежних экранов, чтобы аналитика оставалась сравнимой;
    // почему «Инкубатор» означает созданную закладку — в `Events`. Событие — про одну
    // закладку, поэтому при закладке нескольких пород их столько же, сколько закладок:
    // каждая со своим именем, породой, яйцами и ценой, как они и лягут в базу.
    fun report(event: String = if (editing) Events.BATCH_EDITED else Events.BATCH_CREATED) {
        val batches = if (editing) listOf(state.toBatch()) else state.toBatches()
        batches.forEach { batch ->
            val eventParameters: MutableMap<String, Any> = HashMap()
            eventParameters["Имя"] = batch.title
            eventParameters["Тип"] = batch.type
            eventParameters["Порода"] = batch.breed
            eventParameters["Кол-во"] = batch.eggAll
            eventParameters["Стоимость"] = if (batch.price == 0) "" else batch.price.toString()
            eventParameters["Прим"] = batch.note
            eventParameters["АвтоОхл"] = state.airing
            eventParameters["АвтоПрев"] = state.over
            // Сколько закладок легло одним нажатием — разрез «а по породам вообще
            // раскладывают?», по одному событию его не задать.
            eventParameters["Закладок"] = batches.size
            uiState.reminders.forEachIndexed { index, time ->
                eventParameters[index.toString()] = time.time
            }
            Analytics.report(event, eventParameters)
        }
    }

    // Соседом шторки, а не внутри неё: так конструктор ложится собственным окном поверх
    // формы, как шторка овоскопирования поверх закладки. Сохранённый вид тут же
    // становится выбранным — ради него конструктор и открывали.
    if (openCustomSpecies) {
        CustomSpeciesSheet(
            draft = speciesDraft,
            onDismiss = { openCustomSpecies = false },
            onSaved = { species ->
                openCustomSpecies = false
                send(AddBatchIntent.SelectNewSpecies(species))
            },
        )
    }

    if (openArchiveChoice) {
        ArchiveScheduleDialog(
            options = uiState.archiveOptions,
            onDismiss = { openArchiveChoice = false },
            onUseArchive = { sourceId, source ->
                openArchiveChoice = false
                send(AddBatchIntent.ApplyArchiveSchedule(sourceId, source))
            },
        )
    }

    // Расписание из файла — своего, сохранённого раньше, или присланного другим
    // человеком. Системный выбор файла, как у копии базы в настройках; тип «*/*»,
    // потому что у файла расписания своего MIME нет и провайдеры документов его не знают.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { send(AddBatchIntent.ImportFile(it)) } }

    uiState.importPreview?.let { preview ->
        ImportScheduleDialog(
            preview = preview,
            onDismiss = { send(AddBatchIntent.DismissImport) },
            onConfirm = { source ->
                Analytics.report(
                    Events.SCHEDULE_IMPORT,
                    mapOf(
                        "Вид" to preview.resolvedType,
                        "Дней" to preview.export.days,
                        "Источник" to if (source == ArchiveScheduleSource.Fact) "замеры" else "план",
                        "Вид создан" to preview.creatingSpecies,
                        "Вид сменён" to preview.speciesChanges,
                    ),
                )
                send(AddBatchIntent.ConfirmImport(source))
            },
        )
    }

    uiState.importError?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { send(AddBatchIntent.ClearImportError) },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Шторка на всю высоту: страницы разной длины, и переключатель под заголовком
        // не должен прыгать при свайпе. Прокручивается не она, а каждая страница.
        Column(modifier = Modifier.fillMaxHeight().clearFocusOnTap()) {
            Column(modifier = Modifier.padding(horizontal = SheetPadding)) {
                SheetHeader(
                    title = if (editing) "Закладка" else "Новая закладка",
                    onClose = close,
                )
                FormSpacer(12.dp)
                SlidingTabSwitcher(
                    tabs = formTabs,
                    position = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    },
                    onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 0.dp,
            ) { page ->
                when (BatchFormTab.entries[page]) {
                    // При правке поля заполняются из базы не сразу, и пустая форма над
                    // существующей закладкой читается как потерянные данные. При
                    // создании читать нечего, и колеса тут не бывает.
                    BatchFormTab.Fields ->
                        if (uiState.loading) LoadingBox()
                        else FieldsPage(
                            uiState = uiState,
                            editing = editing,
                            onIntent = send,
                            onPickDate = { openDatePicker = true },
                            onPickTime = { openTimePicker = true },
                            onAddSpecies = { openCustomSpecies = true },
                        )

                    BatchFormTab.Schedule -> SchedulePage(
                        rows = uiState.schedule,
                        ready = uiState.scheduleReady,
                        species = state.type,
                        canUseArchive = !editing && uiState.archiveOptions.isNotEmpty(),
                        autoTurn = state.over,
                        autoAiring = state.airing,
                        canReset = !editing,
                        canImport = !editing,
                        importing = uiState.importing,
                        origin = uiState.scheduleOrigin,
                        overlap = uiState.overlap,
                        hasOverlap = uiState.overlapShown,
                        onRowChange = { index, row ->
                            send(AddBatchIntent.UpdateScheduleRow(index, row))
                        },
                        onUseArchive = { openArchiveChoice = true },
                        onReset = { send(AddBatchIntent.ResetSchedule) },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }

            // Пока открыта клавиатура, кнопки нет.
            //
            // Окно шторки при вводе сжимается до полосы над клавиатурой, а всё, что
            // стоит вне пейджера, высоту держит: заголовок, переключатель страниц и
            // этот футер вместе съедали почти всю оставшуюся полосу, и от формы
            // оставалось одно-два поля. Пейджер здесь единственный, кто отдаёт место
            // (`weight(1f)`), поэтому отнимать его должно только то, без чего нельзя
            // обойтись прямо сейчас, — а нажимать «Сохранить», не дописав поле, никто
            // не станет. Клавиатура закрывается — кнопка возвращается на место.
            if (!WindowInsets.isImeVisible) {
                SheetSaveFooter(
                    // С несколькими породами кнопка называет число закладок: это последнее
                    // место, где можно узнать, что лоток ляжет двумя карточками, до того
                    // как они появятся.
                    label = when {
                        editing -> "Сохранить"
                        state.splitByBreeds -> "Заложить ${plural(state.breeds.size, "закладку", "закладки", "закладок")}"
                        else -> "Заложить яйца"
                    },
                    enabled = uiState.isValid,
                    onClick = {
                        report()
                        send(AddBatchIntent.Save)
                    },
                )
            }
        }
    }
}

/**
 * Первая страница — поля из макета.
 *
 * Кнопки сохранения здесь нет: она стоит под пейджером и видна с обеих страниц, иначе
 * с таблицы за ней пришлось бы возвращаться свайпом.
 */
@Composable
private fun FieldsPage(
    uiState: AddBatchState,
    editing: Boolean,
    onIntent: (AddBatchIntent) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onAddSpecies: () -> Unit,
) {
    val state = uiState.form
    val update = { form: BatchUiState -> onIntent(AddBatchIntent.Update(form)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SheetPadding)
            .padding(bottom = 20.dp)
    ) {
        FormSpacer(20.dp)
        FieldLabel(text = "Название закладки", required = true)
        SheetTextField(
            value = state.title,
            onValueChange = { update(state.copy(title = it)) },
            placeholder = "Например, Весенняя партия",
        )

        // Вид выбирают один раз, при закладке. Правке он показан запертым полем, а не
        // сеткой с одной выделенной плиткой: пять птиц под пальцем зовут нажать, а
        // нажатие ничего бы не сделало (`AddBatchViewModel.updateForm` вид при правке
        // не меняет). Почему нельзя — за «i»: яйца уже в лотке, расписание сгенерировано
        // под эту птицу и на его днях висят замеры, а у другой птицы другой срок.
        FormSpacer(20.dp)
        if (editing) {
            FieldLabelWithHint(
                text = "Вид птицы",
                hint = "Вид задаётся при закладке и не меняется: под него сгенерировано " +
                    "расписание по дням, а у другой птицы другой срок. Если вид указан " +
                    "ошибочно, удалите закладку и заложите яйца заново.",
            )
            LockedSpeciesField(species = state.type, days = uiState.catalog.incubationDays(state.type))
        } else {
            FieldLabel(text = "Вид птицы")
            SpeciesGrid(
                selected = state.type,
                catalog = uiState.catalog,
                onSelect = { update(state.copy(type = it)) },
                onAddSpecies = onAddSpecies,
            )
        }

        // Порода — свой блок со своей подписью, а не хвост блока вида. Это два разных
        // вопроса: вид решает срок и режим, порода — только чьи яйца в лотке; под одной
        // подписью «Порода / вид птицы» поле породы читалось как продолжение сетки, и
        // ответ «Курицы» выглядел ответом на оба. Поле — то же, что у бренда и модели
        // инкубатора: из него выпадает список пород, которые для этой птицы уже
        // вводили, а набрать своё можно всегда.
        //
        // У закладки одна порода. Вторая появляется по пунктирной кнопке и с этого
        // момента у каждой породы своя карточка со своими яйцами и ценой, а при
        // сохранении **каждая становится отдельной закладкой** под общим названием с
        // хвостом-породой (`BatchUiState.toBatches`): лоток на две породы — это две
        // закладки под одним расписанием, у каждой свой вывод, своя отбраковка и свои
        // деньги. Форма говорит об этом под карточками и в кнопке сохранения, чтобы
        // две карточки в списке не стали неожиданностью. При правке кнопки нет: породу
        // можно переименовать, но не добавить — у закладки уже свои яйца, режим и замеры.
        FormSpacer(20.dp)
        FieldLabel(text = "Порода")
        if (state.splitByBreeds) {
            state.breeds.forEachIndexed { index, row ->
                if (index > 0) FormSpacer(8.dp)
                BreedCard(
                    row = row,
                    number = index + 1,
                    suggestions = state.breedSuggestions(uiState.knownBreeds, index),
                    onChange = { onIntent(AddBatchIntent.UpdateBreed(index, it)) },
                    onRemove = { onIntent(AddBatchIntent.RemoveBreed(index)) },
                )
            }
            FormSpacer(8.dp)
            Text(
                text = splitNotice(state),
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val first = state.breeds.firstOrNull() ?: BreedUiState()
            SuggestingSheetTextField(
                value = first.name,
                onValueChange = {
                    onIntent(AddBatchIntent.UpdateBreed(0, first.copy(name = it)))
                },
                placeholder = "Ломан Браун, Пекинская…",
                suggestions = state.breedSuggestions(uiState.knownBreeds, 0),
                capitalization = KeyboardCapitalization.Sentences,
            )
        }
        if (!editing) {
            FormSpacer(8.dp)
            DashedAddButton(
                text = if (state.splitByBreeds) "Добавить породу" else "Ещё одна порода — отдельной закладкой",
                onClick = { onIntent(AddBatchIntent.AddBreed) },
            )
        }

        // Количество с отбраковкой: отбраковка встаёт рядом с количеством, из которого
        // её и вычитают. При создании отбраковки нет вовсе — у ещё не заложенной
        // закладки убирать нечего, — и количество разворачивается на всю ширину: пустая
        // половина ряда читалась бы как поле, которое не загрузилось.
        FormSpacer(20.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel(text = "Количество яиц", required = true)
                if (state.splitByBreeds) {
                    // Сумма по породам — считается, а не вводится: второе поле для того
                    // же числа рано или поздно разошлось бы со строками над ним. Это
                    // яйца всех закладок, которые сохранит форма, о чём и говорит подпись.
                    DerivedCountField(
                        value = state.breedEggsTotal.toString(),
                        hint = "всего по породам",
                    )
                } else {
                    SheetTextField(
                        value = state.eggAll,
                        onValueChange = {
                            update(state.copy(eggAll = it.filter(Char::isDigit)))
                        },
                        // Прочерк, а не пример: «24» читалось как уже введённое число —
                        // сколько яиц в лотке, знает только тот, кто их заложил, и
                        // подсказывать ему нечего. Тот же прочерк, что у «Стоимости».
                        placeholder = "—",
                        numeric = true,
                    )
                }
            }
            if (editing) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Отбраковано яиц")
                    SheetTextField(
                        value = state.eggRejected,
                        onValueChange = {
                            update(state.copy(eggRejected = it.filter(Char::isDigit)))
                        },
                        placeholder = "0",
                        numeric = true,
                    )
                }
            }
        }

        // Та же полоса, что на карточке инкубатора, только уже с этой закладкой внутри:
        // сколько мест останется, видно до того, как яйца легли в лоток. Только при
        // создании — при правке закладка сама среди идущих и считала бы себя дважды —
        // и только когда вместимость указана: без неё полоса ответить не может, а
        // просьба заполнить её уже стоит на карточке, здесь же она бы только мешала
        // закладывать яйца.
        if (!editing && uiState.capacity > 0) {
            FormSpacer(12.dp)
            IncubatorFill(
                occupied = uiState.occupiedEggs,
                adding = state.eggCount,
                capacity = uiState.capacity,
                split = state.splitByBreeds,
            )
        }

        FormSpacer(20.dp)
        val currency = LocalUnits.current.currency
        FieldLabel(text = "Стоимость, ${currency.symbol}")
        if (state.splitByBreeds) {
            // Цена у каждой породы своя, и складывать их можно только в рублях. Поле
            // здесь показывает итог по всем будущим закладкам, а правят цену в
            // карточках выше.
            DerivedCountField(value = formatMoney(state.breedPriceTotal, currency), hint = "всего по породам")
        } else {
            PriceRow(
                price = state.price,
                perUnit = state.pricePerEgg,
                perUnitLabel = "за яйцо",
                totalLabel = "за всё",
                onPriceChange = {
                    update(state.copy(price = it.filter(Char::isDigit)))
                },
                onModeChange = { update(state.copy(pricePerEgg = it)) },
            )
        }
        FormSpacer(8.dp)
        PriceSummary(
            price = if (state.splitByBreeds) state.breedPriceTotal
            else state.price.toIntOrNull() ?: 0,
            // Сумма по породам — всегда «за всё»: сложены были рубли.
            perEgg = !state.splitByBreeds && state.pricePerEgg,
            eggCount = state.eggCount,
        )

        // Дата и час — одна величина, разнесённая по колонкам ради ввода, и стоять они
        // должны друг против друга. Ряд стоит прямо над «Напоминаниями»: там идут те же
        // «ЧЧ:ММ» из того же диалога, и час закладки читается вместе с ними — это тот
        // момент, от которого отсчитывается срок, а напоминания расставляют по нему день.
        FormSpacer(20.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel(text = "Дата закладки")
                SheetPickerField(
                    value = state.data,
                    placeholder = "—",
                    onClick = onPickDate,
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
            Column(Modifier.weight(1f)) {
                FieldLabel(text = "Время закладки")
                SheetPickerField(
                    value = state.time,
                    placeholder = "—",
                    onClick = onPickTime,
                    modifier = Modifier.fillMaxWidth(),
                    // Моноширинным, как время напоминания: «08:05» и «19:30» стоят
                    // одинаковой ширины, а рядом с датой это заметно.
                    textStyle = DesignType.MonoField,
                    trailing = {
                        Icon(
                            painter = painterResource(R.drawable.ic_clock_design),
                            contentDescription = "Выбрать время",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        FormSpacer(20.dp)
        FieldLabel(text = "Напоминания")
        uiState.reminders.forEachIndexed { index, time ->
            if (index > 0) FormSpacer(8.dp)
            ReminderCard(
                time = time.time,
                note = time.note,
                onTimeChange = { onIntent(AddBatchIntent.UpdateReminder(index, time = it)) },
                onNoteChange = { onIntent(AddBatchIntent.UpdateReminder(index, note = it)) },
                onRemove = { onIntent(AddBatchIntent.RemoveReminder(index)) },
            )
        }
        if (uiState.reminders.isNotEmpty()) FormSpacer(8.dp)
        DashedAddButton(
            text = "Добавить напоминание",
            onClick = { onIntent(AddBatchIntent.AddReminder) },
        )

        // Автоматика — единственный блок формы, который спрашивает не про яйца, а про
        // устройство, и стоит он последним, перед заметкой: всё выше него — то, ради
        // чего форму открыли (что заложили, почём, когда, о чём напомнить), а это —
        // поправка к настройкам устройства, нужная не всякий раз. Значения приходят от
        // инкубатора (`AddBatchViewModel.load`), и переключатель здесь нужен ровно
        // затем, чтобы их можно было отменить на одну закладку: устройство с
        // автопереворотом остаётся с автопереворотом, а лоток, который в этот раз
        // переворачивают руками, — нет.
        //
        // Только при создании. Нормы в столбцах поворота и проветривания ставятся один
        // раз, вместе с расписанием, и на днях уже заложенной закладки висят замеры;
        // стереть или вернуть норму задним числом — это переписать не план, а то, по
        // чему человек работал.
        if (!editing) {
            FormSpacer(20.dp)
            FieldLabelWithHint(
                text = "Автоматика инкубатора",
                hint = "Что инкубатор делает сам. Пока включено, нормы поворотов и " +
                    "проветриваний по дням не ставятся — в расписании и в счётчиках дня " +
                    "будет «на автомате».",
            )
            ToggleRow(
                title = "Автопереворот",
                checked = state.over,
                onCheckedChange = { onIntent(AddBatchIntent.SetAutoTurn(it)) },
            )
            FormSpacer(8.dp)
            ToggleRow(
                title = "Автопроветривание",
                checked = state.airing,
                onCheckedChange = { onIntent(AddBatchIntent.SetAutoAiring(it)) },
            )
        }

        FormSpacer(20.dp)
        FieldLabel(text = "Заметка")
        SheetTextField(
            value = state.note,
            // Как и заметка к замеру: фраза, а не значение, и первая буква заглавная
            // даже у текста, вставленного из буфера.
            onValueChange = { update(state.copy(note = it.capitalizeFirst())) },
            placeholder = "Особенности партии…",
            minHeight = 70.dp,
            singleLine = false,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        )
    }
}

// --- Расписание ------------------------------------------------------------------------------

/**
 * Вторая страница — режим по дням таблицей: строка на день, столбцы «Температура»,
 * «Влажность», «Переворот», «Проветривание». Правится любая клетка.
 *
 * Предупреждение стоит **до** таблицы, а не под ней и не в диалоге: режим в :domain —
 * усреднённый справочник, а инкубаторы врут каждый по-своему, и узнать об этом надо
 * прежде, чем поверить цифрам, а не после.
 *
 * Шапка таблицы вынесена из прокрутки и висит над строками: до тридцати одной строки
 * подряд, и уехавшие подписи столбцов превратили бы её в четыре колонки чисел без
 * значения. Прокручиваются только строки — их и много.
 *
 * Столбцы заданы долями, а не фиксированной шириной с горизонтальной прокруткой:
 * таблица должна помещаться целиком на любом телефоне, иначе правка проветривания
 * требовала бы отдельного жеста на каждой из тридцати строк. Плата — «2 раза по
 * 5 минут» в клетке видно не целиком, оно прокручивается внутри самого поля.
 */
@Composable
private fun SchedulePage(
    rows: List<ValueUiState>,
    ready: Boolean,
    species: String,
    canUseArchive: Boolean,
    canReset: Boolean,
    /** Кнопка «Из файла»: только при создании, как и две другие. */
    canImport: Boolean,
    /** Файл читается — кнопка на это время не нажимается второй раз. */
    importing: Boolean,
    autoTurn: Boolean,
    autoAiring: Boolean,
    origin: ScheduleOrigin?,
    /** Приговор каждой строке — по индексу [rows]; см. `ScheduleOverlap.kt`. */
    overlap: List<CellVerdicts?>,
    /** Есть окрашенные дни — над таблицей стоит легенда. */
    hasOverlap: Boolean,
    onRowChange: (Int, ValueUiState) -> Unit,
    onUseArchive: () -> Unit,
    onReset: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SchedulePadding)
    ) {
        FormSpacer(16.dp)
        // Одно место, три разных сообщения: пока режим справочный — предупреждение о
        // том, что он справочный; как только он взят из архива — из какой закладки; из
        // файла — чей он и на сколько дней. «По умолчанию» и смена вида возвращают
        // предупреждение вместе с режимом.
        //
        // Меняются они не подменой, а переходом: блоки разной высоты, и мгновенная
        // замена дёргает вниз всю таблицу под ними — на глаз это неотличимо от того,
        // что уехал сам список дней. `SizeTransform` ведёт высоту, прозрачность —
        // содержимое, поэтому видно, что сменилась именно надпись над таблицей.
        AnimatedContent(
            targetState = origin,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(HintFadeInMillis)),
                    initialContentExit = fadeOut(tween(HintFadeOutMillis)),
                    sizeTransform = SizeTransform(clip = false) { _, _ ->
                        tween(HintSizeMillis)
                    },
                )
            },
            label = "schedule-hint",
        ) { source ->
            when (source) {
                is ScheduleOrigin.Archive -> ArchiveSourceCard(source.applied)
                is ScheduleOrigin.File -> FileSourceCard(source)
                null -> ScheduleDisclaimer()
            }
        }

        // Три действия над таблицей — откуда взять режим и как вернуть справочный —
        // по две кнопки в ряд: три в одном ряду на 360 dp ужимают «Взять из архива» до
        // переноса посреди слова. Порядок — по тому, как часто нужны: архив своего
        // хозяйства, файл со стороны, сброс.
        val actions = buildList {
            if (canUseArchive) add("Взять из архива" to onUseArchive)
            if (canImport) add((if (importing) "Читаем файл…" else "Из файла") to onImport)
            if (canReset) add("По умолчанию" to onReset)
        }
        actions.chunked(2).forEach { row ->
            FormSpacer(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (text, onClick) ->
                    ScheduleActionButton(
                        text = text,
                        onClick = onClick,
                        enabled = !(importing && onClick === onImport),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        FormSpacer(16.dp)
        // Пока таблица не наполнена — колесо: при правке строки приезжают из базы не
        // сразу, и «режима нет» в первые кадры было бы неправдой, а пустое место под
        // заголовком неотличимо от неё.
        if (!ready) {
            LoadingBox()
            return@Column
        }
        if (rows.isEmpty()) {
            // Пусто бывает ровно в одном случае: в :domain нет режима для этого вида,
            // и `when` в setIncubator молча ушёл в else.
            Text(
                text = "Режим для вида «$species» пока не описан — дни появятся, " +
                    "когда он будет добавлен.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Легенда — только пока есть что объяснять: без соседей таблица белая, и
        // строка про три цвета над ней говорила бы о том, чего на экране нет. Стоит
        // между кнопками и шапкой, а не в предупреждении: оно сменяется карточкой
        // источника, а цвета от источника не зависят.
        AnimatedVisibility(visible = hasOverlap) {
            OverlapLegend()
        }

        ScheduleHeaderRow()
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(CellGap),
        ) {
            // Без ключей намеренно: список не переупорядочивается, а весь ввод живёт
            // во ViewModel, так что позиции хватает. Ключ по дню уронил бы список на
            // закладке, где день по какой-то причине встретился дважды.
            itemsIndexed(rows) { index, row ->
                ScheduleRow(
                    row = row,
                    onChange = { onRowChange(index, it) },
                    autoTurn = autoTurn,
                    autoAiring = autoAiring,
                    verdicts = overlap.getOrNull(index),
                )
            }
        }
    }
}

/**
 * Легенда к цветам клеток: с чем сравнивают и что значат три заливки.
 *
 * Три квадратика теми же цветами, что и клетки, и по слову на каждый — «совпадает»,
 * «допустимо», «расходится». Первая строка называет предмет сравнения: без неё
 * зелёная клетка читалась бы как «правильное значение», а она значит лишь «такое же,
 * как у соседей», и правы могут быть как раз соседи или никто.
 */
@Composable
private fun OverlapLegend() {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = "Цвет клетки — расхождение с закладками, которые уже идут в этом " +
                "инкубаторе в тот же день. Без цвета — сравнивать не с чем: соседей в " +
                "этот день нет или значение не задано.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormSpacer(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendSwatch(DesignPalette.ScheduleMatch, "совпадает")
            LegendSwatch(DesignPalette.ScheduleTolerable, "допустимо")
            LegendSwatch(DesignPalette.ScheduleConflict, "расходится")
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(3.dp)),
        )
        Text(
            text = label,
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Предупреждение над таблицей: цифры справочные, инкубатор — свой у каждого. */
@Composable
private fun ScheduleDisclaimer() {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.SpeciesTileSelected,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = DesignPalette.DateEmphasis,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Text(
                text = "Значения носят ознакомительный характер. Сверяйтесь с " +
                    "инструкцией к своему инкубатору и правьте режим под свою партию — " +
                    "менять его можно и после закладки.",
                style = DesignType.Note,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * То, что стоит над таблицей вместо [ScheduleDisclaimer], когда режим взят из архива.
 *
 * Предупреждение говорит «цифры справочные, сверяйтесь с инструкцией» — это про режим
 * из :domain. К режиму, взятому из живой закладки, оно неприменимо: он уже прошёл через
 * чей-то инкубатор, и вопрос про него другой — через чей именно. На его месте поэтому
 * стоит сама закладка: какая, сколько дала, чем кончилась и что из неё взяли — план или
 * среднее по замерам.
 *
 * Число замеров названо числом, а не словами «есть замеры»: от него зависит, чего стоит
 * среднее — три записи за три недели усредняются во что угодно. Строка «Досрочно»
 * появляется только у прерванной закладки и, в отличие от остальных, говорит, что этот
 * режим брать, возможно, не стоило.
 */
@Composable
private fun ArchiveSourceCard(applied: AppliedArchive) {
    val batch = applied.option.batch
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.SpeciesTileSelected,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = when (applied.source) {
                    ArchiveScheduleSource.Plan -> "Режим из архивной закладки"
                    ArchiveScheduleSource.Fact -> "Среднее по замерам архивной закладки"
                },
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOf(batch.title, batch.breed)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = DesignType.CardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            FormSpacer(6.dp)
            SummaryLine(
                label = "Сроки",
                value = if (batch.dateEnd.isNotBlank()) {
                    "${batch.data} → ${batch.dateEnd}"
                } else {
                    batch.data
                },
            )
            SummaryLine(label = "Яйца", value = archiveEggsLine(batch))
            SummaryLine(
                label = "Замеры",
                value = if (applied.option.measurementCount > 0) {
                    val count = applied.option.measurementCount
                    "$count ${measurementsWord(count)}"
                } else {
                    "не записывались"
                },
            )
            if (batch.endReason.isNotBlank()) {
                SummaryLine(label = "Досрочно", value = batch.endReason)
            }
        }
    }
}

/**
 * То, что стоит над таблицей вместо [ScheduleDisclaimer], когда режим взят из файла.
 *
 * Файл несёт только вид и дни, и сводка говорит ровно это: чья птица, сколько дней и
 * когда файл сделан. Вид назван отдельной строкой намеренно: из архива своего
 * хозяйства вид совпадает с формой по построению, а из файла мог прийти любой, и
 * человек только что согласился на него в диалоге — здесь он видит, на что.
 */
@Composable
private fun FileSourceCard(origin: ScheduleOrigin.File) {
    val export = origin.export
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.SpeciesTileSelected,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = when (origin.source) {
                    ArchiveScheduleSource.Plan -> "Режим из файла расписания"
                    ArchiveScheduleSource.Fact -> "Среднее по замерам из файла расписания"
                },
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = export.type,
                style = DesignType.CardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FormSpacer(6.dp)
            SummaryLine(label = "Срок", value = plural(export.days, "день", "дня", "дней"))
            SummaryLine(label = "Замеры", value = measurementsLine(export.measurementCount))
            FileProvenanceLines(export)
            if (export.exportedAt.isNotBlank()) {
                SummaryLine(label = "Файл", value = "от ${export.exportedAt}")
            }
        }
    }
}

/** «12 замеров» или «не записывались» — одна строка для карточки над таблицей и диалога. */
private fun measurementsLine(count: Int): String =
    if (count > 0) "$count ${measurementsWord(count)}" else "не записывались"

/**
 * Откуда режим родом: порода закладки-источника и инкубатор, на котором он выверен.
 *
 * Обе строки — на показ, и только когда есть что показать: пустая «Порода» читалась бы
 * как забытая графа. В форму ни то ни другое не уезжает — порода и устройство у новой
 * закладки свои, — но режим, выверенный на «Блиц Норма 72» для «Ломан Браун», значит
 * не то же, что режим без роду и племени, и это решает, брать его или нет. Одна функция
 * на диалог и карточку над таблицей, чтобы после нажатия карточка не сказала ничего
 * нового.
 */
@Composable
private fun FileProvenanceLines(export: ScheduleExport) {
    if (export.breed.isNotBlank()) SummaryLine(label = "Порода", value = export.breed)
    if (export.incubatorLabel.isNotBlank()) SummaryLine(label = "Инкубатор", value = export.incubatorLabel)
}

// --- Расписание из файла ---------------------------------------------------------------------

/**
 * Что в файле и что импорт сделает с формой — до того, как он это сделает.
 *
 * Диалог стоит между выбором файла и переписанной таблицей ради двух предупреждений,
 * которые нельзя показать после. **Вид не совпадает**: в форме выбраны «Курицы», а файл
 * — расписание утки. Импорт сменит вид, а с ним срок и овоскопирования; файл при этом
 * мог быть выбран по ошибке, и единственный момент сказать об этом — сейчас, пока
 * таблица ещё та, что была. **Вида нет**: расписание описано для своего вида, которого
 * у получателя нет, и он будет заведён в его свои виды — запись в базу, которая
 * останется, даже если форму потом закроют крестиком.
 *
 * Сводка та же, что покажет карточка над таблицей после импорта, — чтобы после нажатия
 * ничего не оказалось неожиданным. Последняя строка перечисляет, чего импорт **не**
 * трогает: файл несёт одно расписание, и человек, помнящий про «Взять из архива»,
 * вправе ждать от него того же.
 */
@Composable
private fun ImportScheduleDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (ArchiveScheduleSource) -> Unit,
) {
    val export = preview.export
    val hasFact = export.fact != null
    var source by remember(preview) { mutableStateOf(ArchiveScheduleSource.Plan) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Расписание из файла", style = DesignType.SectionTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = export.type,
                    style = DesignType.CardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FormSpacer(4.dp)
                SummaryLine(label = "Срок", value = plural(export.days, "день", "дня", "дней"))
                SummaryLine(label = "Замеры", value = measurementsLine(export.measurementCount))
                FileProvenanceLines(export)
                if (export.exportedAt.isNotBlank()) {
                    SummaryLine(label = "Файл", value = "от ${export.exportedAt}")
                }

                if (preview.speciesChanges) {
                    FormSpacer(12.dp)
                    ImportNotice(
                        text = "В файле расписание для вида «${preview.resolvedType}», а в " +
                            "форме выбран «${preview.currentType}». После импорта закладка " +
                            "станет по виду «${preview.resolvedType}» — с его сроком и " +
                            "овоскопированиями. Если яйца не этой птицы, файл не подходит.",
                        emphasised = true,
                    )
                }
                if (preview.creatingSpecies) {
                    FormSpacer(12.dp)
                    ImportNotice(
                        text = "Вида «${preview.resolvedType}» у вас нет — он будет добавлен " +
                            "в свои виды и появится в сетке птиц.",
                    )
                }

                // Тот же выбор, что в «Взять из архива», теми же строками: план той
                // закладки или среднее по её замерам, второе недоступно у файла без
                // замеров — иначе две строки давали бы одну таблицу.
                FormSpacer(12.dp)
                DialogSubtitle("Что взять из файла")
                ArchiveChoiceRow(
                    selected = source == ArchiveScheduleSource.Plan,
                    title = "Режим той закладки",
                    subtitle = "Цифры, с которыми она начиналась",
                    onClick = { source = ArchiveScheduleSource.Plan },
                )
                ArchiveChoiceRow(
                    selected = source == ArchiveScheduleSource.Fact,
                    title = "Среднее по её замерам",
                    subtitle = if (hasFact) {
                        "Каким режим вышел на самом деле; дни без замеров останутся из режима"
                    } else {
                        "У той закладки замеров нет"
                    },
                    enabled = hasFact,
                    onClick = { source = ArchiveScheduleSource.Fact },
                )

                FormSpacer(12.dp)
                Text(
                    text = "Температура, влажность, поворот и проветривание по дням " +
                        "заменятся значениями из файла — их будет видно в таблице. " +
                        "Остальное в форме не изменится.",
                    style = DesignType.Note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(source) }) {
                Text(text = "Заменить", color = DesignPalette.Accent)
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
 * Замечание в диалоге импорта — кремовая плашка с «i», как предупреждение над таблицей.
 * [emphasised] — несовпадение вида — набрано основным цветом текста, а не приглушённым:
 * это единственное место, где нажатие «Заменить» может оказаться ошибкой.
 */
@Composable
private fun ImportNotice(text: String, emphasised: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.SpeciesTileSelected,
        border = BorderStroke(0.8.dp, if (emphasised) DesignPalette.Accent else DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = DesignPalette.DateEmphasis,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Text(
                text = text,
                style = DesignType.Note,
                color = if (emphasised) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Файл не прочитан: не тот, повреждён или от более новой версии. Текст приходит готовым. */
@Composable
private fun ImportErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Не получилось", style = DesignType.SectionTitle) },
        text = {
            Text(
                text = message,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Понятно", color = DesignPalette.Accent)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}


/** Строка сводки: подпись слева фиксированной ширины, значение справа. */
@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = label,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(SummaryLabelWidth),
        )
        Text(
            text = value,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * «60 заложено · 3 брак · 45 вывелось (75%)» — вся арифметика закладки одной строкой.
 *
 * Отбраковка отсутствует в строке, когда её не было: «0 брак» читается как графа,
 * которую забыли заполнить. Здесь это только та отбраковка, что вписана руками в
 * форме, — овоскопирования карточка не читает, а вывод и так сказан процентом.
 */
private fun archiveEggsLine(batch: Batch): String {
    val parts = mutableListOf("${batch.eggAll} заложено")
    if (batch.eggRejected > 0) parts += "${batch.eggRejected} брак"
    parts += if (batch.eggAll > 0) {
        "${batch.eggAllEND} вывелось (${batch.eggAllEND * 100 / batch.eggAll}%)"
    } else {
        "${batch.eggAllEND} вывелось"
    }
    return parts.joinToString(" · ")
}

/** Склонение «замер», как в `chicks()` на экране инкубатора. */
private fun measurementsWord(count: Int): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return "замеров"
    return when (count % 10) {
        1 -> "замер"
        2, 3, 4 -> "замера"
        else -> "замеров"
    }
}

// --- Вид птицы -------------------------------------------------------------------------------

/**
 * Сетка 3 в ряд из макета (узел 12:4782): встроенные виды, за ними свои, последней —
 * плитка «Свой вид», открывающая конструктор.
 *
 * Свои виды стоят в той же сетке теми же плитками, а не отдельным списком: для закладки
 * свой вид — такая же птица, как курица, с тем же сроком под названием. Отличает их
 * только эмодзи — общее яйцо, потому что рисовать по птице на каждое придуманное имя
 * неоткуда.
 *
 * Вид, которого в каталоге уже нет, но который стоит в правящейся закладке, всё равно
 * получает плитку — без срока. Иначе сетка показала бы «ничего не выбрано» над закладкой,
 * у которой вид есть, и первое же нажатие подменило бы его молча.
 *
 * Плитка «Свой вид» — пунктирная, как «Добавить напоминание» ниже: она не выбор, а
 * дверь, и выглядеть как ещё одна птица не должна.
 */
@Composable
private fun SpeciesGrid(
    selected: String,
    catalog: SpeciesCatalog,
    onSelect: (String) -> Unit,
    onAddSpecies: () -> Unit,
) {
    val names = catalog.allNames.let { known ->
        if (selected.isNotBlank() && selected !in known) known + selected else known
    }
    val builtIn = names.filter { it in SpeciesCatalog.BUILT_IN }
    val custom = names.filterNot { it in SpeciesCatalog.BUILT_IN }

    // Своих видов бывает сколько угодно, и на десятке их сетка вырастает выше экрана:
    // форму открывают, чтобы заложить яйца, а она начинается со списка птиц, который
    // надо пролистать. Свёрнутая сетка поэтому ровно в две строки — пять встроенных
    // птиц и шестая ячейка, — а всё остальное за кнопкой под ней.
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Шестая ячейка — дверь в конструктор («Свой вид», в списке это null), а если выбран
    // свой вид, то он: сетка, в которой ничего не выбрано над закладкой, у которой вид
    // есть, врёт опаснее, чем спрятанная дверь, — та в одном нажатии, а подменить вид
    // можно случайно и молча. Дверь тогда уходит в конец хвоста.
    val selectedCustom = custom.firstOrNull { it == selected }
    val head: List<String?> = builtIn.take(5) + listOf(selectedCustom)
    val tail: List<String?> = custom.filterNot { it == selectedCustom } +
        if (selectedCustom == null) emptyList() else listOf(null)

    // Раскрытие и сворачивание — движение самих плиток, а не рост пустоты под ними.
    // Первая версия анимировала высоту всей сетки через `animateContentSize`, и на
    // сворачивании это было заметно: ряды пропадали мгновенно, а плавно съезжалось
    // пустое место, где они только что стояли. Хвост поэтому живёт в собственном
    // [AnimatedVisibility] — тогда он уезжает вверх и гаснет вместе с высотой.
    Column {
        SpeciesRows(head, catalog, selected, onSelect, onAddSpecies)

        if (tail.isNotEmpty()) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(SpeciesGridExpandMillis)) +
                    fadeIn(tween(SpeciesGridExpandMillis)),
                exit = shrinkVertically(tween(SpeciesGridExpandMillis)) +
                    fadeOut(tween(SpeciesGridExpandMillis)),
            ) {
                // Отступ внутри, а не между детьми колонки: снаружи он занимал бы свои
                // 8 dp и на свёрнутой сетке, оставляя щель под вторым рядом.
                Column(Modifier.padding(top = 8.dp)) {
                    SpeciesRows(tail, catalog, selected, onSelect, onAddSpecies)
                }
            }

            FormSpacer(8.dp)
            // Кнопка, а не строка текста: она стоит под сеткой плиток, и подпись в один
            // ряд с ними читалась бы как подпись к нижней плитке. Та же белая кнопка с
            // обводкой, что «По умолчанию» и «Взять из архива» над таблицей расписания,
            // во всю ширину — под сеткой ей не с чем делить строку. Число в подписи не
            // ставится: за кнопкой не только виды, но и «Свой вид», и счётчик, в котором
            // дверь посчитана как птица, врал бы на единицу.
            ScheduleActionButton(
                text = if (expanded) "Свернуть" else "Показать все виды",
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Ряды плиток по три в ряд; `null` в списке — плитка «Свой вид».
 *
 * Отдельная функция, потому что сетка рисуется двумя кусками — постоянные две строки и
 * хвост под [AnimatedVisibility], — и вторая копия этих рядов разошлась бы с первой на
 * первом же изменении плитки.
 */
@Composable
private fun SpeciesRows(
    cells: List<String?>,
    catalog: SpeciesCatalog,
    selected: String,
    onSelect: (String) -> Unit,
    onAddSpecies: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            // [TileRow], а не обычный `Row`: с тех пор как плитка растёт под длинное
            // название (см. [SpeciesTile]), высота ряда задаётся самой высокой из трёх,
            // а не константой, и без выравнивания соседки «Цесарок крупных» стояли бы
            // на строку выше неё.
            TileRow(spacing = 8.dp) {
                row.forEach { species ->
                    if (species == null) {
                        AddSpeciesTile(onClick = onAddSpecies, modifier = tileWeight())
                    } else {
                        SpeciesTile(
                            species = species,
                            days = catalog.incubationDays(species),
                            selected = species == selected,
                            onClick = { onSelect(species) },
                            modifier = tileWeight(),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Сколько длится раскрытие сетки видов.
 *
 * Те же 180 мс, что у полосы фильтров и переключателя архива на стартовом экране: это
 * одно и то же движение — блок над списком, который приходит и уходит, — и разная его
 * скорость в двух местах читалась бы как разная работа приложения.
 */
private const val SpeciesGridExpandMillis = 180

/** Пунктирная плитка той же высоты, что плитки видов; открывает конструктор своего вида. */
@Composable
private fun AddSpeciesTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Цвет читается здесь, а не внутри `drawBehind`: та лямбда — не композиция,
    // а [DesignPalette] живёт только в ней.
    val dashedBorder = DesignPalette.DashedBorder
    Column(
        modifier = modifier
            .heightIn(min = TileHeight)
            .clip(RoundedCornerShape(TileRadius))
            .background(DesignPalette.DashedSurface)
            .drawBehind {
                drawRoundRect(
                    color = dashedBorder,
                    cornerRadius = CornerRadius(TileRadius.toPx()),
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx()), 0f
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus_design),
            contentDescription = null,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Свой вид",
            style = DesignType.CaptionEmphasis,
            color = DesignPalette.Accent,
        )
    }
}

/**
 * Плитка вида птицы.
 *
 * **Высота — `heightIn(min = …)`, а не `height`, и название ограничено двумя строками.**
 * Встроенные виды называются одним коротким словом, а свой вид — как его назовут: имя
 * длиннее «Цесарок крупных» разворачивалось в три-четыре строки и выдавливало срок из
 * плитки, а всё, что не помещалось в её 102 dp, обрезалось по краю без многоточия —
 * «[Дракон] (365» — потому что высота была фиксированной, а клип скруглением молчалив.
 * Теперь плитка растёт под название (ряд выравнивает соседок по ней, см. [SpeciesGrid]),
 * а на третьей строке название кончается многоточием: срок под ним — единственное, что
 * плитка сообщает кроме имени, и уступать ему место не должен.
 */
@Composable
private fun SpeciesTile(
    species: String,
    /** Срок инкубации из каталога; `null` — вида в каталоге нет, подпись пустая. */
    days: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(TileRadius),
        color = if (selected) DesignPalette.SpeciesTileSelected else DesignPalette.Surface,
        border = BorderStroke(
            0.8.dp,
            if (selected) DesignPalette.Accent else DesignPalette.CardBorder,
        ),
        modifier = modifier.heightIn(min = TileHeight),
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
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = days?.let { "$it дн." }.orEmpty(),
                style = DesignType.MonoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// --- Стоимость -------------------------------------------------------------------------------

/**
 * Две плитки-итога под полем: цена за яйцо и за всю партию.
 *
 * Пересчёт «за всё → за яйцо» делится нацело: копеек в базе нет, и показывать
 * дробную цену яйца там, где всё остальное в целых рублях, было бы враньём о точности.
 */
@Composable
private fun PriceSummary(price: Int, perEgg: Boolean, eggCount: Int) {
    val currency = LocalUnits.current.currency
    val perEggValue = if (perEgg) price else if (eggCount > 0) price / eggCount else 0
    val totalValue = if (perEgg) price * eggCount else price

    // [TileRow]: «За все 100: 12 000 ₽» переносится на вторую строку заметно раньше
    // соседней «За одно яйцо: 120 ₽», и без выравнивания правая плитка вырастала бы.
    TileRow {
        PriceSummaryTile(
            label = "За одно яйцо: ",
            value = "$perEggValue ${currency.symbol}",
            modifier = tileWeight(),
        )
        PriceSummaryTile(
            label = if (eggCount > 0) "За все $eggCount: " else "За всю партию: ",
            value = "$totalValue ${currency.symbol}",
            modifier = tileWeight(),
        )
    }
}


// --- Породы ----------------------------------------------------------------------------------

/**
 * Карточка одной породы — будущей закладки, когда их в форме две и больше.
 *
 * Внутри ровно то, чем закладки одного лотка друг от друга отличаются: имя, яйца и
 * цена; всё общее — название, вид, дата, режим, напоминания — остаётся в полях формы
 * и достаётся каждой закладке без изменений. Отбраковки нет и при правке: при правке
 * карточек не бывает вовсе, у закладки одна порода.
 *
 * Поля чисел узкие и с цифрой посередине, как клетки таблицы расписания: две-три цифры
 * у левого края широкого поля читаются как незаполненные.
 *
 * Крестик вынесен из ряда полей в собственную строку над ними. Стоя рядом с «Яиц», он
 * отнимал у названия породы ширину кнопки вместе с зазором, и «Ломан Браун белый»
 * упирался в него, хотя название — единственное, чем две карточки различаются. Строка
 * над полями ничего у них не отнимает, а её левая половина занята номером карточки —
 * иначе кнопка висела бы в пустой строке и было бы неясно, что именно она уберёт.
 */
@Composable
private fun BreedCard(
    row: BreedUiState,
    number: Int,
    suggestions: List<String>,
    onChange: (BreedUiState) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.Surface,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            // Крестик стоит в собственной строке над полями, а не рядом с ними: в одном
            // ряду он забирал у «Породы» те же 40 dp, что и у «Яиц», и длинное название
            // упиралось в него на середине слова. Заголовок слева — номер строки, а не
            // ещё одна подпись поля: он говорит, к какой из карточек относится крестик.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Закладка $number",
                    style = DesignType.FieldLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Тот же крестик, что снимает напоминание ниже по форме, — в буквальном
                // смысле тот же composable: карточки стоят в одной форме друг под другом.
                SheetRemoveButton(onClick = onRemove, contentDescription = "Убрать породу")
            }

            // Строка заголовка высотой в цель для пальца уже несёт воздух под собой —
            // прежние 8 dp поверх него отодвинули бы поля от своей же подписи.
            FormSpacer(2.dp)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Порода", required = true)
                    // Тот же список, что у одиночного поля породы, — у каждой карточки
                    // свой: уже занятые другой карточкой породы из него убраны.
                    SuggestingSheetTextField(
                        value = row.name,
                        onValueChange = { onChange(row.copy(name = it)) },
                        placeholder = "Ломан Браун",
                        suggestions = suggestions,
                        capitalization = KeyboardCapitalization.Sentences,
                    )
                }
                Column(Modifier.width(BreedCountWidth)) {
                    FieldLabel(text = "Яиц", required = true)
                    SheetTextField(
                        value = row.eggs,
                        onValueChange = { onChange(row.copy(eggs = it.filter(Char::isDigit))) },
                        placeholder = "0",
                        numeric = true,
                        textAlign = TextAlign.Center,
                        horizontalPadding = 8.dp,
                    )
                }
            }

            FormSpacer(10.dp)
            val currency = LocalUnits.current.currency
            FieldLabel(text = "Стоимость, ${currency.symbol}")
            PriceRow(
                price = row.price,
                perUnit = row.pricePerEgg,
                perUnitLabel = "за яйцо",
                totalLabel = "за всё",
                onPriceChange = { onChange(row.copy(price = it.filter(Char::isDigit))) },
                onModeChange = { onChange(row.copy(pricePerEgg = it)) },
                // Ряд внутри карточки уже формы, и половина под переключатель обрезала
                // бы «за яйцо» — тот же случай, что и в диалоге завершения.
                evenSplit = false,
            )
        }
    }
}

/**
 * Подпись под карточками пород: сколько закладок сохранит форма и как они будут
 * называться. Названия печатаются заранее, а не после нажатия: хвост «— Хайсекс» к
 * названию — единственное, чего в самой форме не видно.
 */
private fun splitNotice(state: BatchUiState): String {
    val titles = state.breeds
        .filter { it.name.isNotBlank() }
        .map { splitBatchTitle(state.title, it.name) }
        .filter { it.isNotBlank() }
        .joinToString(", ") { "«$it»" }
    return "Каждая порода станет отдельной закладкой" +
        (if (titles.isNotEmpty()) " — $titles" else "") +
        ". Расписание, дата и напоминания у них общие."
}

/**
 * Заполнение инкубатора с этой закладкой — под полем «Количество яиц».
 *
 * Ровно та же [CapacityBlock], что на карточке инкубатора: «50 / 72 места», процент и
 * полоса, красные при переборе. Одна функция на оба места, чтобы форма обещала ровно
 * то, что карточка покажет после сохранения. Без вместимости блок не рисуется вовсе —
 * ни числа яиц, ни подписи: полоса без потолка ни на что не отвечает, а «укажите
 * вместимость» карточка уже говорит. В число входят и яйца, которые уже лежат в устройстве, — иначе полоса
 * отвечала бы «сколько яиц в этой закладке», а на это отвечает поле над ней.
 *
 * Подпись под полосой раскладывает сумму на слагаемые, когда в инкубаторе уже что-то
 * лежит: «50 / 72» над пустым полем без неё читалось бы как ошибка — человек ещё ничего
 * не ввёл. Пустому инкубатору подпись не нужна: число и есть эта закладка.
 */
@Composable
private fun IncubatorFill(occupied: Int, adding: Int, capacity: Int, split: Boolean) {
    CapacityBlock(eggs = occupied + adding, capacity = capacity)
    val caption = fillCaption(occupied, adding, split) ?: return
    Spacer(Modifier.height(4.dp))
    Text(
        text = caption,
        style = DesignType.Caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Подпись к полосе заполнения: сколько уже в инкубаторе и сколько станет с новыми
 * яйцами; `null` — инкубатор пуст, и раскладывать нечего.
 *
 * Пока поле яиц пустое, вторая половина не пишется: «с этой закладкой — 20» при тех же
 * двадцати, что уже лежат, читалось бы так, будто закладка ничего не добавляет.
 * [split] — лоток разбит по породам, и новых закладок будет несколько.
 */
internal fun fillCaption(occupied: Int, adding: Int, split: Boolean): String? {
    if (occupied <= 0) return null
    val already = plural(occupied, "яйцо", "яйца", "яиц") + " уже в инкубаторе"
    if (adding <= 0) return already
    val with = if (split) "с новыми закладками" else "с этой закладкой"
    return "$already, $with — " + formatCount(occupied + adding)
}

/** Ширина числового поля в карточке породы: три цифры с отступами, и не больше. */
private val BreedCountWidth = 76.dp

/**
 * Поле, которое показывает число, а не спрашивает его, — «Количество яиц», когда лоток
 * разбит по породам. Той же формы и высоты, что [SheetTextField], но на кремовой
 * подложке и с подписью справа: чтобы читалось как поле, из которого ввод убрали, а не
 * как поле, которое сломалось.
 */
@Composable
private fun DerivedCountField(value: String, hint: String) {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.SheetIconButton,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = value,
                style = DesignType.FieldValue,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = hint,
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Вид птицы в форме правки: поле, из которого выбор убрали, — та же кремовая подложка,
 * что у [DerivedCountField], с эмодзи и названием слева и сроком справа. Плитка вида без
 * соседок читалась бы как сетка, у которой не загрузились остальные; поле в ряду других
 * полей формы говорит «здесь значение, а не выбор». Срок пустой, если вида нет в
 * каталоге, — как у плитки.
 */
@Composable
private fun LockedSpeciesField(species: String, days: Int?) {
    DerivedCountField(
        value = "${speciesEmoji(species)}  $species",
        hint = days?.let { "$it дн." }.orEmpty(),
    )
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
        color = DesignPalette.Surface,
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
                SheetRemoveButton(
                    onClick = onRemove,
                    contentDescription = "Удалить напоминание",
                )
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

// --- Режим из архива -------------------------------------------------------------------------

/**
 * Предложение взять режим по дням из завершённой закладки того же вида.
 *
 * Макета у диалога нет — он сохраняет возможность, которая была в прежней двухшаговой
 * форме.
 *
 * Открывается он кнопкой над таблицей, а не сам собой при сохранении, как раньше.
 * Пока режим был не виден, спросить о нём можно было только в последний момент; теперь
 * таблица открыта, и подменять её за спиной у того, кто её только что правил, нельзя —
 * поэтому и выбор рядом с ней, и результат замены сразу видно.
 *
 * Выборов здесь два, и второй — главный. Сначала закладка, потом что из неё брать:
 * её план или среднее по её замерам ([ArchiveScheduleSource]). План — это цифры, с
 * которыми закладка начиналась и которые могли ни разу не правиться; замеры — то, каким
 * режим у этого инкубатора вышел на самом деле, и ради второго прошлые закладки обычно
 * и открывают.
 *
 * «Среднее по замерам» у закладки без единого замера недоступно, а не молча равно плану:
 * иначе две кнопки давали бы один и тот же результат, и разницу между ними нельзя было
 * бы увидеть. Выбор закладки без замеров сам возвращает переключатель к плану — иначе
 * подтверждение осталось бы стоять на недоступном варианте.
 */
@Composable
private fun ArchiveScheduleDialog(
    options: List<ArchiveOption>,
    onDismiss: () -> Unit,
    onUseArchive: (Long, ArchiveScheduleSource) -> Unit,
) {
    if (options.isEmpty()) return
    var selectedId by remember(options) { mutableLongStateOf(options.first().batch.id) }
    var source by remember(options) { mutableStateOf(ArchiveScheduleSource.Plan) }
    val selected = options.firstOrNull { it.batch.id == selectedId } ?: options.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Взять режим из архива?", style = DesignType.SectionTitle) },
        text = {
            // Диалог прокручивается: закладок одного вида может накопиться сколько
            // угодно, а список стоит над переключателем источника и кнопкой.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Температура, влажность, поворот и проветривание по дням " +
                        "заменятся значениями выбранной закладки — их будет видно " +
                        "в таблице.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(12.dp)
                DialogSubtitle("Из какой закладки")
                // Список — со своей прокруткой и потолком по высоте: второй выбор
                // здесь главный, а десяток закладок уносил его за нижний край диалога,
                // и о том, что режим можно взять по замерам, узнать было неоткуда.
                // Потолок не кратен строке намеренно: снизу видно половину следующей —
                // это и говорит, что список продолжается.
                Column(
                    modifier = Modifier
                        .heightIn(max = ArchiveListMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { option ->
                        ArchiveChoiceRow(
                            selected = option.batch.id == selectedId,
                            title = option.batch.title,
                            subtitle = archiveBatchCaption(option),
                            trailing = archiveBatchHatchRate(option.batch),
                            onClick = {
                                selectedId = option.batch.id
                                if (!option.hasMeasurements) source = ArchiveScheduleSource.Plan
                            },
                        )
                    }
                }

                FormSpacer(12.dp)
                DialogSubtitle("Что из неё взять")
                ArchiveChoiceRow(
                    selected = source == ArchiveScheduleSource.Plan,
                    title = "Режим той закладки",
                    subtitle = "Цифры, с которыми она начиналась",
                    onClick = { source = ArchiveScheduleSource.Plan },
                )
                ArchiveChoiceRow(
                    selected = source == ArchiveScheduleSource.Fact,
                    title = "Среднее по её замерам",
                    subtitle = if (selected.hasMeasurements) {
                        "Каким режим вышел на самом деле; дни без замеров останутся из режима"
                    } else {
                        "У этой закладки замеров нет"
                    },
                    enabled = selected.hasMeasurements,
                    onClick = { source = ArchiveScheduleSource.Fact },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onUseArchive(selectedId, source) }) {
                Text(text = "Заменить", color = DesignPalette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Отмена",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/** Подпись над группой переключателей: в диалоге их две, и они о разном. */
@Composable
private fun DialogSubtitle(text: String) {
    Text(
        text = text,
        style = DesignType.Micro,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

/**
 * Строка выбора: переключатель, название и пояснение под ним.
 *
 * Недоступная строка гасится целиком и перестаёт нажиматься — но остаётся на месте
 * вместе со своим пояснением: «замеров нет» объясняет, почему выбора нет, а исчезнувшая
 * строка не объяснила бы ничего.
 */
@Composable
private fun ArchiveChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: String? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = DesignPalette.Accent,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = subtitle,
                style = DesignType.Micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = DesignType.MonoEmphasis,
                color = DesignPalette.Accent.copy(alpha = alpha),
            )
        }
    }
}

/**
 * Вывод закладки в процентах — то, ради чего одну архивную закладку выбирают вместо
 * другой.
 *
 * Стоит справа в строке, а не в её подписи: проценты выстраиваются в столбец и
 * сравниваются взглядом, тогда как в конце разной длины подписей их пришлось бы
 * искать. Считается так же, как «средний вывод» на экране инкубатора — целочисленно,
 * от заложенных яиц.
 *
 * Ноль — это ответ, а не пропуск: досрочно завершённая закладка честно показывает 0 %,
 * и брать режим из неё вряд ли стоит. Нет процента только там, где нет знаменателя.
 */
private fun archiveBatchHatchRate(batch: Batch): String? =
    if (batch.eggAll > 0) "${batch.eggAllEND * 100 / batch.eggAll}%" else null

/**
 * Чем одна завершённая закладка отличается от другой в списке: когда её заложили,
 * сколько вывелось и есть ли у неё замеры.
 *
 * Названия у закладок сплошь и рядом одинаковые («Курицы», «Курицы 2»), а выбирают
 * из них ровно по этим трём вещам.
 */
private fun archiveBatchCaption(option: ArchiveOption): String {
    val parts = mutableListOf<String>()
    if (option.batch.data.isNotBlank()) parts += option.batch.data
    if (option.batch.eggAll > 0) {
        parts += "${option.batch.eggAllEND} из ${option.batch.eggAll}"
    }
    parts += if (option.hasMeasurements) "есть замеры" else "без замеров"
    return parts.joinToString(" · ")
}
