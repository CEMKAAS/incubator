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
 * станет с этой закладкой. Одна функция, а не две похожие: число, процент, красный
 * перебора и заглушка «укажите вместимость» должны читаться одинаково там и там —
 * иначе форма обещала бы одно, а карточка после сохранения показывала бы другое.
 */

/**
 * Яйца против вместимости инкубатора.
 *
 * Вместимость обязательна, но у инкубатора, созданного при миграции с первой версии,
 * её взять было неоткуда — там ноль. В этом случае вместо процента показываем просьбу
 * заполнить: полоса без вместимости бессмысленна.
 *
 * **У инкубатора в архиве остаётся одна вместимость — «22 места».** «0 / 22», процент и
 * полоса отвечают на вопрос «насколько он сейчас занят», а выведенное из работы
 * устройство не занято ничем и не будет: архив прерывает все идущие закладки. Пустая
 * полоса и «0%» под названием читались бы как простаивающий инкубатор, то есть как
 * упрёк, — тогда как вместимость это просто свойство устройства, и она никуда не делась.
 * Ровно то же решение, что и с цифрами в шапке экрана инкубатора.
 */
@Composable
internal fun CapacityBlock(eggs: Int, capacity: Int, archived: Boolean = false) {
    if (archived) {
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
                // Ноль вместимости — наследство первой версии схемы; просьба заполнить
                // остаётся в силе и в архиве: карандаш в шапке инкубатора работает.
                text = if (capacity > 0) plural(capacity, "место", "места", "мест")
                else "вместимость не указана",
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
                    plural(eggs, "яйцо", "яйца", "яиц")
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
        } else {
            Text(
                text = "укажите вместимость",
                style = DesignType.Caption,
                color = DesignPalette.DateEmphasis,
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
