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
}
