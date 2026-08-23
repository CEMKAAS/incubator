package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.components.FieldLabel
import ru.zaroslikov.incubator.ui.components.FieldRadius
import ru.zaroslikov.incubator.ui.components.FormSpacer
import ru.zaroslikov.incubator.ui.components.SheetDragHandle
import ru.zaroslikov.incubator.ui.components.SheetHeader
import ru.zaroslikov.incubator.ui.components.SheetPadding
import ru.zaroslikov.incubator.ui.components.SheetTextField
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType

/**
 * Форма инкубатора в нижней шторке — макет
 * [9:2738](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=9-2738).
 *
 * Шторка, а не отдельный экран: так задумано в макете — скруглённый верх, «ручка»
 * и крестик вместо стрелки назад. Поэтому её открывает тот экран, поверх которого
 * она появляется, а маршрута в навигации у неё нет.
 *
 * @param incubatorId ноль — создание, иначе правка существующего.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncubatorSheet(
    incubatorId: Long,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddIncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = viewModel.uiState
    val usedBrands by viewModel.usedBrands.collectAsState()
    val usedModels by viewModel.usedModels.collectAsState()

    // Шторка живёт в композиции только пока открыта, поэтому форма заполняется заново
    // при каждом показе и не тащит за собой прошлый ввод.
    LaunchedEffect(incubatorId) { viewModel.load(incubatorId) }

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
                title = if (viewModel.isEditing) "Инкубатор" else "Новый инкубатор",
                onClose = onDismiss,
            )

            FormSpacer(20.dp)

            FieldLabel(text = "Название", required = true)
            SheetTextField(
                value = state.name,
                onValueChange = { viewModel.update(state.copy(name = it)) },
                placeholder = "Например, Ферма «Заря»",
                imeAction = ImeAction.Next,
            )

            FormSpacer(16.dp)
            Text(
                text = "Остальные параметры необязательны — заполните, что знаете.",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FormSpacer(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Бренд")
                    SuggestingSheetTextField(
                        value = state.brand,
                        onValueChange = { viewModel.update(state.copy(brand = it)) },
                        placeholder = "Rcom, Блиц…",
                        suggestions = usedBrands,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Модель")
                    SuggestingSheetTextField(
                        value = state.model,
                        onValueChange = { viewModel.update(state.copy(model = it)) },
                        placeholder = "72 Turbo",
                        suggestions = usedModels,
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
                            viewModel.update(state.copy(capacity = it.filter(Char::isDigit)))
                        },
                        placeholder = "48",
                        numeric = true,
                        imeAction = ImeAction.Next,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(text = "Стоимость, ₽")
                    SheetTextField(
                        value = state.price,
                        onValueChange = {
                            viewModel.update(state.copy(price = it.filter(Char::isDigit)))
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
                onCheckedChange = { viewModel.update(state.copy(autoTurn = it)) },
            )
            FormSpacer(8.dp)
            ToggleRow(
                title = "Автопроветривание",
                checked = state.autoAiring,
                onCheckedChange = { viewModel.update(state.copy(autoAiring = it)) },
            )

            FormSpacer(16.dp)
            FieldLabel(text = "Заметки")
            SheetTextField(
                value = state.note,
                onValueChange = { viewModel.update(state.copy(note = it)) },
                placeholder = "Особенности, режим, где стоит…",
                minHeight = 80.dp,
                singleLine = false,
                imeAction = ImeAction.Default,
            )

            FormSpacer(24.dp)
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = viewModel.isValid,
                shape = RoundedCornerShape(FieldRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = Color.White,
                    // В макете неактивная кнопка — тот же зелёный с прозрачностью 40 %.
                    disabledContainerColor = DesignPalette.Accent.copy(alpha = 0.4f),
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (viewModel.isEditing) "Сохранить" else "Создать инкубатор",
                    style = DesignType.ButtonLabel,
                )
            }
        }
    }
}

/**
 * Поле с подсказками из уже введённых значений.
 *
 * Ввод остаётся свободным — новый бренд никто не запрещает; список лишь избавляет от
 * перенабора. Стрелка справа появляется только когда есть что показать: иначе она
 * обещала бы список, которого нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestingSheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { if (suggestions.isNotEmpty()) expanded = it },
    ) {
        SheetTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            placeholder = placeholder,
            trailingIcon = if (suggestions.isEmpty()) null else {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && matches.isNotEmpty()) }
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = suggestion,
                            style = DesignType.FieldValue,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = Color.White,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = DesignType.ToggleLabel,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = DesignPalette.Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = DesignPalette.CardBorder,
                    uncheckedBorderColor = DesignPalette.CardBorder,
                ),
            )
        }
    }
}
