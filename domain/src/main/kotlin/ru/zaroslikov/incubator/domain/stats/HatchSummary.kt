package ru.zaroslikov.incubator.domain.stats

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.status

/**
 * Краткий итог закладки, доведённой до срока, — то, что показывает поздравление сразу
 * после того, как птенцов посчитали.
 *
 * Здесь, а не в композабле, по той же причине, что и [IncubatorStats]: сложить яйца и
 * поделить проценты — арифметика над закладкой, и её закрепляет `HatchSummaryTest` на
 * обычной JVM. Цифры те же, что покажут «Статистика» и «Финансы» через минуту:
 * [rate] считается как `StatsSlice.rate`, деньги идут через [batchFinanceOf] — второй
 * подсчёт того же самого разошёлся бы с первым на первой же правке формулы.
 *
 * [termDays] — срок вида по каталогу; `null`, если вид неизвестен. [dateEnd] — дата
 * вывода «дд.ММ.гггг», как её записали в закладку: её же увозит в «Моё хозяйство»
 * `FarmLink` как дату поступления птенцов.
 *
 * Деньги показываются только те, что вводили: [invested] — при [hasEggPrice],
 * [income] — при [hasChickPrice], а [profit] — только когда есть обе цены. Разница
 * с одним известным слагаемым — не итог, а выдумка: без цены яиц «прибыль» равна
 * выручке, без цены птенцов — убытку на всю закладку.
 *
 * **Та же сводка служит и прерванной закладке** — карточке «Инкубация прервана», без
 * салюта. Её отличает [endReason]: непустая причина и есть «прервана» ([stopped]), ровно
 * как у `Batch.status`. Птенцов у такой нет по построению, [rejected] — то, что убрали до
 * остановки, а [stoppedDay] — день, на котором её остановили.
 */
data class HatchSummary(
    val batchId: Long,
    val title: String,
    val species: String,
    val breed: String,
    val eggs: Int,
    val rejected: Int,
    val hatched: Int,
    val termDays: Int?,
    val invested: Int,
    val income: Int,
    val hasEggPrice: Boolean,
    val hasChickPrice: Boolean,
    val dateEnd: String = "",
    /** Свет закладки в рублях — та же доля, что в «Финансах»; `null` — посчитать не из чего. */
    val electricity: Int? = null,
    /** Киловатт-часы за [electricity]. */
    val kwh: Double? = null,
    /** Причина досрочного завершения; пусто — закладка дошла до срока. */
    val endReason: String = "",
    /** День инкубации, на котором закладку прервали; `null` — в срок или день неизвестен. */
    val stoppedDay: Int? = null,
) {
    /** Прервана досрочно: итог — не вывод, а то, что ушло в расход. */
    val stopped: Boolean get() = endReason.isNotBlank()

    /** Сколько яиц лежало в инкубаторе к концу — заложено минус убранное раньше. */
    val remaining: Int get() = (eggs - rejected).coerceAtLeast(0)

    /** Вывод в процентах от заложенного — тот же знаменатель, что у `StatsSlice.rate`. */
    val rate: Int get() = if (eggs > 0) hatched * 100 / eggs else 0

    /** Хоть одна цена введена — есть что сказать о деньгах. */
    val hasMoney: Boolean get() = hasEggPrice || hasChickPrice || electricity != null

    /**
     * Выручка минус вложения и свет; `null`, пока одной из цен нет. Свет вычитается, когда
     * он посчитан, — как в `BatchFinance.profit`, иначе итог поздравления разошёлся бы с
     * итогом закладки в «Финансах».
     */
    val profit: Int? get() =
        if (hasEggPrice && hasChickPrice) income - invested - (electricity ?: 0) else null
}

/**
 * Итог закладки [batch] по её собственным числам.
 *
 * @param rejected сколько яиц убрано за всё время — овоскопирования плюс отбраковка,
 *        вписанная руками; тот же двойной учёт, что у «Осталось» в шторке закладки.
 * @param termDays срок вида по каталогу.
 * @param electricity её доля счёта за свет (`electricityCosts`); `null` — не считали.
 * @param stoppedDay день, на котором закладку прервали; у дошедшей до срока не нужен
 *        и в сводку не попадает.
 */
fun hatchSummaryOf(
    batch: Batch,
    rejected: Int,
    termDays: Int?,
    electricity: ElectricityCost? = null,
    stoppedDay: Int? = null,
): HatchSummary {
    val finance = batchFinanceOf(batch, electricity)
    // По статусу, а не по одной причине: у него `arhive` проверяется первым, и причина,
    // случайно оставшаяся у закладки в срок, не делает сводку «прерванной».
    val stopped = batch.status == BatchStatus.Stopped
    return HatchSummary(
        batchId = batch.id,
        title = batch.title,
        species = batch.type,
        breed = batch.breed,
        eggs = batch.eggAll,
        rejected = rejected.coerceAtLeast(0),
        hatched = batch.eggAllEND,
        termDays = termDays,
        invested = finance.invested,
        income = finance.income,
        hasEggPrice = finance.hasEggPrice,
        hasChickPrice = finance.hasChickPrice,
        dateEnd = batch.dateEnd,
        electricity = finance.electricity,
        kwh = finance.kwh,
        endReason = if (stopped) batch.endReason else "",
        stoppedDay = stoppedDay?.takeIf { stopped && it > 0 },
    )
}

/**
 * Итог партии — нескольких закладок, заложенных одним нажатием на разные породы, —
 * одной сводкой: яйца, брак и птенцы складываются, срок берётся у первой (вид у партии
 * один).
 *
 * Деньги складываются только когда цена есть у **каждой** закладки: сумма с пропущенным
 * слагаемым — не сумма, и «вложено 900 ₽» по партии, где одну породу купили без цены,
 * читалось бы как полная стоимость лотка. Название и порода — первой закладки; кто
 * показывает партию, называет её сам.
 */
fun List<HatchSummary>.combined(): HatchSummary {
    require(isNotEmpty()) { "Партия не может быть пустой" }
    val first = first()
    val allEggPrices = all { it.hasEggPrice }
    val allChickPrices = all { it.hasChickPrice }
    // Свет — по той же мерке, что деньги: сумма только когда он посчитан у каждой.
    val allElectricity = all { it.electricity != null }
    return HatchSummary(
        batchId = 0,
        title = first.title,
        species = first.species,
        breed = "",
        eggs = sumOf { it.eggs },
        rejected = sumOf { it.rejected },
        hatched = sumOf { it.hatched },
        termDays = first.termDays,
        invested = if (allEggPrices) sumOf { it.invested } else 0,
        income = if (allChickPrices) sumOf { it.income } else 0,
        hasEggPrice = allEggPrices,
        hasChickPrice = allChickPrices,
        dateEnd = first.dateEnd,
        electricity = if (allElectricity) sumOf { it.electricity ?: 0 } else null,
        kwh = if (allElectricity) sumOf { it.kwh ?: 0.0 } else null,
    )
}
