package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.farm.FarmChicks

/** Закрепляет первую строку поздравления — для одной закладки и для партии пород. */
class HatchCelebrationTest {

    private fun summary(title: String, breed: String = "", eggs: Int, hatched: Int) = HatchSummary(
        batchId = 1,
        title = title,
        species = "Курицы",
        breed = breed,
        eggs = eggs,
        rejected = 0,
        hatched = hatched,
        termDays = 21,
        invested = 0,
        income = 0,
        hasEggPrice = false,
        hasChickPrice = false,
    )

    @Test
    fun `одна закладка — по названию, глагол и падежи по числам`() {
        assertEquals(
            "Закладка «Весенняя» доведена до срока: вывелся 21 птенец из 24 яиц.",
            celebrationLine(listOf(summary("Весенняя", eggs = 24, hatched = 21))),
        )
        assertEquals(
            "Закладка «Весенняя» доведена до срока: вывелось 11 птенцов из 21 яйца.",
            celebrationLine(listOf(summary("Весенняя", eggs = 21, hatched = 11))),
        )
    }

    @Test
    fun `без названия закладка зовётся по виду`() {
        assertEquals(
            "Закладка «Курицы» доведена до срока: вывелось 2 птенца из 5 яиц.",
            celebrationLine(listOf(summary("", eggs = 5, hatched = 2))),
        )
    }

    @Test
    fun `партия — общее имя без хвоста породы и число пород`() {
        val line = celebrationLine(
            listOf(
                summary("Весенняя — Хайсекс", breed = "Хайсекс", eggs = 20, hatched = 18),
                summary("Весенняя — Ломан", breed = "Ломан", eggs = 10, hatched = 5),
            ),
        )
        assertEquals(
            "Партия «Весенняя» доведена до срока: по 2 породам вывелось 23 птенца из 30 яиц.",
            line,
        )
    }

    private val chicks = FarmChicks(
        type = "Курицы", name = "Весенняя — Хайсекс", breed = "Хайсекс", count = 22, date = "29.09.2026",
    )

    @Test
    fun `кнопка хозяйства — у одной закладки действие, у партии порода и число`() {
        assertEquals("Добавить птенцов в «Моё хозяйство»", farmButtonLabel(chicks, several = false))
        assertEquals("Хайсекс, 22 птенца → в «Моё хозяйство»", farmButtonLabel(chicks, several = true))
        assertEquals(
            "Весенняя, 21 птенец → в «Моё хозяйство»",
            farmButtonLabel(chicks.copy(name = "Весенняя", breed = "", count = 21), several = true),
        )
    }

    private fun priced(breed: String, egg: Int?, chick: Int?) = summary("Весенняя — $breed", breed, eggs = 10, hatched = 8)
        .copy(invested = egg ?: 0, income = chick ?: 0, hasEggPrice = egg != null, hasChickPrice = chick != null)

    @Test
    fun `партия, где цена яиц только у второй породы, — выручка, сумма известных яиц и имя породы без цены`() {
        val view = profitView(listOf(priced("Хайсекс", egg = null, chick = 1000), priced("Ломан", egg = 300, chick = 800)))
        assertEquals(false, view.isProfit)
        assertEquals(1800, view.headline)
        assertEquals("выручка за птенцов", view.caption)
        assertEquals(300, view.eggs)
        assertEquals(
            "Во что обошлись заложенные яйца — по цене из формы закладки: за яйцо × заложено " +
                "или за всё сразу. У «Хайсекс» не указана стоимость яиц — их яйца не вычтены.",
            view.eggsHint,
        )
        assertEquals(
            "Прибыль не посчитать: у «Хайсекс» не указана стоимость яиц. Крупная цифра — " +
                "только выручка за птенцов, расходы из неё не вычтены.",
            view.explanation,
        )
    }

    @Test
    fun `обе цены у всех пород — прибыль партии`() {
        val view = profitView(listOf(priced("Хайсекс", egg = 200, chick = 1000), priced("Ломан", egg = 300, chick = 800)))
        assertEquals(true, view.isProfit)
        assertEquals(1300, view.headline)
        assertEquals("заработано на птенцах", view.caption)
        assertEquals("Выручка за птенцов минус стоимость яиц.", view.explanation)
    }

    @Test
    fun `одна закладка без цены яиц — без имён`() {
        val view = profitView(listOf(priced("Хайсекс", egg = null, chick = 1000)))
        assertEquals(1000, view.headline)
        assertEquals(null, view.eggs)
        assertEquals(
            "Прибыль не посчитать: не указана стоимость яиц. Крупная цифра — только выручка " +
                "за птенцов, расходы из неё не вычтены.",
            view.explanation,
        )
    }

    @Test
    fun `без цен птенцов — прочерк`() {
        val view = profitView(listOf(priced("Хайсекс", egg = 200, chick = null)))
        assertEquals(null, view.headline)
        assertEquals("заработок неизвестен", view.caption)
        assertEquals(200, view.eggs)
    }

    private fun stopped(day: Int? = 6, term: Int? = 21) = summary("Весенняя", eggs = 30, hatched = 0)
        .copy(rejected = 4, termDays = term, endReason = "Отключили свет", stoppedDay = day)

    @Test
    fun `прерванная — день остановки и срок, без дня просто досрочно`() {
        assertEquals("Закладка «Весенняя» остановлена на 6-й день из 21.", stoppedLine(stopped()))
        assertEquals("Закладка «Весенняя» остановлена на 6-й день.", stoppedLine(stopped(term = null)))
        assertEquals("Закладка «Весенняя» остановлена досрочно.", stoppedLine(stopped(day = null)))
        assertEquals(
            "Закладка «Курицы» остановлена на 6-й день из 21.",
            stoppedLine(stopped().copy(title = "")),
        )
    }

    @Test
    fun `расходы прерванной — яйца и свет с минусом, выручки нет`() {
        val view = lossView(
            listOf(stopped().copy(invested = 300, hasEggPrice = true, electricity = 120, kwh = 50.4)),
        )
        assertEquals(-420, view.headline)
        assertEquals("ушло в расход", view.caption)
        assertEquals(null, view.income)
        assertEquals(300, view.eggs)
        assertEquals(120, view.electricity)
    }

    @Test
    fun `расходы прерванной — только известное, а без цен прочерк`() {
        val onlyLight = lossView(listOf(stopped().copy(electricity = 120)))
        assertEquals(-120, onlyLight.headline)
        assertEquals(null, onlyLight.eggs)
        assertEquals(
            "Птенцов нет, и всё вложенное в закладку — расход. Стоимость яиц не указана — " +
                "в сумме её нет.",
            onlyLight.explanation,
        )

        val nothing = lossView(listOf(stopped()))
        assertEquals(null, nothing.headline)
        assertEquals("расходы неизвестны", nothing.caption)
    }

    @Test
    fun `вывод ноль в срок — строка для закладки и для партии`() {
        assertEquals(
            "Закладка «Весенняя» доведена до срока, но из 21 яйца не вывелся ни один птенец.",
            emptyHatchLine(listOf(summary("Весенняя", eggs = 21, hatched = 0))),
        )
        assertEquals(
            "Партия «Весенняя» доведена до срока, но по 2 породам из 30 яиц не вывелся ни один птенец.",
            emptyHatchLine(
                listOf(
                    summary("Весенняя — Хайсекс", breed = "Хайсекс", eggs = 20, hatched = 0),
                    summary("Весенняя — Ломан", breed = "Ломан", eggs = 10, hatched = 0),
                ),
            ),
        )
    }

    @Test
    fun `расходы партии без птенцов — сумма известного по породам`() {
        val view = lossView(
            listOf(priced("Хайсекс", egg = null, chick = null), priced("Ломан", egg = 300, chick = null)),
        )
        assertEquals(-300, view.headline)
        assertEquals(300, view.eggs)
        assertEquals(
            "Птенцов нет, и всё вложенное в закладку — расход. Стоимость яиц указана не у всех " +
                "пород — в сумме только известная. Электроэнергия не посчитана: не указаны " +
                "потребление или тариф.",
            view.explanation,
        )
    }

    @Test
    fun `короткая карточка — залпы над и под ней, за карточкой`() {
        val placement = fireworksPlacement(cardTop = 0.25f, cardBottom = 0.75f)
        assertEquals(false, placement.overCard)
        assertEquals(listOf(0.02f..0.22f, 0.78f..0.97f), placement.bands)
    }

    @Test
    fun `место только сверху — одна полоса`() {
        val placement = fireworksPlacement(cardTop = 0.2f, cardBottom = 0.95f)
        assertEquals(false, placement.overCard)
        assertEquals(1, placement.bands.size)
    }

    @Test
    fun `карточка на весь экран — салют поверх её верха`() {
        val placement = fireworksPlacement(cardTop = 0.04f, cardBottom = 0.96f)
        assertEquals(true, placement.overCard)
        assertEquals(listOf(0.04f..0.24f), placement.bands)
    }
}
