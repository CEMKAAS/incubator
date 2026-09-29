package ru.zaroslikov.incubator.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val content = if (selected) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(ChoiceChipHeight)
            .clip(shape)
            .background(if (selected) DesignPalette.Accent else DesignPalette.Surface)
            .then(
                if (selected) Modifier
                else Modifier.border(0.8.dp, DesignPalette.CardBorder, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
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
