package ru.zaroslikov.incubator.domain.incubation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * isIncubationFinished() — единственная функция, которую при переносе в :domain пришлось
 * переписать: старая endInc() принимала Compose-овский MutableState и сама открывала диалог.
 * Тест сверяет новую реализацию со старой на всех видах птиц и всех разумных днях.
 */
class IncubationPeriodTest {

    /** Дословная копия прежней endInc() из IncubatorScreen.kt, без побочного эффекта. */
    private fun legacyEndInc(typeBird: String, day: Int): Boolean =
        if ((typeBird == "Курицы") && day < 21) false
        else if ((typeBird == "Индюки") && day < 28) false
        else if ((typeBird == "Гуси") && day < 30) false
        else if ((typeBird == "Утки") && day < 28) false
        else if ((typeBird == "Перепела") && day < 17) false
        else true

    @Test
    fun `behaves exactly like the old endInc for every species and day`() {
        val species = listOf("Курицы", "Индюки", "Гуси", "Утки", "Перепела", "Цесарки", "")
        for (bird in species) {
            for (day in -1..45) {
                assertEquals(
                    "$bird, день $day",
                    legacyEndInc(bird, day),
                    isIncubationFinished(bird, day)
                )
            }
        }
    }

    /**
     * Кнопка завершения в шторке закладки зеленеет ровно на два дня: последний день
     * инкубации и предпоследний. Раньше — красная, и завершение считается досрочным.
     */
    @Test
    fun `canFinishIncubation opens up on the last two days`() {
        val periods = mapOf(
            "Курицы" to 21,
            "Индюки" to 28,
            "Гуси" to 30,
            "Утки" to 28,
            "Перепела" to 17,
        )
        for ((bird, total) in periods) {
            for (day in 1..total) {
                assertEquals(
                    "$bird, день $day из $total",
                    day >= total - 1,
                    canFinishIncubation(bird, day)
                )
            }
            assertFalse("$bird, предпредпоследний день", canFinishIncubation(bird, total - 2))
            assertTrue("$bird, предпоследний день", canFinishIncubation(bird, total - 1))
            assertTrue("$bird, последний день", canFinishIncubation(bird, total))
        }
    }

    /** Неизвестному виду срока нет, значит и «рано» для него не бывает. */
    @Test
    fun `unknown species is always ready to finish`() {
        for (day in 1..45) {
            assertTrue("день $day", canFinishIncubation("Цесарки", day))
        }
    }
}
