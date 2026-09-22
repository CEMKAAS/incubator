package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.FormLoaderHeight
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.SuggestingSheetTextField
import ru.zaroslikov.incubator.design.components.ToggleRow
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Форма инкубатора в нижней шторке — макет
 * [9:2738](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=9-2738).
 *
 * Шторка, а не отдельный экран: так задумано в макете — скруглённый верх, «ручка»
 * и крестик вместо стрелки назад. Поэтому её открывает тот экран, поверх которого
 * она появляется, а маршрута в навигации у неё нет.
 *
 * @param incubatorId ноль — создание, иначе правка существующего.
 * @param draft черновик формы: свёрнутую шторку открывают тем же вводом, закрытую
 *   крестиком — пустой. См. [SheetDraft].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncubatorSheet(
    incubatorId: Long,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddIncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val state = uiState.form
    val editing = uiState.isEditing
    val update = { form: IncubatorFormUiState -> viewModel.onIntent(AddIncubatorIntent.Update(form)) }

    // Форма заполняется заново только тогда, когда прошлый черновик отменён: свёрнутую
    // шторку открывают ровно тем, что в ней набрали.
    LaunchedEffect(incubatorId) {
        if (draft.claim(incubatorId)) viewModel.onIntent(AddIncubatorIntent.Load(incubatorId))
    }

    // «Сохранено» — эффект, а не callback: шторка закрывается один раз, ровно тогда,
    // когда запись состоялась, и не зависит от того, какая лямбда стояла в кнопке.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is AddIncubatorEffect.Saved -> {
                draft.discard()
                onSaved(effect.id)
            }
        }
    }

    // Крестик — отказ от ввода, в отличие от свайпа вниз, которым шторку сворачивают
    // (в том числе случайно, прокручивая форму).
    val close = {
        draft.discard()
        onDismiss()
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
                .fillMaxHeight()
                .clearFocusOnTap()
                .padding(horizontal = SheetPadding)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SheetHeader(
                title = if (editing) "Инкубатор" else "Новый инкубатор",
                onClose = close,
            )

            // Правка открывается пустой формой, пока инкубатор читается из базы, и
            // пустые поля над существующим устройством — это утверждение, что у него
            // нет ни названия, ни модели, ни цены. При создании читать нечего, и
            // колеса тут не бывает.
            if (uiState.loading) {
                LoadingBox(Modifier.height(FormLoaderHeight))
                return@Column
            }

            FormSpacer(20.dp)

            FieldLabel(text = "Название", required = true)
            SheetTextField(
                value = state.name,
                onValueChange = { update(state.copy(name = it)) },
                placeholder = uiState.nextNumber
                    ?.takeUnless { editing }
                    ?.let { "Инкубатор $it" }
                    ?: "Инкубатор",
                imeAction = ImeAction.Next,
            )

            FormSpacer(16.dp)
            Text(
                text = "Остальные параметры необязательны — заполните, что знаете.",
                style = DesignType.Note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FormSpacer(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Бренд")
                    SuggestingSheetTextField(
                        value = state.brand,
                        onValueChange = { update(state.copy(brand = it)) },
                        placeholder = "—",
                        suggestions = uiState.usedBrands,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Модель")
                    SuggestingSheetTextField(
                        value = state.model,
                        onValueChange = { update(state.copy(model = it)) },
                        placeholder = "—",
                        suggestions = uiState.usedModels,
                    )
                }
            }

            FormSpacer(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Вместимость (яиц)")
                    SheetTextField(
                        value = state.capacity,
                        onValueChange = {
                            update(state.copy(capacity = it.filter(Char::isDigit)))
                        },
                        // Прочерк, как у «Стоимости» рядом и у «Количества яиц» в форме
                        // закладки: вместимость написана на самом инкубаторе, придумывать
                        // за человека число незачем, а «48» читалось как уже введённое.
                        placeholder = "—",
                        numeric = true,
                        imeAction = ImeAction.Next,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Стоимость, ${LocalUnits.current.currency.symbol}")
                    SheetTextField(
                        value = state.price,
                        onValueChange = {
                            update(state.copy(price = it.filter(Char::isDigit)))
                        },
                        placeholder = "—",
                        numeric = true,
                        imeAction = ImeAction.Next,
                    )
                }
            }

            FormSpacer(16.dp)
            ToggleRow(
                title = "Автопереворот",
                checked = state.autoTurn,
                onCheckedChange = { update(state.copy(autoTurn = it)) },
            )
            FormSpacer(8.dp)
            ToggleRow(
                title = "Автопроветривание",
                checked = state.autoAiring,
                onCheckedChange = { update(state.copy(autoAiring = it)) },
            )

            FormSpacer(16.dp)
            FieldLabel(text = "Заметки")
            SheetTextField(
                value = state.note,
                onValueChange = { update(state.copy(note = it)) },
                placeholder = "Особенности, режим, где стоит…",
                minHeight = 80.dp,
                singleLine = false,
                imeAction = ImeAction.Default,
            )

            FormSpacer(24.dp)
            Button(
                onClick = { viewModel.onIntent(AddIncubatorIntent.Save) },
                enabled = uiState.isValid,
                shape = RoundedCornerShape(FieldRadius),
                colors = accentButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (editing) "Сохранить" else "Создать инкубатор",
                    style = DesignType.ButtonLabel,
                )
            }
        }
    }
}
