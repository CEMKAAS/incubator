package ru.zaroslikov.incubator.domain.stats

import ru.zaroslikov.incubator.domain.model.Batch

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
) {
    /** Вывод в процентах от заложенного — тот же знаменатель, что у `StatsSlice.rate`. */
    val rate: Int get() = if (eggs > 0) hatched * 100 / eggs else 0

    /** Хоть одна цена введена — есть что сказать о деньгах. */
    val hasMoney: Boolean get() = hasEggPrice || hasChickPrice

    /** Выручка минус вложения; `null`, пока одной из цен нет. */
    val profit: Int? get() = if (hasEggPrice && hasChickPrice) income - invested else null
}

/**
 * Итог закладки [batch] по её собственным числам.
 *
 * @param rejected сколько яиц убрано за всё время — овоскопирования плюс отбраковка,
 *        вписанная руками; тот же двойной учёт, что у «Осталось» в шторке закладки.
 * @param termDays срок вида по каталогу.
 */
fun hatchSummaryOf(batch: Batch, rejected: Int, termDays: Int?): HatchSummary {
    val finance = batchFinanceOf(batch)
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
    )
}
