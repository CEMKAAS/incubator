package ru.zaroslikov.incubator.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling

/**
 * Закрепляет допущение, на котором стоит экран «Профиль».
 *
 * Профиль не считает ничего своего: он зовёт те же [incubatorStats] и [incubatorFinance],
 * что и экран одного инкубатора, подавая им закладки всех устройств сразу, а вместо цены
 * инкубатора — сумму цен всех. Это и есть допущение: что обе функции не знают и не хотят
 * знать, из скольких устройств собран поданный им список.
 *
 * Проверять его стоит, потому что провалиться оно может тихо. Все числа останутся
 * правдоподобными; разойдётся только сводка с суммой частей, и увидит это лишь тот, кто
 * сложит инкубаторы в уме. Ради этого же допущения второй реализации «того же счёта, но
 * по всем» не заведено вовсе.
 */
class AllIncubatorsTest {

    private fun batch(
        id: Long,
        incubatorId: Long,
        type: String = "Курицы",
        breed: String = "",
        eggAll: Int = 0,
        eggAllEND: Int = 0,
        arhive: String = "1",
        price: Int = 0,
        chickPrice: Int = 0,
        eggRejected: Int = 0,
    ) = Batch(
        id = id,
        title = "Закладка $id",
        type = type,
        data = "01.01.2026",
        eggAll = eggAll,
        eggAllEND = eggAllEND,
        airing = "0",
        over = "0",
        arhive = arhive,
        dateEnd = "01.02.2026",
        note = "",
        incubatorId = incubatorId,
        breed = breed,
        price = price,
        pricePerEgg = true,
        chickPrice = chickPrice,
        chickPricePerHead = true,
        eggRejected = eggRejected,
    )

    /** Инкубатор №1: куры, дорогие яйца, вывелось хорошо. */
    private val first = listOf(
        batch(1, incubatorId = 1, eggAll = 100, eggAllEND = 80, price = 30, chickPrice = 120),
        batch(2, incubatorId = 1, eggAll = 50, eggAllEND = 40, price = 30, chickPrice = 120),
    )

    /** Инкубатор №2: гуси, дешевле и хуже, плюс одна ещё идущая закладка. */
    private val second = listOf(
        batch(3, incubatorId = 2, type = "Гуси", eggAll = 60, eggAllEND = 15, price = 10, chickPrice = 200),
        batch(4, incubatorId = 2, type = "Гуси", eggAll = 40, arhive = "0", price = 10),
    )

    private val firstPrice = 8_000
    private val secondPrice = 5_000

    @Test
    fun `расход, доход и баланс хозяйства равны сумме своих инкубаторов`() {
        val a = incubatorFinance(firstPrice, first)
        val b = incubatorFinance(secondPrice, second)
        val all = incubatorFinance(firstPrice + secondPrice, first + second)

        assertEquals(a.income + b.income, all.income)
        assertEquals(a.eggsExpense + b.eggsExpense, all.eggsExpense)
        assertEquals(a.lost + b.lost, all.lost)
        assertEquals(a.activeInvested + b.activeInvested, all.activeInvested)

        // Главное: техника обоих устройств вошла в расход ровно один раз каждая.
        assertEquals(a.expense + b.expense, all.expense)
        assertEquals(a.balance + b.balance, all.balance)
        assertEquals(first.size + second.size, all.batches.size)
    }

    /**
     * Окупаемость по хозяйству считается от общей прибыли и общей цены техники — то
     * есть отвечает на вопрос «отбило ли хозяйство свои инкубаторы», а не «отбил ли
     * каждый свой». Это разные вопросы, и складывать проценты по устройствам нельзя.
     *
     * Заодно проверяется связка, ради которой окупаемость и считается по
     * [IncubatorFinance.profitOnBatches], а не по балансу: `balance >= 0` тогда и только
     * тогда, когда `payback >= 100`.
     */
    @Test
    fun `окупаемость хозяйства считается от суммарной цены техники`() {
        val all = incubatorFinance(firstPrice + secondPrice, first + second)

        // Числа посчитаны руками, а не той же формулой, что и в коде: выражение
        // `profitOnBatches * 100 / incubatorPrice`, подставленное в ожидание, проверяло
        // бы лишь то, что цена сложилась, и молча принимало бы любую ошибку в самой
        // формуле. Расклад: яйца 3 000 + 1 500 + 600 + 400 = 5 500, птенцы
        // 9 600 + 4 800 + 3 000 = 17 400, техника 8 000 + 5 000 = 13 000.
        assertEquals(5_500, all.eggsExpense)
        assertEquals(17_400, all.income)
        assertEquals(18_500, all.expense)
        assertEquals(11_900, all.profitOnBatches)

        // 11 900 из 13 000 — 91 % (целочисленно), и хозяйство ещё в минусе на 1 100.
        assertEquals(91, all.payback)
        assertEquals(-1_100, all.balance)
    }

    /**
     * Связка, ради которой окупаемость считается по [IncubatorFinance.profitOnBatches],
     * а не по балансу: `balance >= 0` тогда и только тогда, когда `payback >= 100`.
     *
     * Проверяется на обеих сторонах порога, иначе утверждение прошло бы и на коде,
     * который всегда отвечает «нет».
     */
    @Test
    fun `баланс переходит через ноль ровно на ста процентах окупаемости`() {
        val cheap = incubatorFinance(1_000, first + second)
        val dear = incubatorFinance(firstPrice + secondPrice, first + second)

        // Та же прибыль 11 900 против техники за 1 000 — окупилась с лихвой.
        assertEquals(1_190, cheap.payback)
        assertEquals(10_900, cheap.balance)

        assertEquals(true, cheap.balance >= 0 && (cheap.payback ?: 0) >= 100)
        assertEquals(false, dear.balance >= 0 || (dear.payback ?: 0) >= 100)
    }

    /**
     * Средняя цена яйца по хозяйству — не среднее двух средних.
     *
     * Инкубаторы покупали яйца по 30 и по 10 рублей, и «среднее из 30 и 10» дало бы 20 —
     * цену, по которой не куплено ни одного яйца. Правильный ответ взвешен по количеству,
     * и он получается сам собой ровно потому, что профиль подаёт функции все закладки, а
     * не складывает готовые ответы по устройствам.
     */
    @Test
    fun `средняя цена яйца взвешена по количеству, а не усреднена по инкубаторам`() {
        val a = incubatorFinance(firstPrice, first)
        val b = incubatorFinance(secondPrice, second)
        val all = incubatorFinance(firstPrice + secondPrice, first + second)

        assertEquals(30, a.expensePerEgg)
        assertEquals(10, b.expensePerEgg)

        val naive = ((a.expensePerEgg!! + b.expensePerEgg!!) / 2)
        assertEquals(20, naive)
        assertNotEquals(naive, all.expensePerEgg)

        // 150 яиц по 30 и 100 по 10 — это 5 500 ₽ на 250 яиц, то есть 22.
        assertEquals(22, all.expensePerEgg)
    }

    /**
     * Процент вывода по хозяйству — тоже взвешенный, и тоже только по завершённым.
     *
     * Идущая гусиная закладка вносит свои 40 яиц в «всего», но не в знаменатель
     * эффективности: птенцов у неё ещё нет. Ошибка здесь стоила бы дороже всего — она
     * занизила бы вывод хозяйства, ничего при этом не сломав.
     */
    @Test
    fun `эффективность хозяйства взвешена и считается только по завершённым`() {
        val all = incubatorStats(first + second, emptyList())

        assertEquals(250, all.totalEggs)
        assertEquals(135, all.hatched)

        // Завершены три закладки: 100 + 50 + 60 = 210 яиц, 80 + 40 + 15 = 135 птенцов.
        assertEquals(210, all.total.finishedEggs)
        assertEquals(135, all.total.finishedHatched)
        assertEquals(135 * 100 / 210, all.hatchRate)

        // Идущая закладка видна отдельно — экран обязан назвать этот знаменатель.
        assertEquals(40, all.total.activeEggs)
        assertEquals(1, all.total.activeBatches)
    }

    /** Разрезы по видам собираются по всем устройствам сразу, а не по каждому отдельно. */
    @Test
    fun `виды птицы собираются со всех инкубаторов в один разрез`() {
        val all = incubatorStats(first + second, emptyList())

        assertEquals(listOf("Курицы", "Гуси"), all.bySpecies.map { it.slice.name })
        assertEquals(150, all.bySpecies.first { it.slice.name == "Курицы" }.slice.eggs)
        assertEquals(100, all.bySpecies.first { it.slice.name == "Гуси" }.slice.eggs)
    }

    /**
     * Отбраковка складывается из двух учётов и по хозяйству тоже: графа закладки плюс
     * итоги её овоскопирований. Овоскопирования приходят от закладок разных устройств
     * вперемешку — раскладываются они по `idPT`, и чужое устройство им не помеха.
     */
    @Test
    fun `отбраковка по хозяйству складывает графу закладки и овоскопирования`() {
        val batches = listOf(
            batch(1, incubatorId = 1, eggAll = 100, eggAllEND = 80, eggRejected = 5),
            batch(3, incubatorId = 2, type = "Гуси", eggAll = 60, eggAllEND = 15, eggRejected = 2),
        )
        val candlings = listOf(
            Candling(idPT = 1, day = 7, date = "08.01.2026", rejected = 8),
            Candling(idPT = 3, day = 9, date = "10.01.2026", rejected = 20),
        )

        val all = incubatorStats(batches, candlings)

        assertEquals(5 + 2 + 8 + 20, all.rejected)
    }
}
