package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import kotlin.math.roundToInt

/*
 * Полоса вместимости — одна на два места: карточку инкубатора на стартовом экране и
 * форму новой закладки, где под «Количеством яиц» она показывает, сколько в устройстве
 * станет с этой закладкой. Одна функция, а не две похожие: число, процент и красный
 * перебора должны читаться одинаково там и там —
 * иначе форма обещала бы одно, а карточка после сохранения показывала бы другое.
 */

/**
 * Яйца против вместимости инкубатора. Вместимость может быть нулём (миграция с первой версии,
 * необязательное поле): тогда остаётся «N яиц в инкубации», без процента и полосы.
 *
 * **У инкубатора в архиве остаётся одна вместимость — «22 места».** Архив прерывает все идущие
 * закладки, и «0 / 22» с пустой полосой читалось бы как простой, то есть как упрёк. Архивный
 * инкубатор без вместимости не рисует ничего.
 */
@Composable
internal fun CapacityBlock(eggs: Int, capacity: Int, archived: Boolean = false) {
    if (archived) {
        if (capacity <= 0) return
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_egg_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = plural(capacity, "место", "места", "мест"),
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Заложено больше, чем мест: не ошибка ввода, а обычное дело — в лоток на 72 яйца
    // кладут 80, — но и не то, о чём узнают из ровно такой же зелёной строки, как при
    // половинной загрузке. Красный тут ничего не запрещает, он отвечает на вопрос
    // «сколько влезло» честно: перебор виден, пока закладку ещё можно поправить.
    val overfilled = capacity > 0 && eggs > capacity
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_egg_design),
                contentDescription = null,
                tint = if (overfilled) DesignPalette.Expense
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (capacity > 0) {
                    formatCount(eggs) + " / " + plural(capacity, "место", "места", "мест")
                } else {
                    plural(eggs, "яйцо", "яйца", "яиц") + " в инкубации"
                },
                style = DesignType.Caption,
                color = if (overfilled) DesignPalette.Expense
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (capacity > 0) {
            Text(
                text = "${(eggs.toFloat() / capacity * 100).roundToInt()}%",
                style = DesignType.MonoEmphasis,
                color = if (overfilled) DesignPalette.Expense else DesignPalette.Accent,
            )
        }
    }

    if (capacity > 0) {
        Spacer(Modifier.height(6.dp))
        // Полоса упирается в 100 %, хотя подпись может показывать больше:
        // в макете 108 из 72 мест — это 150 %, и полоса залита целиком.
        val fill = (eggs.toFloat() / capacity).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(DesignPalette.ProgressTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (overfilled) DesignPalette.Expense else DesignPalette.Accent)
            )
        }
    }
}
