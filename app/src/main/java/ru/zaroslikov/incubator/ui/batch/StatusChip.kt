package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.domain.model.BatchStatus

/**
 * Статус-чип закладки: «Инкубация», «Завершено» или «Не завершено».
 *
 * Один на карточку в списке инкубатора и на сводку в шторке закладки — про одну
 * закладку в двух местах должно стоять одно и то же слово одним и тем же цветом.
 *
 * Третий — прерванная закладка — красный, той же парой цветов, что и кнопка досрочного
 * завершения. Нейтральный «Завершено» на ней читался бы как «всё прошло как надо», а
 * прошло оно как раз не так, и это единственное, что карточка о ней сообщает: причина
 * лежит в `endReason` и видна только внутри.
 */
@Composable
internal fun StatusChip(status: BatchStatus, modifier: Modifier = Modifier) {
    val (text, content, surface) = when (status) {
        BatchStatus.Active -> Triple(
            "Инкубация",
            DesignPalette.StatusActiveText,
            DesignPalette.StatusActiveSurface,
        )

        BatchStatus.Hatched -> Triple(
            "Завершено",
            DesignPalette.StatusDoneText,
            DesignPalette.StatusDoneSurface,
        )

        BatchStatus.Stopped -> Triple(
            "Не завершено",
            DesignPalette.Expense,
            DesignPalette.ExpenseSurface,
        )
    }

    Text(
        text = text,
        style = DesignType.ChipLabel,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(surface)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
