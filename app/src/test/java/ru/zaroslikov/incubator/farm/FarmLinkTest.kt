package ru.zaroslikov.incubator.farm

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.stats.HatchSummary

/**
 * Ссылка в «Моё хозяйство» — договор с другим приложением, которое разбирает её своим
 * кодом. Менять её можно только вместе с ним, и этот тест — то, что об этом напомнит.
 */
class FarmLinkTest {

    private val chicks = FarmChicks(
        type = "Курицы",
        name = "Весенняя партия — Ломан Браун",
        breed = "Ломан Браун",
        count = 18,
        date = "29.09.2026",
    )

    private fun params(link: String): Map<String, String> =
        link.substringAfter('?').split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `схема, хост и путь — те, на которые хозяйство вешает фильтр`() {
        assertTrue(FarmLink.encode(chicks).startsWith("myferma://animal/add?"))
    }

    @Test
    fun `все поля читаются обратно как были`() {
        val p = params(FarmLink.encode(chicks))
        assertEquals("1", p["v"])
        assertEquals("incubator", p["source"])
        assertEquals("Курицы", p["type"])
        assertEquals("Весенняя партия — Ломан Браун", p["name"])
        assertEquals("Ломан Браун", p["breed"])
        assertEquals("18", p["count"])
        assertEquals("29.09.2026", p["date"])
    }

    @Test
    fun `пробел кодируется процентом, а не плюсом`() {
        val link = FarmLink.encode(chicks)
        assertFalse(link.contains('+'))
        assertTrue(link.contains("%20"))
    }

    @Test
    fun `служебные символы в названии не ломают запрос`() {
        val tricky = chicks.copy(name = "A&B=C?#+")
        assertEquals("A&B=C?#+", params(FarmLink.encode(tricky))["name"])
    }

    @Test
    fun `пустая порода уходит пустой строкой`() {
        assertEquals("", params(FarmLink.encode(chicks.copy(breed = "")))["breed"])
    }

    private fun summary(title: String = "Весенняя", hatched: Int = 18, dateEnd: String = "28.09.2026") =
        HatchSummary(
            batchId = 1,
            title = title,
            species = "Утки",
            breed = "",
            eggs = 20,
            rejected = 0,
            hatched = hatched,
            termDays = 28,
            invested = 0,
            income = 0,
            hasEggPrice = false,
            hasChickPrice = false,
            dateEnd = dateEnd,
        )

    @Test
    fun `из сводки берутся вид, название, птенцы и дата вывода`() {
        assertEquals(
            FarmChicks(type = "Утки", name = "Весенняя", breed = "", count = 18, date = "28.09.2026"),
            farmChicksOf(summary(), today = "29.09.2026"),
        )
    }

    @Test
    fun `безымянная закладка называется видом, без даты — сегодняшним числом`() {
        val c = farmChicksOf(summary(title = "", dateEnd = ""), today = "29.09.2026")!!
        assertEquals("Утки", c.name)
        assertEquals("29.09.2026", c.date)
    }

    @Test
    fun `без птенцов передавать нечего`() {
        assertNull(farmChicksOf(summary(hatched = 0), today = "29.09.2026"))
    }

    private fun batch(arhive: String = "1", hatched: Int = 18, endReason: String = "", title: String = "Весенняя — Хайсекс") =
        Batch(
            id = 1,
            title = title,
            type = "Курицы",
            data = "08.09.2026",
            eggAll = 20,
            eggAllEND = hatched,
            airing = "false",
            over = "false",
            arhive = arhive,
            dateEnd = "29.09.2026",
            note = "",
            endReason = endReason,
            breed = "Хайсекс",
        )

    @Test
    fun `завершённая в срок закладка с птенцами уходит из меню карточки`() {
        assertEquals(
            FarmChicks(type = "Курицы", name = "Весенняя — Хайсекс", breed = "Хайсекс", count = 18, date = "29.09.2026"),
            farmChicksOf(batch(), today = "30.09.2026"),
        )
    }

    @Test
    fun `идущая, прерванная и пустая закладки в хозяйство не предлагаются`() {
        assertNull(farmChicksOf(batch(arhive = "0"), today = "30.09.2026"))
        assertNull(farmChicksOf(batch(hatched = 0, endReason = "Отключили свет"), today = "30.09.2026"))
        assertNull(farmChicksOf(batch(hatched = 0), today = "30.09.2026"))
    }
}
