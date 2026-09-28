package ru.zaroslikov.incubator.domain.incubation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.CustomSpeciesDay

/**
 * Каталог — единственная дверь к знанию о виде, и тест закрепляет обе её половины:
 * встроенные виды отвечают ровно так же, как прежние `when`-функции, а свой вид
 * отвечает по своим строкам, а не проваливается в «неизвестно».
 */
class SpeciesCatalogTest {

    private fun day(
        n: Int,
        candling: Boolean = false,
        temp: Double? = 37.7,
        damp: Double? = 55.0,
    ) = CustomSpeciesDay(
        day = n, temp = temp, damp = damp, over = 4, airingCount = 2, airingTime = 10,
        candling = candling,
    )

    /** Цесарки на 26 дней с овоскопированием на 8, 14 и 24 — вида, которого в коде нет. */
    private val guineaFowl = CustomSpecies(
        id = 7,
        name = "Цесарки",
        days = (1..26).map { day(it, candling = it == 8 || it == 14 || it == 24) },
    )

    private val catalog = SpeciesCatalog(listOf(guineaFowl))

    @Test
    fun `built-in species answer exactly like the when-functions`() {
        for (bird in SpeciesCatalog.BUILT_IN) {
            assertEquals(bird, incubationDays(bird), catalog.incubationDays(bird))
            assertEquals(bird, setIncubator(bird), catalog.schedule(bird))
            for (day in 1..35) {
                assertEquals("$bird $day", setOvoskop(bird, day), catalog.isCandlingDay(bird, day))
                assertEquals("$bird $day", ovoskopStage(bird, day), catalog.candlingStage(bird, day))
                assertEquals(
                    "$bird $day",
                    isIncubationFinished(bird, day),
                    catalog.isIncubationFinished(bird, day),
                )
                assertEquals(
                    "$bird $day",
                    canFinishIncubation(bird, day),
                    catalog.canFinishIncubation(bird, day),
                )
            }
        }
    }

    @Test
    fun `custom species is answered from its own rows`() {
        assertTrue(catalog.isCustom("Цесарки"))
        assertEquals(26, catalog.incubationDays("Цесарки"))
        assertFalse(catalog.isIncubationFinished("Цесарки", 25))
        assertTrue(catalog.isIncubationFinished("Цесарки", 26))
        assertFalse(catalog.canFinishIncubation("Цесарки", 24))
        assertTrue(catalog.canFinishIncubation("Цесарки", 25))

        assertEquals(listOf(8, 14, 24), guineaFowl.candlingDays)
        assertEquals(0, catalog.candlingStage("Цесарки", 7))
        assertEquals(1, catalog.candlingStage("Цесарки", 8))
        assertEquals(2, catalog.candlingStage("Цесарки", 14))
        assertEquals(3, catalog.candlingStage("Цесарки", 24))
        assertTrue(catalog.isCandlingDay("Цесарки", 14))
        assertFalse(catalog.isCandlingDay("Цесарки", 15))
    }

    @Test
    fun `custom schedule is one Value per day, sorted, and a fresh list each time`() {
        val shuffled = CustomSpecies(
            name = "Страусы",
            days = listOf(day(3), day(1, temp = 36.5), day(2)),
        )
        val cat = SpeciesCatalog(listOf(shuffled))
        val schedule = cat.schedule("Страусы")
        assertEquals(listOf(1, 2, 3), schedule.map { it.day })
        assertEquals(36.5, schedule[0].temp)
        assertEquals("", schedule[0].note)
        assertEquals(0L, schedule[0].idPT)

        schedule[0].temp = 99.0
        assertEquals(36.5, cat.schedule("Страусы")[0].temp)
    }

    @Test
    fun `unknown species stays unknown`() {
        assertNull(catalog.incubationDays("Фазаны"))
        assertTrue(catalog.isIncubationFinished("Фазаны", 1))
        assertTrue(catalog.canFinishIncubation("Фазаны", 1))
        assertEquals(0, catalog.candlingStage("Фазаны", 7))
        // Режим для неизвестного вида — тот же запасной, что у setIncubator в ветке else.
        assertEquals(setIncubator("Фазаны"), catalog.schedule("Фазаны"))
        assertFalse(catalog.isCustom("Фазаны"))
    }

    @Test
    fun `custom species with the same name overrides the built-in one`() {
        val shortHens = CustomSpecies(name = "Курицы", days = (1..19).map { day(it) })
        val cat = SpeciesCatalog(listOf(shortHens))
        assertEquals(19, cat.incubationDays("Курицы"))
        assertEquals(19, cat.schedule("Курицы").size)
    }

    @Test
    fun `name is taken by built-ins and other custom species, case-insensitively`() {
        assertTrue(catalog.isNameTaken("Курицы"))
        assertTrue(catalog.isNameTaken(" курицы "))
        assertTrue(catalog.isNameTaken("цесарки"))
        assertFalse(catalog.isNameTaken("Цесарки", exceptId = 7))
        assertFalse(catalog.isNameTaken("Фазаны"))
        assertEquals(SpeciesCatalog.BUILT_IN + "Цесарки", catalog.allNames)
    }
}
