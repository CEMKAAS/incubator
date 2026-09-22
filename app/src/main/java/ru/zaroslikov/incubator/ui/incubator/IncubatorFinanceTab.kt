package ru.zaroslikov.incubator.ui.incubator

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.BannerAdHost
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.stats.BatchFinance
import ru.zaroslikov.incubator.domain.stats.IncubatorFinance
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Вкладка «Финансы»
 * ([узел 11:3466](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=11-3466)).
 *
 * Верхняя карточка — из макета: крупный итог и две плитки под ним. Всё остальное
 * дописано сверх него, потому что макет рисовал журнал операций, которого в приложении
 * нет и не завели: приложение знает о деньгах ровно три числа — во что обошлись яйца
 * закладки, за сколько ушли её птенцы и сколько стоил сам инкубатор, — и раздел считает
 * себя целиком из них (`:domain`, `stats/IncubatorFinance.kt`). Поэтому пунктирной
 * кнопки «Добавить операцию» здесь тоже нет: нажимать ей было бы не на что.
 *
 * Порядок карточек — от общего к частному, как и в «Статистике»: баланс, показатели на
 * единицу (яйцо и птенца), окупаемость самого инкубатора, разбор по закладкам и вывод.
 *
 * **Главная оговорка раздела повторяется на экране трижды, и это не избыточность.**
 * Цена — необязательная графа: закладка без неё вносит в расход ноль, и баланс
 * получается лучше настоящего. Поэтому число закладок без цены стоит и под балансом, и
 * в подсказках плиток, и в выводе внизу — там, где человек как раз и решает, верить ли
 * цифре.
 */
@Composable
internal fun FinanceTab(
    finance: IncubatorFinance,
    scope: TabScope = TabScope.One,
    /**
     * Владелец рекламного объявления этой вкладки; `null` — вкладка без рекламы.
     * Заводится на уровне экрана — см. [StatsTab].
     */
    adHost: BannerAdHost? = null,
) {

    BalanceCard(finance, scope)

    // Реклама — сразу после карточки баланса, до всего остального: она должна попасть
    // на экран и в том случае, когда цен нигде не введено и вкладка кончается через
    // одну карточку, и в том, когда под ней ещё четыре блока.
    if (adHost != null) {
        Spacer(Modifier.height(16.dp))
        AdBanner(adHost)
    }

    if (!finance.hasMoney) {
        Spacer(Modifier.height(16.dp))
        NoMoneyCard(scope)
        return
    }

    Spacer(Modifier.height(16.dp))
    UnitMetrics(finance)

    Spacer(Modifier.height(16.dp))
    IncubatorPaybackCard(finance, scope)

    Spacer(Modifier.height(16.dp))
    BatchFinanceCard(finance)

    Spacer(Modifier.height(16.dp))
    FinanceInsightCard(finance)
}

// --- Баланс сверху -------------------------------------------------------------------------

/**
 * Текущий баланс и две плитки под ним — карточка из макета.
 *
 * Баланс считается по всем закладкам, включая идущие: их яйца уже куплены. Поэтому под
 * ним стоит оговорка, сколько денег сейчас лежит в инкубаторе, — без неё уходящий в
 * минус баланс работающего хозяйства читается как убыток, хотя это ещё не итог.
 *
 * Цена самого инкубатора входит и в расход, и в баланс: хозяйство не в плюсе, пока не
 * вернуло себе и технику тоже. Поэтому плитка расхода подписана «инкубатор + яйца» —
 * сумма, в которой слагаемые разной природы, обязана их назвать, иначе она читается как
 * ошибка у всякого, кто сложит свои закладки в уме. Сколько там техники, показывает
 * карточка инкубатора ниже.
 */
@Composable
private fun BalanceCard(finance: IncubatorFinance, scope: TabScope) {
    val currency = LocalUnits.current.currency
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Текущий баланс",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Одна строка и здесь: баланс — самое крупное число на вкладке, и перенос
            // его хвоста под себя читался бы как вторая, другая сумма.
            TruncatedText(
                text = formatMoneySigned(finance.balance, currency),
                style = DesignType.MoneyLarge,
                color = if (finance.balance < 0) DesignPalette.Expense else DesignPalette.Accent,
                maxLines = 1,
            )
            val note = balanceNote(finance, currency)
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = DesignType.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            // Плитки одной высоты ([TileRow]): подпись расхода — «инкубатор + яйца» и
            // длиннее, чем «за птенцов», а на «Аналитике» она ещё и во множественном
            // числе, так что переносится на вторую строку раньше соседки.
            TileRow(spacing = 12.dp) {
                MoneyTile(
                    icon = R.drawable.ic_arrow_up_design,
                    label = "Доход",
                    amount = formatMoney(finance.income, currency),
                    hint = "за птенцов",
                    surface = DesignPalette.IncomeSurface,
                    tint = DesignPalette.Accent,
                    modifier = tileWeight(),
                )
                MoneyTile(
                    icon = R.drawable.ic_arrow_down_design,
                    label = "Расход",
                    amount = formatMoney(finance.expense, currency),
                    // Из чего сложился — обязательная часть плитки, а не украшение:
                    // цена инкубатора и цена яиц складываются в одно число, но природа
                    // у них разная, и человек, увидевший расход больше суммы своих
                    // закладок, должен тут же прочесть, откуда взялась разница.
                    hint = expenseHint(finance, scope),
                    surface = DesignPalette.ExpenseSurface,
                    tint = DesignPalette.Expense,
                    modifier = tileWeight(),
                )
            }
        }
    }
}

/**
 * Из чего сложился расход: «инкубатор + яйца», либо только яйца, если цены техники нет.
 *
 * Порядок слагаемых — как в вопросе, который к плитке возникает: сперва то, чего в сумме
 * не ждали. Про инкубатор молчим, когда его цену не вводили: обещать слагаемое, которого
 * в сумме нет, хуже, чем не обещать ничего.
 */
private fun expenseHint(finance: IncubatorFinance, scope: TabScope): String =
    if (finance.incubatorPrice > 0) scope.expenseComposition else "вложено в яйца"

/** «в работе ещё 9 000 ₽ · 2 закладки без цены» — пустая, когда сказать нечего. */
private fun balanceNote(finance: IncubatorFinance, currency: Currency): String? {
    val parts = buildList {
        if (finance.activeInvested > 0) add("в работе ещё ${formatMoney(finance.activeInvested, currency)}")
        if (finance.batchesWithoutEggPrice > 0) {
            add(plural(finance.batchesWithoutEggPrice, "закладка", "закладки", "закладок") + " без цены")
        }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

@Composable
private fun MoneyTile(
    @DrawableRes icon: Int,
    label: String,
    amount: String,
    hint: String,
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
            TruncatedText(text = amount, style = DesignType.MoneyTile, color = tint, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                style = DesignType.Micro,
                color = tint.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Что показать вместо всего раздела, пока цен нет ни одной.
 *
 * Не «0 ₽» во всех плитках: ноль — это ответ «денег не заработано», а тут другое —
 * не спрашивали. Поэтому вместо чисел карточка объясняет, где эти цены вводятся:
 * все три графы необязательные, и найти их с непривычки негде.
 */
@Composable
private fun NoMoneyCard(scope: TabScope) {
    val currency = LocalUnits.current.currency
    TabCard {
        CardHeader(
            title = "Денег пока не видно",
            subtitle = "Раздел считается из трёх необязательных граф",
        )
        Spacer(Modifier.height(14.dp))
        FinanceHintRow("Стоимость яиц", "в форме закладки, поле «Стоимость, ${currency.symbol}»")
        Spacer(Modifier.height(10.dp))
        FinanceHintRow("Стоимость птенцов", "при завершении закладки")
        Spacer(Modifier.height(10.dp))
        FinanceHintRow(scope.equipmentPriceLabel, scope.equipmentPriceHint)
    }
}

@Composable
private fun FinanceHintRow(title: String, where: String) {
    Column {
        Text(
            text = title,
            style = DesignType.ListItemTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = where,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Показатели на единицу -----------------------------------------------------------------

/**
 * Четыре числа «на штуку»: во что обходится яйцо и птенец и за сколько уходит птенец.
 *
 * Средняя цена и себестоимость птенца стоят рядом намеренно: их разница и есть заработок
 * с головы, и считать её глазами через всю страницу не пришлось бы никому.
 *
 * Каждое из четырёх — среднее по своему знаменателю, и знаменатель написан под числом.
 * Себестоимость считается только по завершённым закладкам, ровно как процент вывода в
 * «Статистике»: у идущей деньги уже потрачены, а птенцов ещё нет, и она одна утащила бы
 * себестоимость в небо.
 */
@Composable
private fun UnitMetrics(finance: IncubatorFinance) {
    val currency = LocalUnits.current.currency
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = finance.expensePerEgg?.let { formatMoney(it, currency) } ?: "—",
            label = "Расход на яйцо",
            hint = if (finance.pricedEggs > 0) {
                "по " + plural(finance.pricedEggs, "яйцу", "яйцам", "яйцам") + " с ценой"
            }
            else "цены яиц не указаны",
            modifier = tileWeight(),
        )
        MetricCard(
            value = formatMoney(finance.lost, currency),
            label = "Потери",
            hint = lostHint(finance),
            valueColor = DesignPalette.Expense,
            modifier = tileWeight(),
        )
    }
    Spacer(Modifier.height(12.dp))
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = finance.avgChickPrice?.let { formatMoney(it, currency) } ?: "—",
            label = "Цена птенца",
            hint = if (finance.pricedChicks > 0) {
                "средняя по ${plural(finance.pricedChicks, "птенцу", "птенцам", "птенцам")}"
            } else "цены птенцов не указаны",
            valueColor = DesignPalette.Accent,
            modifier = tileWeight(),
        )
        MetricCard(
            value = finance.chickCost?.let { formatMoney(it, currency) } ?: "—",
            label = "Себестоимость",
            hint = if (finance.hatchedWithCost > 0) {
                "по ${plural(finance.hatchedWithCost, "птенцу", "птенцам", "птенцам")} завершённых"
            } else "нет завершённых с ценой",
            modifier = tileWeight(),
        )
    }
}

/** «140 яиц не вывелось» — или почему потерь не посчитать. */
private fun lostHint(finance: IncubatorFinance): String = when {
    finance.expensePerEgg == null -> "цены яиц не указаны"
    finance.lostEggs == 0 -> "завершённые вывелись полностью"
    else -> plural(finance.lostEggs, "яйцо", "яйца", "яиц") + " не вывелось"
}

// --- Окупаемость инкубатора ----------------------------------------------------------------

/**
 * Во что обошёлся сам инкубатор и какую долю своей цены он уже отбил.
 *
 * Отдельной карточкой, а не плиткой в ряду: это единственное слагаемое расхода, которое
 * к закладкам не относится вовсе — разовая покупка техники, — и здесь оно названо своим
 * числом, тогда как в плитке расхода оно спрятано внутри суммы.
 *
 * Окупаемость считается по прибыли **до** вычета техники: в балансе она уже вычтена, и
 * делить его на её же цену значило бы вычесть дважды. Поэтому «100 %» на этой полоске —
 * та самая отметка, на которой баланс наверху переходит через ноль.
 *
 * Полоска — та же, что у эффективности в «Статистике», и по той же причине: доля
 * читается глазом быстрее, чем проценты цифрами.
 */
@Composable
private fun IncubatorPaybackCard(finance: IncubatorFinance, scope: TabScope) {
    val currency = LocalUnits.current.currency
    TabCard {
        CardHeader(
            title = scope.equipmentTitle,
            subtitle = "Разовая покупка — её доля в общем расходе",
        )
        Spacer(Modifier.height(14.dp))

        if (finance.incubatorPrice <= 0) {
            Text(
                text = scope.equipmentPriceMissing,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TabCard
        }

        val payback = finance.payback ?: 0
        // Сумма расписана слагаемыми: «инкубатор + яйца» в подписи плитки наверху
        // говорит, из чего она сложена, а здесь стоят сами числа — иначе разницу между
        // расходом и суммой своих закладок пришлось бы вычислять в уме.
        PaybackRow(scope.equipmentPriceLabel, formatMoney(finance.incubatorPrice, currency))
        Spacer(Modifier.height(10.dp))
        PaybackRow("Вложено в яйца", formatMoney(finance.invested, currency))
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
        Spacer(Modifier.height(10.dp))
        PaybackRow("Общий расход", formatMoney(finance.expense, currency), emphasis = true)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Окупаемость",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$payback%",
                style = DesignType.MonoAccent,
                color = if (payback > 0) DesignPalette.Accent
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(fraction = payback.coerceIn(0, 100) / 100f, color = DesignPalette.Accent)
        Spacer(Modifier.height(5.dp))
        Text(
            text = paybackFooter(finance, payback, scope, currency),
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Строка «подпись — сумма» карточки инкубатора; итоговая набрана заметнее слагаемых. */
@Composable
private fun PaybackRow(label: String, value: String, emphasis: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = if (emphasis) DesignType.ListItemTitle else DesignType.Body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = DesignType.MoneyRow,
            color = if (emphasis) DesignPalette.Expense else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * «Прибыль покрыла 6 200 из 8 000 ₽» — или что мешает ей это сделать.
 *
 * Считает по [IncubatorFinance.profitOnBatches]: техника отбивается прибылью от закладок,
 * а не балансом, из которого её саму уже вычли.
 */
private fun paybackFooter(
    finance: IncubatorFinance,
    payback: Int,
    scope: TabScope,
    currency: Currency,
): String = when {
    finance.profitOnBatches <= 0 ->
        "Пока в минусе: прибыль закладок ещё не покрыла расход на яйца."
    payback >= 100 ->
        scope.paybackDone + formatMoney(finance.balance, currency) + "."
    else ->
        "Прибыль покрыла ${formatMoney(finance.profitOnBatches, currency)} " +
            "из ${formatMoney(finance.incubatorPrice, currency)}."
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DesignPalette.PillSurface)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// --- Разбор по закладкам -------------------------------------------------------------------

private const val BatchPreview = 5

/** Сколько места держит за собой денежная колонка строки: «−12 500 ₽» в MonoAccent. */
private val MoneyColumnWidth = 108.dp

/**
 * Финансовый итог каждой закладки: свёрнутая строка — прибыль, развёрнутая — из чего
 * она сложилась.
 *
 * Развёрнута всегда не больше одной: четыре числа на закладку, помноженные на десяток
 * закладок, превращают карточку в простыню, из которой главное — какая закладка сколько
 * принесла — уже не читается. Первыми показываются пять строк, как в истории выводов, и
 * по той же причине: карточка стоит посреди прокручиваемой страницы.
 */
@Composable
private fun BatchFinanceCard(finance: IncubatorFinance) {
    var expandedId by rememberSaveable { mutableLongStateOf(0L) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    TabCard {
        CardHeader(
            title = "По закладкам",
            subtitle = "Доход, расход, потери и итог каждой",
        )

        if (finance.batches.isEmpty()) {
            EmptyNote("Закладок пока нет — здесь появится финансовый разбор каждой.")
            return@TabCard
        }

        val shown = if (showAll) finance.batches else finance.batches.take(BatchPreview)

        Spacer(Modifier.height(14.dp))
        shown.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                Spacer(Modifier.height(10.dp))
            }
            BatchFinanceRow(
                row = row,
                expanded = expandedId == row.batchId,
                onClick = { expandedId = if (expandedId == row.batchId) 0L else row.batchId },
            )
        }

        if (finance.batches.size > BatchPreview) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (showAll) "Свернуть" else "Показать все (${finance.batches.size})",
                style = DesignType.MonoAccent,
                color = DesignPalette.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAll = !showAll }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun BatchFinanceRow(row: BatchFinance, expanded: Boolean, onClick: () -> Unit) {
    val currency = LocalUnits.current.currency
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = speciesEmoji(row.species), fontSize = 18.sp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                // Как и в истории выводов: обрезанное название закладки — и строку с
                // породой под ним — договаривает подсказка по нажатию. Строка сама по
                // себе раскрывает разбор; пока название влезает, нажатие достаётся ей
                // целиком.
                TruncatedText(
                    text = row.title,
                    style = DesignType.ListItemTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                TruncatedText(
                    text = batchSubtitle(row),
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.size(10.dp))
            // Ширина задана числом, а тексты растянуты по ней и прижаты вправо: колонка,
            // которой отмерили ровно по содержимому, теряет последнюю букву — шрифты
            // догружаются с Google Fonts, и меряется строка не тем начертанием, которым
            // рисуется. Отступ в конце — про то же: буква, упирающаяся в границу
            // колонки, лишается своего последнего столбца пикселей.
            Column(
                modifier = Modifier
                    .width(MoneyColumnWidth)
                    .padding(end = 2.dp)
            ) {
                Text(
                    text = profitText(row, currency),
                    style = DesignType.MonoAccent,
                    color = profitColor(row),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = profitCaption(row),
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            BatchFinanceBreakdown(row)
        }
    }
}

/** «Ломан Браун · 12.03.2026 · 143 из 200» — пустые части просто выпадают. */
private fun batchSubtitle(row: BatchFinance): String = listOf(
    row.breed,
    row.dateEnd,
    if (row.status == BatchStatus.Active) plural(row.eggs, "яйцо", "яйца", "яиц") + " в работе"
    else "${formatCount(row.hatched)} из ${formatCount(row.eggs)}",
).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Число справа: итог у завершённой, вложенное — у идущей, прочерк — у безденежной.
 *
 * У идущей закладки итога нет, но деньги в ней уже лежат, и слово «в работе» на месте
 * суммы промолчало бы о единственном, что про неё сейчас известно. Чем это число
 * является, говорит подпись под ним, и она же держит колонку узкой: сумма вместе со
 * словами в одну строку с названием закладки не помещается.
 */
private fun profitText(row: BatchFinance, currency: Currency): String {
    val profit = row.profit
    return when {
        !row.known -> "—"
        profit == null -> formatMoney(row.invested, currency)
        else -> formatMoneySigned(profit, currency)
    }
}

private fun profitCaption(row: BatchFinance): String = when {
    !row.known -> "нет цен"
    row.profit == null -> "вложено"
    else -> "прибыль"
}

/** Композабл: [DesignPalette] зависит от темы и читается только из композиции. */
@Composable
@ReadOnlyComposable
private fun profitColor(row: BatchFinance): Color {
    val profit = row.profit
    return when {
        !row.known || profit == null -> DesignPalette.StatusDoneText
        profit < 0 -> DesignPalette.Expense
        else -> DesignPalette.Accent
    }
}

/**
 * Четыре числа закладки: доход, расход, потери и итог.
 *
 * «Вложено» отдельной строкой нет — это подпись расхода, ровно как в плитке наверху.
 * Потери тоже не пятое слагаемое: они уже сидят внутри расхода, и складывать их с ним
 * значило бы посчитать одни и те же яйца дважды, — о чём строка и говорит.
 */
@Composable
private fun BatchFinanceBreakdown(row: BatchFinance) {
    val currency = LocalUnits.current.currency
    Column(Modifier.padding(top = 12.dp, start = 28.dp)) {
        MoneyLine(
            label = "Доход",
            value = if (row.hasChickPrice) formatMoney(row.income, currency) else "не указан",
            hint = if (row.hasChickPrice) "за ${plural(row.hatched, "птенца", "птенцов", "птенцов")}"
            else null,
            color = if (row.hasChickPrice) DesignPalette.Accent else null,
        )
        Spacer(Modifier.height(8.dp))
        MoneyLine(
            label = "Расход",
            value = if (row.hasEggPrice) formatMoney(row.expense, currency) else "не указан",
            hint = row.eggPrice?.let {
                "вложено в " + plural(row.eggs, "яйцо", "яйца", "яиц") + " по ${formatMoney(it, currency)}"
            },
            color = if (row.hasEggPrice) DesignPalette.Expense else null,
        )
        Spacer(Modifier.height(8.dp))
        MoneyLine(
            label = "Потери",
            value = if (row.hasEggPrice && row.status != BatchStatus.Active) {
                formatMoney(row.lost, currency)
            } else "—",
            hint = when {
                row.status == BatchStatus.Active -> "закладка ещё идёт"
                !row.hasEggPrice -> null
                else -> "часть расхода: " +
                    plural(row.eggs - row.hatched, "яйцо", "яйца", "яиц") + " не вывелось"
            },
            color = if (row.lost > 0) DesignPalette.Expense else null,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
        Spacer(Modifier.height(8.dp))
        MoneyLine(
            label = "Итог",
            // Не profitText: в свёрнутой строке у идущей закладки на месте суммы стоит
            // вложенное, а здесь строка так и подписана — «Итог», — и вложенное под этим
            // словом читалось бы как заработок.
            value = row.profit?.takeIf { row.known }?.let { formatMoneySigned(it, currency) } ?: "—",
            hint = when {
                row.profit == null -> "закладка ещё идёт"
                !row.known -> "цены не указаны"
                else -> null
            },
            color = profitColor(row),
            emphasis = true,
        )
    }
}

@Composable
private fun MoneyLine(
    label: String,
    value: String,
    hint: String?,
    color: Color?,
    emphasis: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasis) DesignType.ListItemTitle else DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = DesignType.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = value,
            style = DesignType.MoneyRow,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Вывод внизу ---------------------------------------------------------------------------

/**
 * Тот же зелёный блок-вывод, что и в «Статистике», только про деньги.
 *
 * Сначала — заработок с головы, если его есть из чего посчитать: он и есть ответ на
 * вопрос, ради которого раздел открывают. Следом, отдельным предложением, — сколько
 * закладок в счёт не попало, потому что вывод, умалчивающий о своей неполноте, хуже
 * отсутствующего.
 */
@Composable
private fun FinanceInsightCard(finance: IncubatorFinance) {
    val currency = LocalUnits.current.currency
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.InsightSurface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        val cost = finance.chickCost
        val sale = finance.avgChickPrice
        val text = buildAnnotatedString {
            if (cost != null && sale != null) {
                append("Птенец обходится в ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(formatMoney(cost, currency)) }
                append(", а уходит по ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(formatMoney(sale, currency)) }
                append(" — ")
                append(marginVerdict(sale - cost, currency))
            // Не по общему расходу: в него входит цена инкубатора, и хозяйство, где
            // заполнена только она, получило бы совет вводить цену птенцов, хотя не
            // введена цена яиц.
            } else if (finance.invested > 0) {
                append(
                    "Доход появится, когда при завершении закладки вы укажете стоимость " +
                        "птенцов: тогда посчитаются и заработок с головы, и окупаемость."
                )
            } else {
                append(
                    "Расход появится, когда в форме закладки вы укажете стоимость яиц: " +
                        "из неё считаются и потери, и себестоимость птенца."
                )
            }

            val skipped = finance.batchesWithoutEggPrice
            if (skipped > 0) {
                // «Не вошли 1 закладка» — поэтому число стоит подлежащим, а не после
                // глагола: так одна фраза годится и для одной закладки, и для пяти.
                append(" Ещё ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(plural(skipped, "закладка", "закладки", "закладок"))
                }
                append(" без указанной цены яиц — их деньги в счёт не вошли.")
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

private fun marginVerdict(margin: Int, currency: Currency): String = when {
    margin > 0 -> "${formatMoney(margin, currency)} прибыли с головы."
    margin == 0 -> "работа идёт ровно в ноль."
    else -> "${formatMoney(-margin, currency)} убытка с головы: птенцы уходят дешевле, чем обходятся."
}
