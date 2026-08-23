package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType

/**
 * Вкладка «Статистика»
 * ([узел 11:3144](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=11-3144)).
 *
 * Всё считается из уже имеющихся закладок: новых полей в базе не появилось.
 */
@Composable
internal fun StatsTab(uiState: IncubatorUiState) {
    // IntrinsicSize.Min тянет обе плитки ряда до высоты более высокой — в макете
    // они выровнены (self-stretch), а «1 акт. · 0 зав.» легко переносится на две строки.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            value = uiState.totalEggs.toString(),
            label = "Всего яиц",
            highlighted = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricCard(
            value = uiState.hatched.toString(),
            label = "Выведено птенцов",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            value = uiState.hatchRate?.let { "$it%" } ?: "—",
            label = "Средний вывод",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricCard(
            value = "${uiState.activeCount} акт. · ${uiState.finishedCount} зав.",
            label = "Закладок",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }

    Spacer(Modifier.height(16.dp))
    SpeciesChartCard(uiState.eggsBySpecies)

    Spacer(Modifier.height(16.dp))
    InsightCard(uiState.hatchRate)
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) DesignPalette.Accent else Color.White
        ),
        border = BorderStroke(
            0.8.dp,
            if (highlighted) DesignPalette.Accent.copy(alpha = 0.2f) else DesignPalette.CardBorder
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = DesignType.MetricValue,
                color = if (highlighted) DesignPalette.HeaderTitle
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = DesignType.Caption,
                color = if (highlighted) DesignPalette.HeaderIcon
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Высота самого высокого столбца — снята с макета (78.75 % от 160 dp). */
private val MaxBarHeight = 126.dp
private val BarWidth = 38.dp

@Composable
private fun SpeciesChartCard(eggsBySpecies: List<Pair<String, Int>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Яйца по видам птицы",
                style = DesignType.SectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Распределение текущего поголовья",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (eggsBySpecies.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Пока не заложено ни одного яйца.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val max = eggsBySpecies.maxOf { it.second }.coerceAtLeast(1)

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                eggsBySpecies.forEachIndexed { index, (species, eggs) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(BarWidth)
                                .height(MaxBarHeight * (eggs / max.toFloat()))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barColor(index))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = speciesEmoji(species), fontSize = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
            Spacer(Modifier.height(12.dp))

            eggsBySpecies.forEachIndexed { index, (species, eggs) ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = speciesEmoji(species), fontSize = 15.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = species,
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$eggs яиц",
                        style = DesignType.MonoAccent,
                        color = barColor(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(hatchRate: Int?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.InsightSurface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        val text = if (hatchRate == null) {
            buildAnnotatedString {
                append("Завершённых закладок пока нет — эффективность вывода появится здесь после первого вывода.")
            }
        } else {
            buildAnnotatedString {
                append("Эффективность вывода этого инкубатора — ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$hatchRate%") }
                append(". ")
                append(hatchVerdict(hatchRate))
            }
        }
        Text(
            text = text,
            style = DesignType.Body,
            color = DesignPalette.Accent,
            modifier = Modifier.padding(20.dp),
        )
    }
}

private fun hatchVerdict(rate: Int): String = when {
    rate >= 85 -> "Это отличный результат для домашнего хозяйства."
    rate >= 70 -> "Это хороший результат, но запас для роста ещё есть."
    rate >= 50 -> "Есть куда расти: проверьте температуру и влажность по дням."
    else -> "Стоит пересмотреть режим инкубации и качество яйца."
}

/** Цвет столбца задаётся позицией в отсортированном списке — так же, как в макете. */
private fun barColor(index: Int): Color =
    DesignPalette.ChartBars[index % DesignPalette.ChartBars.size]
