package ru.zaroslikov.incubator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType

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
internal val SheetPadding = 20.dp
internal val FieldRadius = 16.dp
internal val FieldHeight = 48.dp

@Composable
internal fun FormSpacer(height: Dp) {
    Box(Modifier.height(height))
}

/** «Ручка» шторки: 40×6, цвет рамки карточек. */
@Composable
internal fun SheetDragHandle() {
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
internal fun SheetHeader(title: String, onClose: () -> Unit) {
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
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DesignPalette.SheetIconButton),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_design),
                contentDescription = "Закрыть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun FieldLabel(text: String, required: Boolean = false) {
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
internal fun SheetTextField(
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
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = DesignPalette.Accent,
        unfocusedBorderColor = DesignPalette.CardBorder,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    )
    val textStyle = (if (numeric) DesignType.MonoField else DesignType.FieldValue)
        .copy(color = MaterialTheme.colorScheme.onSurface)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
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
                enabled = true,
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
                        enabled = true,
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
 * Поле-кнопка: выглядит как [SheetTextField], но значение выбирают в диалоге.
 *
 * Именно поле, а не `OutlinedTextField(readOnly = true)`: у readOnly-поля клик
 * съедается самим текстовым полем, и обходить это пришлось бы прозрачным слоем сверху.
 */
@Composable
internal fun SheetPickerField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = FieldHeight,
    radius: Dp = FieldRadius,
    textStyle: TextStyle = DesignType.FieldValue,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = Color.White,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = modifier.height(height),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
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
