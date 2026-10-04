package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.design.components.HintIcon
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Общие детали вкладок экрана инкубатора — «Статистика» и «Финансы».
 *
 * Обе вкладки — это столбец одинаковых белых карточек с одинаковыми заголовками и
 * плитками-числами, и держать два набора этих мелочей значит развести их при первой же
 * правке скругления. Здесь лежит то, чем обе пользуются дословно; всё, что своё —
 * диаграммы, полоски эффективности, денежные строки, — остаётся в файле своей вкладки.
 */

/**
 * По одному ли инкубатору посчитаны вкладки или по всем сразу. Обе вкладки — чистые функции
 * `:domain` над списком закладок, и «Аналитика» показывает те же вкладки, а не копию. Числа от
 * разреза не зависят — только шесть подписей, собранных здесь: «Стоимость инкубатора» под суммой
 * пяти цен была бы неправдой.
 */
internal enum class TabScope(
    /** Техника как слагаемое расхода в подписи плитки: «инкубатор + яйца + свет». */
    val equipmentWord: String,
    /** Строка с ценой техники внутри неё. */
    val equipmentPriceLabel: String,
    /** Где эту цену вписать, если её нет. */
    val equipmentPriceHint: String,
    /** Что сказать, когда цена не введена ни у одного устройства. */
    val equipmentPriceMissing: String,
    /**
     * Начало фразы об окупившейся технике; дальше подставляется сумма сверх её цены.
     *
     * Отдельная строка, а не название техники со сказуемым по месту: число у подлежащего
     * меняет глагол («инкубатор окупился» против «инкубаторы окупились»), и склеенная
     * из двух частей фраза была бы верна ровно в одном из двух разрезов.
     */
    val paybackDone: String,
    /** Чья эффективность вывода — в подсказке внизу «Статистики». */
    val efficiencySubject: String,
) {
    /** Экран одного инкубатора. */
    One(
        equipmentWord = "инкубатор",
        equipmentPriceLabel = "Стоимость инкубатора",
        equipmentPriceHint = "в его настройках — шестерёнка сверху",
        equipmentPriceMissing = "Цена не указана — её можно вписать в настройках инкубатора.",
        paybackDone = "Инкубатор окупился, сверх его цены заработано ",
        efficiencySubject = "этого инкубатора",
    ),

    /** «Аналитика»: всё хозяйство сразу. */
    All(
        equipmentWord = "инкубаторы",
        equipmentPriceLabel = "Стоимость инкубаторов",
        equipmentPriceHint = "в настройках каждого инкубатора",
        equipmentPriceMissing = "Цены не указаны — их можно вписать в настройках инкубаторов.",
        paybackDone = "Инкубаторы окупились, сверх их цены заработано ",
        efficiencySubject = "всего хозяйства",
    ),
}

/** Белая карточка со скруглением 22 dp — из неё собраны все блоки обеих вкладок. */
@Composable
internal fun TabCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
internal fun CardHeader(title: String, subtitle: String? = null, hint: String? = null) {
    // [hint] — значок «i» у заголовка: правило, которое нужно раз, а не при каждом взгляде.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = DesignType.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) HintIcon(hint = hint)
    }
    // Без подзаголовка — один заголовок: вкладка «Статистика» подписей под ним не носит.
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = DesignType.Placeholder,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Плитка с числом: значение, подпись и — почти всегда — знаменатель под ней. [hint] нужен везде,
 * где числа посчитаны по разным подмножествам закладок: рядом они читаются как ошибка счёта.
 *
 * **Значение в одну строку, переполненное договаривает подсказка** ([TruncatedText]): плитки идут
 * по две с общей высотой ([TileRow]), и перенос «1 234 567 ₽» перекосил бы весь ряд. Нажатие
 * показывает сумму целиком — обрезаются младшие разряды, то есть точность.
 */
@Composable
internal fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    highlighted: Boolean = false,
    valueColor: Color? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) DesignPalette.Accent else DesignPalette.Surface
        ),
        border = BorderStroke(
            0.8.dp,
            if (highlighted) DesignPalette.Accent.copy(alpha = 0.2f) else DesignPalette.CardBorder
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            TruncatedText(
                text = value,
                style = DesignType.MetricValue,
                // Залитая плитка красится акцентом, поэтому надпись на ней —
                // [DesignPalette.OnAccent], а не белый цвет шапки: шапка тёмно-зелёная
                // в обеих темах, а акцент в тёмной светлеет, и белым по нему не прочитать.
                color = when {
                    highlighted -> DesignPalette.OnAccent
                    valueColor != null -> valueColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = DesignType.Caption,
                color = if (highlighted) DesignPalette.OnAccent.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = hint,
                    style = DesignType.Micro,
                    // Тише подписи: это уточнение к числу, а не второе название плитки.
                    color = if (highlighted) DesignPalette.OnAccent.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * Русское склонение после числа: 1 птенец, 2 птенца, 5 птенцов.
 *
 * Само число идёт через [formatCount] — это самый частый способ напечатать количество в
 * приложении, и разделитель разрядов должен достаться ему по умолчанию, а не по памяти
 * вызывающего.
 */
internal fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "${formatCount(count)} $word"
}

/**
 * «11 250 ₽» — те же разряды, что и у любого другого числа ([formatCount]), плюс знак
 * валюты из «Настроек» ([Currency.symbol]): суммы хранятся числом без валюты, и знак —
 * единственное, что в них меняется вместе с выбором.
 *
 * Минус здесь типографский, а не дефис: рядом с моноширинными цифрами дефис теряется.
 */
internal fun formatMoney(amount: Int, currency: Currency): String =
    "${formatCount(amount)} ${currency.symbol}"

/**
 * То же с плюсом у положительных: «+11 250 ₽», «−3 400 ₽», «0 ₽».
 *
 * Знак нужен там, где число может быть любым по смыслу — баланс, прибыль закладки, — и
 * не нужен там, где отрицательного не бывает: доход, расход, потери всегда со знаком
 * «плюс» по своей природе, и приписанный плюс делал бы расход похожим на прибавку.
 */
internal fun formatMoneySigned(amount: Int, currency: Currency): String =
    if (amount > 0) "+${formatMoney(amount, currency)}" else formatMoney(amount, currency)
