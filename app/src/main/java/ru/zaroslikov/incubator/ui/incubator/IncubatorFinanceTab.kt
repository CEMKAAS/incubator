package ru.zaroslikov.incubator.ui.incubator

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType

/**
 * Вкладка «Финансы»
 * ([узел 11:3466](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=11-3466)).
 *
 * Вёрстка по макету, но данных под ней пока нет: доходов и расходов приложение
 * не хранит — ни таблицы, ни сущности. Поэтому суммы нулевые, список операций
 * заменён пустым состоянием, а кнопка добавления неактивна. Чтобы вкладка ожила,
 * нужна отдельная сущность операции и миграция базы на версию 3.
 */
@Composable
internal fun FinanceTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Чистая прибыль",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(0),
                style = DesignType.MoneyLarge,
                color = DesignPalette.Accent,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyTile(
                    icon = R.drawable.ic_arrow_up_design,
                    label = "Доходы",
                    amount = formatMoney(0),
                    surface = DesignPalette.IncomeSurface,
                    tint = DesignPalette.Accent,
                    modifier = Modifier.weight(1f),
                )
                MoneyTile(
                    icon = R.drawable.ic_arrow_down_design,
                    label = "Расходы",
                    amount = formatMoney(0),
                    surface = DesignPalette.ExpenseSurface,
                    tint = DesignPalette.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Операций пока нет",
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Учёт доходов и расходов инкубатора появится в следующем обновлении.",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    DashedButton(
        text = "Добавить операцию",
        onClick = null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MoneyTile(
    @DrawableRes icon: Int,
    label: String,
    amount: String,
    surface: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(text = label, style = DesignType.Caption, color = tint)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = amount, style = DesignType.MoneyTile, color = tint)
        }
    }
}

/** «11 250 ₽» — разряды через неразрывный пробел, как в макете. */
internal fun formatMoney(amount: Int): String {
    val digits = kotlin.math.abs(amount).toString()
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    val sign = if (amount < 0) "−" else ""
    return "$sign$grouped ₽"
}
