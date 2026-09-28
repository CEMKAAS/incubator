package ru.zaroslikov.incubator.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus

/**
 * Закрепляет арифметику вкладки «Финансы».
 *
 * Проверять здесь есть что по той же причине, что и в [IncubatorStatsTest], только цена
 * ошибки выше: у денег два способа солгать незаметно. Первый — знаменатель: закладка без
 * заполненной цены вносит в расход ноль, и если пустить её яйца в делитель «расхода на
 * яйцо», средняя цена упадёт на ровном месте. Второй — двойной счёт: потери сидят
 * **внутри** расхода, и стоит прибавить их к нему, как одни и те же яйца окажутся
 * оплачены дважды.
 */
class IncubatorFinanceTest {

    private fun batch(
        id: Long,
        type: String = "Курицы",
        eggAll: Int = 0,
        eggAllEND: Int = 0,
        arhive: String = "1",
        endReason: String = "",
        dateEnd: String = "01.01.2026",
        price: Int = 0,
        pricePerEgg: Boolean = true,
        chickPrice: Int = 0,
        chickPricePerHead: Boolean = true,
        title: String = "Закладка $id",
    ) = Batch(
        id = id,
        title = title,
        type = type,
        data = "01.01.2026",
        eggAll = eggAll,
        eggAllEND = eggAllEND,
        airing = "0",
        over = "0",
        arhive = arhive,
        dateEnd = dateEnd,
        note = "",
        incubatorId = 1,
        price = price,
        pricePerEgg = pricePerEgg,
        endReason = endReason,
        chickPrice = chickPrice,
        chickPricePerHead = chickPricePerHead,
    )

    @Test
    fun `порода закладки едет в строку как есть`() {
        val single = batch(id = 1, eggAll = 30, eggAllEND = 25, price = 20, chickPrice = 300)
            .copy(breed = "Ломан Браун")

        val row = incubatorFinance(0, listOf(single)).batches.single()

        assertEquals("Ломан Браун", row.breed)
        assertEquals(600, row.invested)
        assertEquals(7_500, row.income)
    }

    @Test
    fun `инкубатор без закладок помнит только свою цену`() {
        val finance = incubatorFinance(8_000, emptyList())

        assertEquals(8_000, finance.incubatorPrice)
        assertEquals(0, finance.income)
        assertEquals(0, finance.eggsExpense)
        // Купленная техника — уже расход, и баланс с самого начала на неё в минусе.
        assertEquals(8_000, finance.expense)
        assertEquals(-8_000, finance.balance)
        assertNull(finance.expensePerEgg)
        assertNull(finance.avgChickPrice)
        assertNull(finance.chickCost)
        assertEquals(0, finance.payback)
        assertTrue(finance.batches.isEmpty())
        assertTrue(finance.hasMoney)
    }

    @Test
    fun `совсем пустой инкубатор не считает себя денежным`() {
        assertFalse(incubatorFinance(0, emptyList()).hasMoney)
    }

    @Test
    fun `завершённая закладка складывается из двух введённых цен`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(1, eggAll = 200, eggAllEND = 143, price = 45, chickPrice = 250),
            ),
        )

        val row = finance.batches.single()
        assertEquals(45, row.eggPrice)
        assertEquals(250, row.chickPrice)
        assertEquals(200 * 45, row.invested)
        assertEquals(143 * 250, row.income)
        // Потери — цена яиц, не ставших птенцами: 57 штук по 45.
        assertEquals(57 * 45, row.lost)
        assertEquals(143 * 250 - 200 * 45, row.profit)
        assertEquals(row.invested, row.expense)
    }

    @Test
    fun `цена за партию делится на яйца, а вложено остаётся введённым числом`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            // 9 100 за 200 яиц: 45 рублей за яйцо и 100 рублей в остатке.
            batches = listOf(
                batch(1, eggAll = 200, eggAllEND = 143, price = 9_100, pricePerEgg = false),
            ),
        )

        val row = finance.batches.single()
        assertEquals(45, row.eggPrice)
        // Вложено — ровно то, что ввели: обратное умножение потеряло бы сотню рублей.
        assertEquals(9_100, row.invested)
        assertEquals(57 * 45, row.lost)
    }

    @Test
    fun `цена за всех птенцов не умножается на их число`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(
                    1, eggAll = 100, eggAllEND = 80,
                    chickPrice = 20_000, chickPricePerHead = false,
                ),
            ),
        )

        val row = finance.batches.single()
        assertEquals(20_000, row.income)
        assertEquals(250, row.chickPrice)
    }

    @Test
    fun `у идущей закладки нет ни итога, ни потерь`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(batch(1, eggAll = 100, arhive = "0", price = 50)),
        )

        val row = finance.batches.single()
        assertEquals(BatchStatus.Active, row.status)
        // Деньги потрачены и в расход входят — а итога у закладки ещё не было.
        assertNull(row.profit)
        assertEquals(0, row.lost)
        assertEquals(5_000, row.invested)
        assertEquals(5_000, finance.expense)
        assertEquals(5_000, finance.activeInvested)
        assertEquals(-5_000, finance.balance)
    }

    @Test
    fun `прерванная закладка теряет всю свою стоимость`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(
                    1, eggAll = 100, eggAllEND = 0,
                    endReason = "Отключили свет", price = 50,
                ),
            ),
        )

        val row = finance.batches.single()
        assertEquals(BatchStatus.Stopped, row.status)
        assertEquals(5_000, row.lost)
        assertEquals(-5_000, row.profit)
    }

    @Test
    fun `закладка без цены не попадает в знаменатель расхода на яйцо`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(1, eggAll = 100, eggAllEND = 90, price = 50, chickPrice = 200),
                // Цену не указывали: её яйца в среднюю цену яйца входить не должны.
                batch(2, eggAll = 100, eggAllEND = 90),
            ),
        )

        assertEquals(5_000, finance.expense)
        assertEquals(100, finance.pricedEggs)
        // 5 000 / 100, а не 5 000 / 200: вторая закладка про цену ничего не говорит.
        assertEquals(50, finance.expensePerEgg)
        assertEquals(1, finance.batchesWithoutEggPrice)
        assertEquals(1, finance.batchesWithoutChickPrice)
    }

    @Test
    fun `себестоимость птенца считается только по завершённым закладкам`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(1, eggAll = 100, eggAllEND = 80, price = 40),
                // Идущая: деньги уже вложены, птенцов ещё нет. Попади она в счёт —
                // себестоимость выросла бы вдвое на пустом месте.
                batch(2, eggAll = 100, arhive = "0", price = 40),
            ),
        )

        assertEquals(4_000, finance.costOfHatched)
        assertEquals(80, finance.hatchedWithCost)
        assertEquals(50, finance.chickCost)
        // Расход при этом считает обе: яйца второй закладки правда куплены.
        assertEquals(8_000, finance.expense)
    }

    @Test
    fun `потери не прибавляются к расходу, а сидят внутри него`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(batch(1, eggAll = 100, eggAllEND = 60, price = 50, chickPrice = 300)),
        )

        assertEquals(5_000, finance.expense)
        assertEquals(2_000, finance.lost)
        // Баланс — доход минус расход. Потери в него отдельным слагаемым не входят.
        assertEquals(18_000 - 5_000, finance.balance)
    }

    @Test
    fun `невылупившиеся яйца считаются штуками, а не делением денег на среднюю цену`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                // Дорогие гуси и дешёвые перепела: средняя цена яйца не равна ни той,
                // ни другой, и обратное деление потерь на неё даёт число из ниоткуда.
                batch(1, eggAll = 30, eggAllEND = 20, price = 150),
                batch(2, eggAll = 150, eggAllEND = 128, price = 15),
            ),
        )

        assertEquals(10 * 150 + 22 * 15, finance.lost)
        assertEquals(32, finance.lostEggs)
        // Ровно та ошибка, ради которой заведено поле: деньги, делённые на среднюю,
        // дали бы почти вдвое больше яиц, чем не вылупилось на самом деле.
        assertTrue(finance.lost / finance.expensePerEgg!! > finance.lostEggs)
    }

    @Test
    fun `цена инкубатора входит в расход и в баланс, но не в цену яйца`() {
        val finance = incubatorFinance(
            incubatorPrice = 8_000,
            batches = listOf(batch(1, eggAll = 100, eggAllEND = 80, price = 50, chickPrice = 200)),
        )

        assertEquals(16_000, finance.income)
        // Слагаемые доступны порознь — экран называет оба.
        assertEquals(5_000, finance.eggsExpense)
        assertEquals(5_000, finance.invested)
        assertEquals(13_000, finance.expense)
        assertEquals(3_000, finance.balance)
        // Цена за штуку техники не знает: инкубатор куплен один раз, а не на яйцо.
        assertEquals(50, finance.expensePerEgg)
        assertEquals(62, finance.chickCost)
        // Окупаемость — по прибыли до вычета техники, иначе её вычли бы дважды.
        assertEquals(11_000, finance.profitOnBatches)
        assertEquals(137, finance.payback)
    }

    @Test
    fun `баланс переходит через ноль ровно на ста процентах окупаемости`() {
        // Прибыль от закладок равна цене инкубатора: окупился в точности, баланс — ноль.
        val exactly = incubatorFinance(
            incubatorPrice = 8_000,
            batches = listOf(batch(1, eggAll = 100, eggAllEND = 65, price = 50, chickPrice = 200)),
        )
        assertEquals(8_000, exactly.profitOnBatches)
        assertEquals(100, exactly.payback)
        assertEquals(0, exactly.balance)

        // Один птенец сверх того — и баланс, и окупаемость уходят вверх вместе.
        val more = incubatorFinance(
            incubatorPrice = 8_000,
            batches = listOf(batch(1, eggAll = 100, eggAllEND = 66, price = 50, chickPrice = 200)),
        )
        assertTrue(more.balance > 0)
        assertTrue(more.payback!! > 100)
    }

    @Test
    fun `убыточное хозяйство показывает ноль окупаемости, а не минус`() {
        val finance = incubatorFinance(
            incubatorPrice = 8_000,
            batches = listOf(batch(1, eggAll = 100, eggAllEND = 0, price = 50)),
        )

        assertEquals(-13_000, finance.balance)
        assertEquals(-5_000, finance.profitOnBatches)
        assertEquals(0, finance.payback)
    }

    @Test
    fun `средняя цена птенца считается по закладкам с указанной ценой`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(1, eggAll = 100, eggAllEND = 80, chickPrice = 200),
                // Птенцы есть, цены нет — в среднюю не идут ни рублём, ни головой.
                batch(2, eggAll = 100, eggAllEND = 90),
            ),
        )

        assertEquals(16_000, finance.income)
        assertEquals(80, finance.pricedChicks)
        assertEquals(200, finance.avgChickPrice)
    }

    @Test
    fun `идущие закладки идут первыми, завершённые — свежие раньше старых`() {
        val finance = incubatorFinance(
            incubatorPrice = 0,
            batches = listOf(
                batch(1, dateEnd = "01.02.2026"),
                batch(2, dateEnd = "", arhive = "0"),
                batch(3, dateEnd = "01.03.2026"),
                // Пустая дата у завершённой — уезжает вниз, а не наверх.
                batch(4, dateEnd = ""),
            ),
        )

        assertEquals(listOf(2L, 3L, 1L, 4L), finance.batches.map { it.batchId })
    }

    @Test
    fun `закладка без обеих цен помечена как безденежная`() {
        val finance = incubatorFinance(0, listOf(batch(1, eggAll = 100, eggAllEND = 90)))

        val row = finance.batches.single()
        assertFalse(row.known)
        assertNull(row.eggPrice)
        assertNull(row.chickPrice)
        assertEquals(0, row.invested)
        assertEquals(0, row.income)
        assertEquals(0, row.lost)
    }

    @Test
    fun `битые данные не роняют счёт`() {
        val finance = incubatorFinance(
            incubatorPrice = -100,
            batches = listOf(
                // Птенцов больше, чем яиц: так в базе быть не должно, но бывает.
                batch(1, eggAll = 10, eggAllEND = 20, price = 50, chickPrice = 100),
                // Цена за партию при нулевом счёте яиц — делить не на что.
                batch(2, eggAll = 0, price = 500, pricePerEgg = false),
            ),
        )

        assertEquals(0, finance.incubatorPrice)
        assertEquals(0, finance.batches.first { it.batchId == 1L }.lost)
        assertNull(finance.batches.first { it.batchId == 2L }.eggPrice)
        assertNull(finance.payback)
    }
}
