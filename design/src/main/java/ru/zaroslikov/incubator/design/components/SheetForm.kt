package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.design.R
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Общие кирпичики форм в нижних шторках: инкубатора
 * ([9:2738](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=9-2738))
 * и закладки
 * ([12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555)).
 *
 * В макете обе шторки нарисованы одними и теми же элементами — «ручка», крестик,
 * подпись поля, белое поле со скруглением 16 — поэтому они живут здесь, а не
 * дублируются в каждом экране.
 */

/** Отступы формы из макета. */
val SheetPadding = 20.dp
val FieldRadius = 16.dp
val FieldHeight = 48.dp

/**
 * Ширина всплывающей подсказки у значка «i».
 *
 * Те же 280 dp, что у [TruncatedText], и по той же причине: материаловские 200 —
 * размер ярлыка в два слова, а здесь в подсказке лежит объяснение в две-три строки,
 * которое на них рассыпается в столбик.
 */
private val HintTooltipWidth = 280.dp

@Composable
fun FormSpacer(height: Dp) {
    Box(Modifier.height(height))
}

/** «Ручка» шторки: 40×6, цвет рамки карточек. */
@Composable
fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 6.dp)
                .clip(CircleShape)
                .background(DesignPalette.CardBorder)
        )
    }
}

/** Заголовок шторки с круглым крестиком справа. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = DesignType.SheetTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SheetCircleButton(onClick = onClose, contentDescription = "Закрыть")
    }
}

/**
 * Круглый крестик «убрать эту карточку» — породу в закладке, напоминание.
 *
 * Один composable на оба места: карточки стоят в одной форме друг под другом, и две
 * кнопки одного назначения разного размера читались бы как разные действия. Это тот же
 * [SheetCircleButton], что и крестик в заголовке, — так два круглых крестика одной
 * шторки одного размера. Центрированный в большей кнопке кружок заодно отходит от угла
 * карточки на половину разницы — вплотную к обводке он читался как приклеенный к ней.
 */
@Composable
fun SheetRemoveButton(onClick: () -> Unit, contentDescription: String) {
    SheetCircleButton(onClick = onClick, contentDescription = contentDescription)
}

/**
 * Круглая кнопка-крестик шторки: серый кружок [SheetCircleSize] с крестиком внутри
 * кнопки [SheetCircleTouchSize].
 *
 * Кружок рисуется внутри кнопки, а не кнопкой, и это не ради красоты. `IconButton`
 * ставит на себя `minimumInteractiveComponentSize()`, который возвращает 48 dp, какие бы
 * ограничения ни пришли снаружи, — а `clip` и `background`, повешенные на модификатор
 * кнопки, оказываются *снаружи* него и красятся по этим 48. Кнопка с `.size(36.dp)
 * .background(...)` поэтому рисовала кружок в 48 dp, вылезающий на 6 dp за отведённое
 * место с каждой стороны, и крестик в заголовке был заметно крупнее такого же в карточке
 * породы. Вложенный [Box] меряется по своему размеру, и кружок выходит ровно
 * [SheetCircleSize]; палец при этом бьёт по кнопке, так что размер рисунка ничего не
 * отнимает у попадания.
 */
@Composable
fun SheetCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(SheetCircleTouchSize)) {
        Box(
            modifier = Modifier
                .size(SheetCircleSize)
                .clip(CircleShape)
                .background(DesignPalette.SheetIconButton),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_design),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Видимый кружок [SheetCircleButton]: 36 dp, как в макете шторок. */
private val SheetCircleSize = 36.dp

/** Цель для пальца под тем кружком: её размер трогать нельзя, уменьшается только рисунок. */
private val SheetCircleTouchSize = 40.dp

@Composable
fun FieldLabel(text: String, required: Boolean = false) {
    Text(
        text = if (!required) buildAnnotatedString { append(text) } else buildAnnotatedString {
            append("$text ")
            withStyle(SpanStyle(color = DesignPalette.Required)) { append("*") }
        },
        style = DesignType.FieldLabel,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Подпись поля со значком подсказки: объяснение прячется за «i» и всплывает по нажатию.
 *
 * Пояснение в три строки, набранное [DesignType.Micro] прямо под подписью, читают один
 * раз, а занимает оно место всегда — и в форме, которую открывают, чтобы заложить яйца,
 * оно стоит ровно между двумя вопросами, на которые человек и пришёл ответить. Значок
 * отдаёт то же самое тому, кто спросил, и ничего не отнимает у того, кто не спрашивал.
 *
 * Жесты у подсказки — свои, как у [TruncatedText], и по тем же причинам: собственные
 * жесты [TooltipBox] сделаны под наведение мышью и долгое нажатие, о котором на телефоне
 * никто не догадается, поэтому `enableUserInput = false`, а показывает подсказку сам
 * обработчик. Видимость снимается в момент **нажатия** (`wasVisible`), пока подсказка
 * заведомо на экране: попап не перехватывает касания вне себя, и второе нажатие по
 * значку приходит сразу в два окна — спроси о видимости после, ответ зависел бы от того,
 * кто успел первым, и «показать / убрать» срабатывало бы через раз.
 *
 * `isPersistent = true`: полторы секунды, через которые гаснет обычная подсказка, — это
 * про ярлык в два слова у значка столбца ([ScheduleHeaderCell]), а не про абзац, который
 * читают. Гаснет она по нажатию мимо.
 *
 * Нажимается не сам значок в 16 dp, а круг в 32 вокруг него: промах по подсказке
 * выглядит поломкой ровно так же, как промах по кнопке.
 */
@Composable
fun FieldLabelWithHint(text: String, hint: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Text(
            text = text,
            style = DesignType.FieldLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HintIcon(hint = hint)
    }
}

/**
 * Значок «i» с подсказкой по нажатию — половина [FieldLabelWithHint], отданная наружу для
 * мест, где пояснение прячут не у подписи поля, а у заголовка карточки. Жесты и цвета те
 * же, и объяснены у [FieldLabelWithHint].
 *
 * [iconSize] — размер самого значка; круг нажатия всегда вдвое больше.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HintIcon(hint: String, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                maxWidth = HintTooltipWidth,
                shape = shape,
                containerColor = DesignPalette.Surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.border(0.8.dp, DesignPalette.CardBorder, shape),
            ) {
                Text(text = hint, style = DesignType.Body)
            }
        },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize * 2)
                .clip(CircleShape)
                .semantics {
                    onClick(label = "Показать подсказку") {
                        scope.launch { tooltipState.show() }
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val wasVisible = tooltipState.isVisible
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            ?: return@awaitEachGesture
                        up.consume()
                        scope.launch {
                            if (wasVisible) tooltipState.dismiss() else tooltipState.show()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Подсказка",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Поле ввода из макета: белое, скругление 16, высота 48.
 *
 * Собрано из [BasicTextField] и [OutlinedTextFieldDefaults.DecorationBox], а не из
 * готового `OutlinedTextField`: тот жёстко ставит себе `defaultMinSize(minHeight =
 * OutlinedTextFieldDefaults.MinHeight)`, то есть 56 dp, и `heightIn` его не уменьшает —
 * поле оказывалось выше соседей ([SheetPickerField], переключатель «за яйцо / за всё»),
 * которые нарисованы ровно на 48. Отступы по вертикали здесь нулевые: высоту задаёт
 * `heightIn`, а не padding, поэтому при крупном системном шрифте поле растёт, а не режет
 * текст.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minHeight: Dp = FieldHeight,
    radius: Dp = FieldRadius,
    horizontalPadding: Dp = 16.dp,
    // Многострочному полю отступы нужны: высоту ему задаёт уже не только `heightIn`.
    verticalPadding: Dp = if (singleLine) 0.dp else 12.dp,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    // Выравнивание значения и подсказки внутри поля. По умолчанию — по левому краю, как
    // во всей форме; клетка таблицы расписания просит середины: столбец там уже самого
    // значения, и прижатые влево «37.8» и «2×15» читаются рваным краем, а не колонкой.
    textAlign: TextAlign = TextAlign.Start,
    trailingIcon: @Composable (() -> Unit)? = null,
    // Поле только для чтения: ввода нет, клавиатуры нет, фокуса нет — но подсказка
    // видна. Так выглядит клетка расписания, отданная автоматике инкубатора: значение
    // там не стёрли, его там не ставят («Авто»), и поле об этом говорит само, а не
    // молча пропускает нажатия. Кремовая заливка — та же, что у выведенных полей формы
    // («Количество яиц» при делении лотка по породам): одним цветом сказано «это не
    // вводят».
    enabled: Boolean = true,
    // Заливка живого поля вместо белой. Так клетка таблицы расписания говорит цветом,
    // насколько её план расходится с планом закладок, уже идущих в приборе в тот же
    // день: рамка и текст остаются свои, меняется только фон — поле по-прежнему
    // вводят. `null` — обычное белое поле.
    containerColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = containerColor ?: DesignPalette.Surface,
        unfocusedContainerColor = containerColor ?: DesignPalette.Surface,
        focusedBorderColor = DesignPalette.Accent,
        unfocusedBorderColor = DesignPalette.CardBorder,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        // Выключенное поле не гаснет, а меняет смысл: заливка кремовая, как у
        // выведенных полей, рамка и текст — свои же. Материаловские «disabled» цвета
        // здесь были бы неправдой: значение в поле верное и читать его надо, просто
        // ставит его не человек.
        disabledContainerColor = DesignPalette.SheetIconButton,
        disabledBorderColor = DesignPalette.CardBorder,
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val textStyle = (if (numeric) DesignType.MonoField else DesignType.FieldValue)
        .copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        cursorBrush = SolidColor(DesignPalette.Accent),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // В половинном поле со стрелкой подсказка иначе переносится на
                        // вторую строку, и поле становится выше соседнего — в макете они равны.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Ширину подсказке надо задать явно: сама по себе она занимает
                        // ровно свои символы, и центрировать внутри было бы нечего.
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                trailingIcon = trailingIcon,
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = verticalPadding,
                    bottom = verticalPadding,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = RoundedCornerShape(radius),
                        focusedBorderThickness = 1.2.dp,
                        unfocusedBorderThickness = 0.8.dp,
                    )
                },
            )
        },
    )
}

/**
 * Поле с выпадающим списком уже введённых значений — бренд и модель инкубатора, порода
 * в закладке.
 *
 * Ввод остаётся свободным — новый бренд или новую породу никто не запрещает; список лишь
 * избавляет от перенабора и от «ломан браун» рядом с «Ломан Браун», которых статистика
 * посчитала бы за две породы. Стрелка справа появляется только когда есть что показать:
 * иначе она обещала бы список, которого нет.
 *
 * Пока значение набирают, список сужается до пунктов, в которых набранное встречается.
 * Когда в поле стоит один из пунктов целиком — только что выбранный из списка, — список
 * показывает все остальные: промах по пункту исправляется вторым выбором, а не стиранием
 * поля. Совпадение без учёта регистра и краёв, как и в подсказках самих.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestingSheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<String>,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        val typed = value.trim()
        val picked = suggestions.any { it.equals(typed, ignoreCase = true) }
        suggestions.filter { option ->
            !option.equals(typed, ignoreCase = true) &&
                (picked || option.contains(typed, ignoreCase = true))
        }
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
            capitalization = capitalization,
            trailingIcon = if (suggestions.isEmpty()) null else {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && matches.isNotEmpty()) }
            },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            containerColor = DesignPalette.Surface,
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

/**
 * Поле-кнопка: выглядит как [SheetTextField], но значение выбирают в диалоге.
 *
 * Именно поле, а не `OutlinedTextField(readOnly = true)`: у readOnly-поля клик
 * съедается самим текстовым полем, и обходить это пришлось бы прозрачным слоем сверху.
 *
 * Нажимается поле целиком, а не только надпись в нём: ряд внутри растянут по ширине
 * рамки. Без этого поле без [trailing] отдавало бы под нажатие лишь ширину значения —
 * у «08:00» это треть рамки, и промах по пустому месту справа выглядел бы поломкой.
 *
 * [enabled] = `false` — значение показано, но выбрать другое нельзя. Поле тогда
 * кремовое, как запертые клетки «Авто» и производные поля форм, а не серое
 * Material-«недоступно»: значение не потеряно, оно просто уже не правится.
 */
@Composable
fun SheetPickerField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = FieldHeight,
    radius: Dp = FieldRadius,
    textStyle: TextStyle = DesignType.FieldValue,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = if (enabled) DesignPalette.Surface else DesignPalette.SheetIconButton,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = modifier.height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value.ifBlank { placeholder },
                style = textStyle,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = trailing != null),
            )
            trailing?.invoke()
        }
    }
}

/**
 * Поле с выпадающим списком «один из»: значение выбирают из [options], набрать своё нельзя.
 *
 * Пара к [SuggestingSheetTextField], у которого ввод свободен, а список только подсказка.
 * Здесь наоборот: перечень закрыт — валюта в «Настройках», — и поле-кнопка
 * ([SheetPickerField]) со стрелкой честнее текстового: клавиатуре в нём нечего делать.
 * Список рисуется тем же `ExposedDropdownMenu`, что и у подсказок, на той же подложке.
 *
 * Выбранный пункт помечен акцентом, а не галочкой: список короткий, и цвет читается
 * быстрее значка, которого больше нигде в меню приложения нет.
 *
 * [placeholder] — для списка, где выбора ещё может не быть («Взять за основу» в
 * конструкторе вида): поле показывает его, пока `label(selected)` пуст.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SheetDropdownField(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        // Открывает и закрывает само поле-кнопка ниже: якорь тоже ловит нажатие, и два
        // переключателя на один тап гасили друг друга — список не открывался вовсе.
        onExpandedChange = {},
        modifier = modifier,
    ) {
        SheetPickerField(
            value = label(selected),
            placeholder = placeholder,
            onClick = { expanded = !expanded },
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DesignPalette.Surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(option),
                            style = DesignType.FieldValue,
                            color = if (option == selected) DesignPalette.Accent
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// --- Стоимость --------------------------------------------------------------------------------

/**
 * Поле цены и переключатель «за единицу / за всё» рядом с ним — из формы закладки
 * (макет 12:3555).
 *
 * Здесь, а не в `AddBatchSheet` (в `:app`), потому что диалог
 * завершения инкубации спрашивает цену птенцов ровно тем же способом. Различаются
 * только подписи, поэтому они и параметры: «за яйцо / за всё» против «за птенца / за
 * всех».
 *
 * Поле и переключатель делят строку поровну — `weight(1f)` у каждого, — и две кнопки
 * внутри переключателя делят его пополам тем же способом. Без этого ширину задавали бы
 * сами подписи: «за яйцо» шире «за всё», а весь переключатель заметно уже поля слева, и
 * ряд выходил кривым. Высота у обоих ровно [FieldHeight], как в макете.
 *
 * [evenSplit] выключает ровно эту половину — и нужен ровно одному месту, диалогу
 * завершения. Там строка вдвое короче формы, а подписи длиннее («за птенца» против «за
 * яйцо»), и на половине диалога «за птенца» превращалось в «за пте…». Переключатель
 * там остаётся по ширине подписей, но кнопки внутри всё равно равны друг другу —
 * `IntrinsicSize.Max` даёт обеим ширину более длинной.
 */
@Composable
fun PriceRow(
    price: String,
    perUnit: Boolean,
    perUnitLabel: String,
    totalLabel: String,
    onPriceChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    evenSplit: Boolean = true,
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
        Surface(
            shape = RoundedCornerShape(FieldRadius),
            color = DesignPalette.Surface,
            border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
            modifier = (if (evenSplit) Modifier.weight(1f) else Modifier)
                .height(FieldHeight),
        ) {
            Row(
                modifier = (if (evenSplit) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
                    .padding(4.dp)
            ) {
                PriceModeButton(perUnitLabel, perUnit, Modifier.weight(1f)) { onModeChange(true) }
                PriceModeButton(totalLabel, !perUnit, Modifier.weight(1f)) { onModeChange(false) }
            }
        }
    }
}

@Composable
private fun PriceModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
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
            color = if (selected) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Плитка-итог под полем цены: подпись обычным шрифтом, значение — моноширинным.
 *
 * Пересчёт «за всё → за единицу» всюду делится нацело: копеек в базе нет, и дробная
 * цена там, где всё остальное в целых рублях, соврала бы о точности.
 */
@Composable
fun PriceSummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
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

// --- Главная кнопка ---------------------------------------------------------------------------

/**
 * Цвета главной — акцентной — кнопки: «Создать инкубатор», «Заложить яйца», «Записать
 * замер». Одна функция на три места, потому что различаются они только надписью.
 *
 * **Гаснет заливка, а не надпись.** В макете неактивная кнопка — тот же зелёный с
 * прозрачностью 40 %, и надпись на нём оставалась белой: контраст меньше двух к одному,
 * то есть видно ровно настолько, чтобы понять, что там что-то написано. Читается это не
 * как «выключено», а как кнопка, которая не дорисовалась, — тем более что «Записать
 * замер» и «Заложить яйца» неактивны как раз в тот момент, когда на экран смотрят
 * впервые. Поэтому заливка бледнеет дальше, до намёка на зелёный, а надпись берёт
 * акцент целиком: её видно в обеих темах, а то, что нажимать пока нечего, говорит сама
 * кнопка, а не расплывшийся текст на ней.
 */
@Composable
fun accentButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = DesignPalette.Accent,
    contentColor = DesignPalette.OnAccent,
    disabledContainerColor = DesignPalette.Accent.copy(alpha = 0.16f),
    disabledContentColor = DesignPalette.Accent,
)

/**
 * Цвета выключателя — «Автопереворот» в форме инкубатора и «Напоминания» в настройках.
 *
 * **Выключенный кружок — не белый.** Он и был белым, в обоих местах, а лежит такой
 * выключатель на белой карточке, поверх кремовой дорожки: кружка не видно вовсе, и
 * выключенный переключатель читается как пустая таблетка, о которой нельзя сказать,
 * в каком она положении. В тёмной теме выходило наоборот и хуже — белый кружок на
 * тёмной дорожке ярче включённого, то есть «выключено» выглядело активнее «включено».
 * Теперь кружок берёт цвет приглушённого текста, как у Material: на кремовом он тёмный,
 * на тёмном — светло-серый, и в обеих темах положение видно с одного взгляда.
 *
 * Включённый кружок — [DesignColors.OnAccent], по общему правилу темы: то, что стоит на
 * акценте, красится им. В светлой это тот же белый, что и был; в тёмной — тёмно-зелёный,
 * потому что акцент там светлый и белое на нём терялось.
 */
@Composable
fun accentSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = DesignPalette.OnAccent,
    checkedTrackColor = DesignPalette.Accent,
    checkedBorderColor = DesignPalette.Accent,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = DesignPalette.ProgressTrack,
    uncheckedBorderColor = DesignPalette.CardBorder,
)

/**
 * Строка-выключатель: белая карточка с обводкой, подпись слева, [Switch] справа.
 *
 * Жила приватной в форме инкубатора, пока такие же две строки не понадобились форме
 * закладки: автоматика у закладки — это те же «Автопереворот» и «Автопроветривание»,
 * только про один лоток, и выглядеть они обязаны одинаково — один и тот же вопрос,
 * заданный на двух экранах по-разному, читается как два разных вопроса.
 *
 * **Отступ по вертикали 4 dp, а не 12, и высоту строки задаёт не он.** [Switch] несёт
 * на себе обязательные 48 dp зоны нажатия, так что карточка и при нулевом отступе не
 * станет ниже поля ввода рядом (`FieldHeight`); двенадцать сверх этого делали её в
 * полтора раза выше всего, что стоит в форме, — два таких выключателя подряд занимали
 * экран, ничего о себе не добавляя.
 */
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(FieldRadius),
        color = DesignPalette.Surface,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                colors = accentSwitchColors(),
            )
        }
    }
}

// --- Футер сохранения -------------------------------------------------------------------------

/**
 * Кнопка сохранения внизу шторки — одна на всё её содержимое.
 *
 * Не в конце прокрутки, а под ней: в форме закладки страниц две, и уводить кнопку
 * из-под пальца на второй из них было бы обманом — таблица тем же нажатием и
 * сохраняется. У конструктора своего вида причина та же: строк в таблице бывает
 * тридцать, и докручивать до кнопки, чтобы сохранить отмеченный день, никто не станет.
 * Волосяная линия сверху нужна ровно затем, чтобы уезжающие под кнопку строки не
 * читались как обрезанные.
 *
 * Прячется он не сам: обе шторки обходят его при поднятой клавиатуре
 * (`WindowInsets.isImeVisible`), потому что окно шторки при вводе сжимается до полосы
 * над клавиатурой, и высоту в ней держит всё, что стоит вне прокручиваемой части.
 */
@Composable
fun SheetSaveFooter(label: String, enabled: Boolean, onClick: () -> Unit) {
    HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
    Column(
        modifier = Modifier
            .padding(horizontal = SheetPadding)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(FieldRadius),
            colors = accentButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = label, style = DesignType.ButtonLabel)
        }
    }
}
