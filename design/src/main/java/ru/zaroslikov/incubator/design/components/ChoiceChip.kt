package ru.zaroslikov.incubator.design.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/** Высота чипа выбора — фильтра над закладками и плиток единиц в «Настройках». */
private val ChoiceChipHeight = 34.dp

private const val ChoiceChipFadeMillis = 180

/**
 * Чип «один из нескольких»: белый с обводкой, как карточки, и зелёный, когда выбран.
 *
 * Родился как фильтр над списком закладок инкубатора и уехал сюда, когда тот же выбор
 * понадобился «Настройкам» — градусы и валюта. Один чип на оба места, а не копия:
 * два способа сказать «выбрано» в одном приложении читались бы как два разных действия.
 * Значок необязателен — у фильтра он есть только у «Архива».
 *
 * **Подпись всегда в одну строку**, а не влезла — кончается многоточием: высота чипа
 * фиксирована, и перенесённая вторая строка просто обрезалась бы по краю «таблетки». Чтобы
 * многоточию было где появиться, ряд, в котором стоят чипы, отдаёт им ширину через
 * [modifier] — например `Modifier.weight(1f, fill = false)`.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val shape = RoundedCornerShape(50)
    // Выбор перетекает из чипа в чип, а не перескакивает: заливка, обводка и подпись меняют
    // цвет за те же 180 мс, за которые соседние блоки раскрываются.
    val content by animateColorAsState(
        if (selected) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(ChoiceChipFadeMillis),
        label = "chipContent",
    )
    val fill by animateColorAsState(
        if (selected) DesignPalette.Accent else DesignPalette.Surface,
        tween(ChoiceChipFadeMillis),
        label = "chipFill",
    )
    val border by animateColorAsState(
        if (selected) DesignPalette.Accent else DesignPalette.CardBorder,
        tween(ChoiceChipFadeMillis),
        label = "chipBorder",
    )
    Row(
        modifier = modifier
            .height(ChoiceChipHeight)
            .clip(shape)
            .background(fill)
            .border(0.8.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        // По центру — для чипа, растянутого весом (режимы QR-кода); чипу по размеру
        // содержимого это ничего не меняет.
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = text,
            style = DesignType.CaptionEmphasis,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
