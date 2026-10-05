package ru.zaroslikov.incubator.ui.species

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.components.CellGap
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.ui.components.SchedulePadding
import ru.zaroslikov.incubator.ui.components.ScheduleActionButton
import ru.zaroslikov.incubator.ui.components.ScheduleHeaderRow
import ru.zaroslikov.incubator.ui.components.ScheduleRow
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetDropdownField
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetSaveFooter
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Конструктор своего вида птицы в нижней шторке. Макета нет: собран из тех же кирпичей, что форма
 * закладки ([12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555)).
 *
 * Спрашивает то, что знает про птицу :domain: имя, режим по дням, дни овоскопирования. **Срока
 * отдельным полем нет** — это число строк таблицы. Таблица — та же [ScheduleRow], что в форме
 * закладки, с шестым столбцом (`ui/components/ScheduleTable.kt`).
 *
 * @param speciesId ноль — создание, иначе правка существующего вида.
 * @param draft черновик формы (см. [SheetDraft]).
 * @param onSaved сохранённый вид с идентификатором из базы — чтобы вызвавший экран мог выбрать его.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomSpeciesSheet(
    speciesId: Long = 0,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    onSaved: (CustomSpecies) -> Unit,
    viewModel: CustomSpeciesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state.isEditing

    // Заново форма заполняется только с отменённым черновиком: свёрнутую шторку
    // открывают ровно тем, что в ней набрали, — см. [SheetDraft].
    LaunchedEffect(speciesId) {
        if (draft.claim(speciesId)) viewModel.onIntent(CustomSpeciesIntent.Load(speciesId))
    }

    // «Сохранено» — эффект, а не callback у кнопки: вид уезжает наружу один раз, ровно
    // тогда, когда база ответила идентификатором.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is CustomSpeciesEffect.Saved -> {
                draft.discard()
                onSaved(effect.species)
            }
        }
    }

    // Крестик — отказ от ввода, в отличие от свайпа вниз, которым шторку сворачивают.
    val close = {
        draft.discard()
        onDismiss()
    }

    // Событие своё, новое: прежних экранов у конструктора не было, переносить имя
    // неоткуда. Отчитывается шторка, а не ViewModel, — как и в форме закладки.
    fun report() {
        val parameters: MutableMap<String, Any> = HashMap()
        parameters["Имя"] = state.name
        parameters["Дней"] = state.rows.size
        parameters["Овоскопирований"] = state.rows.count { it.candling }
        Analytics.report(Events.CUSTOM_SPECIES_SAVED, parameters)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Шторка на всю высоту: прокручивается только середина, заголовок и кнопка
        // стоят на месте. Дней бывает тридцать, и уезжающая кнопка «Сохранить вид»
        // означала бы, что отмеченный день сохраняют вслепую.
        Column(modifier = Modifier.fillMaxHeight().clearFocusOnTap()) {
            Column(modifier = Modifier.padding(horizontal = SheetPadding)) {
                SheetHeader(
                    title = if (editing) "Свой вид" else "Новый вид",
                    onClose = close,
                )
            }

            if (state.loading) {
                // При правке поля и строки приезжают из базы не сразу, и пустая форма
                // над существующим видом читается как потерянный вид.
                LoadingBox(Modifier.weight(1f))
            } else {
                SpeciesForm(
                    state = state,
                    send = viewModel::onIntent,
                    modifier = Modifier.weight(1f),
                )
            }

            // Пока открыта клавиатура, кнопки нет — ровно как в форме закладки: окно
            // шторки при вводе сжимается до полосы над клавиатурой, и всё, что стоит
            // вне прокрутки, отнимается у неё.
            if (!WindowInsets.isImeVisible) {
                SheetSaveFooter(
                    label = "Сохранить вид",
                    enabled = state.isValid && !state.saving,
                    onClick = {
                        report()
                        viewModel.onIntent(CustomSpeciesIntent.Save)
                    },
                )
            }
        }
    }
}

/**
 * Поля и таблица одной прокруткой — [LazyColumn]: строк бывает под сорок, а вложенная прокрутка даёт
 * две полосы. Отступы разные намеренно: у полей [SheetPadding], у таблицы уже [SchedulePadding] —
 * на 360 dp шесть столбцов иначе не вмещают «37.8».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesForm(
    state: CustomSpeciesState,
    send: (CustomSpeciesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = state.rows
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        item(key = "fields") {
            Column(modifier = Modifier.padding(horizontal = SheetPadding)) {
                FormSpacer(20.dp)
                FieldLabel(text = "Название вида", required = true)
                SheetTextField(
                    value = state.name,
                    onValueChange = { send(CustomSpeciesIntent.UpdateName(it)) },
                    placeholder = "Например, Цесарки",
                    capitalization = KeyboardCapitalization.Sentences,
                )
                // Ошибка стоит под полем и появляется по мере набора: имя вида — это
                // то, чем на него ссылается закладка, и узнать о занятом имени в
                // момент нажатия «Сохранить» значило бы переписывать таблицу заново.
                state.nameError?.let { error ->
                    FormSpacer(4.dp)
                    Text(
                        text = error,
                        style = DesignType.Micro,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!state.isEditing) {
                    FormSpacer(20.dp)
                    FieldLabel(text = "Взять за основу")
                    // Выпадающий список, а не пилюли: пять названий пилюлями занимали
                    // две-три строки над таблицей ради выбора, который делают один раз.
                    // Повторный выбор того же вида снова подменяет таблицу — это и есть
                    // «вернуть режим как было» после неудачных правок.
                    SheetDropdownField(
                        options = SpeciesCatalog.BUILT_IN,
                        selected = state.seededFrom,
                        label = { it.orEmpty() },
                        onSelect = { name -> name?.let { send(CustomSpeciesIntent.SeedFrom(it)) } },
                        placeholder = "Не выбрано",
                    )
                    FormSpacer(6.dp)
                    Text(
                        text = "Режим встроенного вида скопируется в таблицу — дальше " +
                            "правьте под свою птицу.",
                        style = DesignType.Note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FormSpacer(20.dp)
                ConstructorHint()

                FormSpacer(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleActionButton(
                        text = "Добавить день",
                        onClick = { send(CustomSpeciesIntent.AddDay) },
                        modifier = Modifier.weight(1f),
                        enabled = state.canAddDay,
                    )
                    // Кнопка не исчезает на единственном дне, а гаснет: пропавшая
                    // кнопка не объясняет, почему день не убрать, а рядом стоящая
                    // «Добавить день» осталась бы одна посреди строки.
                    ScheduleActionButton(
                        text = "Убрать последний",
                        onClick = { send(CustomSpeciesIntent.RemoveLastDay) },
                        modifier = Modifier.weight(1f),
                        enabled = rows.size > 1,
                    )
                }

                FormSpacer(10.dp)
                Text(
                    text = state.summary,
                    style = DesignType.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormSpacer(12.dp)
            }
        }

        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = SchedulePadding)) {
                ScheduleHeaderRow(candling = true)
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                FormSpacer(4.dp)
            }
        }

        // Без ключей намеренно, как и в форме закладки: список не переупорядочивается,
        // весь ввод живёт во ViewModel, и позиции хватает.
        itemsIndexed(rows) { index, row ->
            Column(modifier = Modifier.padding(horizontal = SchedulePadding)) {
                ScheduleRow(
                    row = row.value,
                    onChange = { send(CustomSpeciesIntent.UpdateRow(index, it)) },
                    candling = row.candling,
                    onCandlingToggle = { send(CustomSpeciesIntent.ToggleCandling(index)) },
                )
            }
        }
    }
}

/**
 * Подсказка над таблицей — как `ScheduleDisclaimer` в форме закладки, но о другом.
 *
 * Там предупреждают, что справочные цифры надо сверять со своим инкубатором; здесь
 * цифры пишет сам пользователь, и сказать нужно то, чего по таблице не видно: где
 * отмечают овоскопирование и откуда берётся срок инкубации. Второе — единственное
 * место, где об этом вообще говорится: поля «срок» в форме нет.
 */
@Composable
private fun ConstructorHint() {
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
                text = "Отметьте дни овоскопирования в последнем столбце. Срок " +
                    "инкубации — это число дней в таблице.",
                style = DesignType.Note,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
