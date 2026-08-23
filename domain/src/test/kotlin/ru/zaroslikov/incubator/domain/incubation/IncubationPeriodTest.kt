package ru.zaroslikov.incubator.domain.incubation

import org.junit.Assert.assertEquals
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
}
