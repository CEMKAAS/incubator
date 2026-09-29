package ru.zaroslikov.incubator.domain.stats

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.status

/**
 * Финансы инкубатора: всё, что показывает вкладка «Финансы», посчитано здесь.
 *
 * Живёт рядом с [incubatorStats] и по той же причине: складывать рубли и делить их на
 * птенцов — арифметика над закладками, а не работа Compose, и проверяется она обычным
 * JVM-тестом (`gradlew :domain:test`), без эмулятора.
 *
 * **Журнала операций под это не заведено, и не нужно.** Деньги в приложении
 * записываются дважды: [Batch.price] с [Batch.pricePerEgg] — во что обошлись яйца,
 * [Batch.chickPrice] с [Batch.chickPricePerHead] — за сколько ушли птенцы, и
 * `Incubator.price` — во что обошлось само устройство. Больше приложение о деньгах
 * ничего не знает: ни электричества, ни корма, ни отдельного журнала операций, — и
 * весь раздел выводится из этих трёх чисел.
 *
 * Порода в деньгах не участвует: в одной закладке одна порода, и «сколько принесла
 * порода» — это сумма по её закладкам, а не строка внутри одной из них.
 *
 * Из этого следует главное ограничение, которое экран обязан проговаривать вслух:
 * **расход инкубатора — это ровно стоимость купленных яиц**, а не полная себестоимость
 * хозяйства. Закладка, у которой цену не заполнили, вносит в него ноль — не потому что
 * яйца достались даром, а потому что не спросили. Поэтому каждое среднее считается по
 * своему знаменателю (только закладки с заполненной ценой), а сколько закладок осталось
 * за бортом, [IncubatorFinance] называет отдельно: [batchesWithoutEggPrice] и
 * [batchesWithoutChickPrice]. Без этой оговорки «расход на яйцо» у хозяйства, где цену
 * ввели однажды, выглядел бы втрое меньше настоящего.
 */

/**
 * Деньги одной закладки.
 *
 * [invested] — что заплатили за её яйца, [income] — что выручили за птенцов, и это
 * единственные два денежных факта, которые о закладке известны. [expense] равен
 * [invested] не по совпадению, а потому, что других расходов у закладки быть неоткуда:
 * заведётся журнал операций — слагаемые появятся у него, и звать их придётся уже здесь.
 *
 * [lost] — не третье слагаемое расхода, а **часть** [invested]: деньги за яйца, которые
 * не стали птенцами. Прибавлять их к расходу значило бы посчитать те же яйца дважды.
 *
 * [profit] пуст у идущей закладки, и это не «ноль прибыли». Итога у неё ещё нет: яйца
 * уже куплены, птенцов ещё не продали, и любое число здесь читалось бы как убыток,
 * которого пока не случилось.
 */
data class BatchFinance(
    val batchId: Long,
    val title: String,
    val species: String,
    val breed: String,
    val status: BatchStatus,
    /** Дата завершения «dd.MM.yyyy»; пустая у идущих и у завершённых до появления поля. */
    val dateEnd: String,
    val eggs: Int,
    val hatched: Int,
    /** Цена одного яйца в рублях; `null` — стоимость не указывали. */
    val eggPrice: Int?,
    /** Цена одного птенца в рублях; `null` — не указывали. */
    val chickPrice: Int?,
    /** Вложено в яйца — ровно то, что заплатили за партию. Ноль, если цены нет. */
    val invested: Int,
    /** Выручено за птенцов. Ноль, если цены нет или закладка ещё идёт. */
    val income: Int,
    /** Деньги за невылупившиеся яйца. Считаются только у завершённых закладок. */
    val lost: Int,
) {
    /** Расход закладки. Сегодня это в точности [invested] — других расходов нет. */
    val expense: Int get() = invested

    /** Итог: доход минус расход. `null` у идущей — итога у неё ещё не было. */
    val profit: Int? get() = if (status == BatchStatus.Active) null else income - invested

    val hasEggPrice: Boolean get() = eggPrice != null
    val hasChickPrice: Boolean get() = chickPrice != null

    /** Есть ли о чём говорить: заполнена хотя бы одна из двух цен. */
    val known: Boolean get() = hasEggPrice || hasChickPrice
}

/**
 * Итоги по инкубатору.
 *
 * [balance] считается по всем закладкам сразу, включая идущие: деньги за яйца, которые
 * прямо сейчас лежат в инкубаторе, уже потрачены, и «текущий баланс», который их не
 * видит, показывал бы хозяйство богаче, чем оно есть. Обратная сторона — пока закладка
 * идёт, баланс ею только придавливается: доход придёт при завершении. Насколько
 * придавлен, говорит [activeInvested], и экран это печатает под балансом — та же
 * честность про знаменатель, что и в «Статистике».
 *
 * Цена самого инкубатора **входит** и в [expense], и в [balance]: за технику заплатили
 * теми же деньгами, и хозяйство выходит в плюс не раньше, чем отобьёт её тоже. Плата за
 * это — первая закладка любого хозяйства выглядит убыточной, пока инкубатор не окупился;
 * поэтому расход на экране подписан «инкубатор + яйца», а не молча складывает два разных
 * по природе числа. Слагаемые доступны порознь: [invested] — только яйца,
 * [incubatorPrice] — только техника.
 *
 * Из этого же следует [payback]: он считается по [profitOnBatches] — прибыли от закладок
 * до вычета техники, — а не по [balance], в котором техника уже вычтена. Связь между
 * ними ровная и её стоит держать в голове: `balance >= 0` тогда и только тогда, когда
 * `payback >= 100`.
 *
 * Все три средних ([expensePerEgg], [avgChickPrice], [chickCost]) — целые рубли: копеек
 * в приложении нет нигде, ни в форме закладки, ни в плитках-итогах под ней. Инкубатор ни
 * в одно из них не входит: это цены за штуку, а техника покупается не на штуку.
 */
data class IncubatorFinance(
    /** Во что обошёлся сам инкубатор; ноль — цену не указывали. */
    val incubatorPrice: Int = 0,
    /** Выручено за птенцов по всем закладкам. */
    val income: Int = 0,
    /**
     * Потрачено на яйца по всем закладкам, включая идущие.
     *
     * Это только яйца. Общий расход хозяйства — [expense], он же прибавляет сюда цену
     * инкубатора; хранится слагаемое, а не сумма, потому что «расход на яйцо» и
     * себестоимость птенца считаются именно от него.
     */
    val eggsExpense: Int = 0,
    /** Деньги за невылупившиеся яйца завершённых закладок. Часть [eggsExpense], не добавка. */
    val lost: Int = 0,
    /**
     * Сколько яиц за этими деньгами стоит.
     *
     * Считается здесь, а не делением [lost] на [expensePerEgg] на экране: закладки
     * покупались по разным ценам, и обратное деление на среднюю даёт число, не имеющее
     * отношения к действительности — у инкубатора с дорогими гусями и дешёвыми
     * перепелами оно завышено в полтора раза.
     */
    val lostEggs: Int = 0,
    /** Сколько денег лежит в ещё идущих закладках. */
    val activeInvested: Int = 0,
    /** Финансы каждой закладки: идущие сверху, завершённые — свежие раньше старых. */
    val batches: List<BatchFinance> = emptyList(),
    /** Яйца закладок с заполненной ценой — знаменатель [expensePerEgg]. */
    val pricedEggs: Int = 0,
    /** Птенцы закладок с заполненной ценой птенца — знаменатель [avgChickPrice]. */
    val pricedChicks: Int = 0,
    /** Вложено в завершённые закладки с известной ценой яйца — числитель [chickCost]. */
    val costOfHatched: Int = 0,
    /** Птенцы этих же закладок — знаменатель [chickCost]. */
    val hatchedWithCost: Int = 0,
    /** Закладок, у которых цену яиц не заполнили. */
    val batchesWithoutEggPrice: Int = 0,
    /** Завершённых закладок, у которых не заполнили цену птенцов. */
    val batchesWithoutChickPrice: Int = 0,
) {
    /**
     * Общий расход хозяйства: яйца всех закладок плюс цена самого инкубатора.
     *
     * Два слагаемых разной природы — расходуемое сырьё и разовая покупка техники, — и
     * экран обязан называть их обоими («инкубатор + яйца»). Складываются они потому, что
     * заплачено за то и другое одними деньгами: хозяйство не в плюсе, пока не вернуло
     * себе и то, и другое.
     */
    val expense: Int get() = eggsExpense + incubatorPrice

    /** Текущий баланс: доход минус весь расход, включая технику. */
    val balance: Int get() = income - expense

    /** «Вложено» — вторая половина расхода, без техники: деньги, ушедшие в яйца. */
    val invested: Int get() = eggsExpense

    /**
     * Прибыль от закладок до вычета техники — то, чем инкубатор себя отбивает.
     *
     * Ровно прежний баланс, до того как в него включили цену инкубатора. Отдельным
     * именем, а не выражением по месту: по нему считается [payback], и «прибыль минус
     * техника плюс техника» в формуле окупаемости читалось бы как ошибка.
     */
    val profitOnBatches: Int get() = income - eggsExpense

    /**
     * Расход на одно яйцо — средняя цена, по которой яйца покупали.
     *
     * Числитель — [eggsExpense], без техники: инкубатор куплен один раз и не дорожает
     * от того, что в него заложили ещё сотню яиц. Знаменатель — только яйца закладок с
     * заполненной ценой: закладка без цены внесла бы в него свои яйца, а в числитель
     * ноль, и средняя цена упала бы на ровном месте. `null` — цен нет ни у одной
     * закладки.
     */
    val expensePerEgg: Int? get() = if (pricedEggs > 0) eggsExpense / pricedEggs else null

    /** Средняя цена, по которой уходил птенец; `null` — цену птенцов ещё не вводили. */
    val avgChickPrice: Int? get() = if (pricedChicks > 0) income / pricedChicks else null

    /**
     * Себестоимость птенца: во что обошлись яйца завершённой закладки, делённые на её
     * птенцов. Невылупившиеся яйца входят в числитель — их цену и несут на себе те,
     * кто вылупился.
     *
     * Только завершённые, по той же причине, по какой [StatsSlice.rate] считается по
     * ним: у идущей закладки птенцов ноль, а деньги уже потрачены, и себестоимость
     * улетела бы в небо. `null` — завершённых с ценой и птенцами ещё нет.
     *
     * Цены инкубатора здесь нет, хотя в общий [expense] она входит: разовую покупку
     * пришлось бы делить на птенцов, которых у хозяйства ещё не было и ещё будет, и
     * себестоимость первого птенца равнялась бы цене инкубатора. Это цена яиц на голову,
     * и рядом с [avgChickPrice] она отвечает на вопрос «сколько я зарабатываю с птенца»;
     * вопрос «отбил ли я технику» отвечает [payback].
     */
    val chickCost: Int? get() = if (hatchedWithCost > 0) costOfHatched / hatchedWithCost else null

    /**
     * Какую долю своей цены инкубатор уже окупил, в процентах.
     *
     * Считается по [profitOnBatches], а не по [balance]: в балансе техника уже вычтена,
     * и делить его на её же цену значило бы вычесть её дважды. И не по [income] —
     * техника отбивается прибылью, а не выручкой, из которой ещё надо вычесть яйца.
     *
     * Убыток — это ноль процентов, а не минус: «минус 40 % окупаемости» не значит
     * ничего. Больше ста процентов, наоборот, оставлено как есть — это ровно тот случай,
     * ради которого показатель и заведён, и ровно та отметка, на которой [balance]
     * переходит через ноль. `null` — цену инкубатора не указывали.
     */
    val payback: Int? get() =
        if (incubatorPrice > 0) profitOnBatches.coerceAtLeast(0) * 100 / incubatorPrice else null

    /** Есть ли вообще о чём говорить: хоть где-то введена хоть одна цена. */
    val hasMoney: Boolean get() = income > 0 || expense > 0
}

/**
 * Считает финансы инкубатора по его цене и его закладкам.
 *
 * Спрятанные в архив закладки приходят сюда наравне с остальными: спрятать закладку
 * значит убрать её с глаз, а не отменить потраченные на неё деньги.
 */
fun incubatorFinance(incubatorPrice: Int, batches: List<Batch>): IncubatorFinance {
    if (batches.isEmpty()) {
        return IncubatorFinance(incubatorPrice = incubatorPrice.coerceAtLeast(0))
    }

    val rows = batches
        .map { batchFinanceOf(it) }
        // Идущие сверху — их деньги ещё в работе и интересны раньше прошлогодних;
        // завершённые дальше, свежие раньше старых. Дата — текст «dd.MM.yyyy»,
        // сортировать его как строку нельзя, поэтому пересобираем в ISO.
        .sortedWith(
            compareByDescending<BatchFinance> { it.status == BatchStatus.Active }
                .thenByDescending { isoOrBlank(it.dateEnd) }
        )

    val finished = rows.filter { it.status != BatchStatus.Active }
    val withEggPrice = rows.filter { it.hasEggPrice }
    val finishedWithEggPrice = finished.filter { it.hasEggPrice }

    return IncubatorFinance(
        incubatorPrice = incubatorPrice.coerceAtLeast(0),
        income = rows.sumOf { it.income },
        eggsExpense = rows.sumOf { it.invested },
        lost = rows.sumOf { it.lost },
        lostEggs = finishedWithEggPrice.sumOf { (it.eggs - it.hatched).coerceAtLeast(0) },
        activeInvested = rows.filter { it.status == BatchStatus.Active }.sumOf { it.invested },
        batches = rows,
        pricedEggs = withEggPrice.sumOf { it.eggs },
        pricedChicks = rows.filter { it.hasChickPrice }.sumOf { it.hatched },
        costOfHatched = finishedWithEggPrice.sumOf { it.invested },
        hatchedWithCost = finishedWithEggPrice.sumOf { it.hatched },
        batchesWithoutEggPrice = rows.count { !it.hasEggPrice },
        // Только завершённые: у идущей птенцов ещё нет, и цены им взяться неоткуда —
        // упрекать её в незаполненной графе не за что.
        batchesWithoutChickPrice = finished.count { !it.hasChickPrice },
    )
}

/**
 * Деньги одной закладки — строка «Финансов». Открыта наружу ради [hatchSummaryOf]:
 * поздравление после вывода печатает вложения и выручку, и считать их должно то же
 * место, что и вкладка, иначе через минуту та назовёт другую сумму.
 */
fun batchFinanceOf(batch: Batch): BatchFinance {
    val finished = batch.status != BatchStatus.Active
    val eggPrice = eggPriceOf(batch)
    val chickPrice = chickPriceOf(batch)
    return BatchFinance(
        batchId = batch.id,
        title = batch.title.ifBlank { batch.type },
        species = batch.type,
        breed = batch.breed,
        status = batch.status,
        dateEnd = batch.dateEnd,
        eggs = batch.eggAll,
        hatched = batch.eggAllEND,
        eggPrice = eggPrice,
        chickPrice = chickPrice,
        // Берём сохранённое число, а не цену яйца, умноженную обратно: при цене за всю
        // партию деление на яйца теряет остаток, и «вложено» разошлось бы с тем, что
        // человек своими руками ввёл в форме.
        invested = if (batch.price <= 0) 0 else {
            if (batch.pricePerEgg) batch.price * batch.eggAll else batch.price
        },
        income = if (batch.chickPrice <= 0) 0 else {
            if (batch.chickPricePerHead) batch.chickPrice * batch.eggAllEND else batch.chickPrice
        },
        // Потери есть только у завершённой: у идущей невылупившихся яиц пока нет —
        // есть яйца, которые ещё не вылупились, а это другое.
        lost = if (finished && eggPrice != null) {
            eggPrice * (batch.eggAll - batch.eggAllEND).coerceAtLeast(0)
        } else 0,
    )
}

/**
 * Цена за штуку: как ввели, либо доля от цены за всё. `null` — не вводили.
 *
 * Одна функция на оба случая (яйца и птенцы): вопрос один и тот же, и две его копии
 * разошлись бы на первом же делении на ноль.
 */
private fun unitPrice(price: Int, perUnit: Boolean, count: Int): Int? = when {
    price <= 0 -> null
    perUnit -> price
    count > 0 -> price / count
    else -> null
}

/** Цена одного яйца закладки: как ввели, либо доля от цены за партию. */
private fun eggPriceOf(batch: Batch): Int? =
    unitPrice(batch.price, batch.pricePerEgg, batch.eggAll)

/** Цена одного птенца закладки: как ввели, либо доля от цены за всех. */
private fun chickPriceOf(batch: Batch): Int? =
    unitPrice(batch.chickPrice, batch.chickPricePerHead, batch.eggAllEND)
