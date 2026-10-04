package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Закрепляет «Отбраковано яиц» в форме правки — итог ручной отбраковки и овоскопирований
 * ([BatchUiState.rejectedTotal]), предел вывода ([BatchUiState.hatchLimit]) и отбраковку
 * остатком у закладки, доведённой до срока ([withBalancedCull]).
 */
class HatchLimitTest {

    private val form = BatchUiState(
        title = "Партия",
        type = "Курицы",
        eggAll = "20",
        eggRejected = "8",
        candlingRejected = 2,
        arhive = "1",
    )

    @Test
    fun `total is manual plus candlings`() {
        assertEquals(10, form.rejectedTotal)
        // Ручная часть в предел не входит — она пересчитывается остатком.
        assertEquals(18, form.hatchLimit)
    }

    @Test
    fun `only the manual part is stored on the batch`() {
        assertEquals(8, form.toBatch().eggRejected)
    }

    @Test
    fun `reading a batch keeps the parts apart`() {
        val back = form.toBatch().toBatchUiState(candlingRejected = 2)
        assertEquals("8", back.eggRejected)
        assertEquals(10, back.rejectedTotal)
        assertEquals("", form.toBatch().copy(eggRejected = 0).toBatchUiState().eggRejected)
    }

    @Test
    fun `cull above the eggs laid does not fit`() {
        assertTrue(form.copy(eggAll = "10").rejectedFits)
        assertFalse(form.copy(eggAll = "9").rejectedFits)
        assertEquals(0, form.copy(eggAll = "1").hatchLimit)
    }

    @Test
    fun `hint shows what the hatch leaves as cull`() {
        assertEquals("Отбраковано: 15 из 20", hatchedHint(eggAll = 20, candling = 2, hatched = "5"))
        assertEquals("Отбраковано: 2 из 20", hatchedHint(eggAll = 20, candling = 2, hatched = "30"))
        assertEquals("Все невылупившиеся яйца будут отбракованы", hatchedHint(20, 2, ""))
    }

    @Test
    fun `save clamps a hatch above the limit`() {
        assertEquals(18, form.copy(eggAllEND = "20").toBatch().eggAllEND)
        assertEquals(8, form.copy(eggAllEND = "8").toBatch().eggAllEND)
    }

    @Test
    fun `a finished batch culls everything that did not hatch`() {
        val balanced = form.copy(eggAllEND = "5").withBalancedCull()
        assertEquals("13", balanced.eggRejected) // 20 − 5 − 2 на овоскопировании
        assertEquals(15, balanced.rejectedTotal)

        val clamped = form.copy(eggAllEND = "25").withBalancedCull()
        assertEquals("18", clamped.eggAllEND)
        assertEquals("", clamped.eggRejected)
    }

    @Test
    fun `typing the egg count does not clamp the hatch on the way`() {
        // 60 заложено, 45 вывелось; правка 60 → 65 проходит через «6».
        val hatched = form.copy(eggAll = "60", candlingRejected = 0, eggAllEND = "45")
        val midway = hatched.copy(eggAll = "6").withBalancedCull(clampHatch = false)
        assertEquals("45", midway.eggAllEND)
        assertFalse(midway.hatchFits)
        val done = midway.copy(eggAll = "65").withBalancedCull(clampHatch = false)
        assertEquals("45", done.eggAllEND)
        assertEquals("20", done.eggRejected)
        assertTrue(done.hatchFits)
    }

    @Test
    fun `an end date before the laying date is an error even without the hour`() {
        val finished = form.copy(data = "10.09.2026", dateEnd = "09.09.2026", timeEnd = "")
        assertEquals("Раньше, чем заложили яйца", finished.finishMomentError)
        assertEquals(null, finished.copy(dateEnd = "10.09.2026").finishMomentError)
        assertEquals(null, finished.copy(dateEnd = "").finishMomentError)
    }

    @Test
    fun `running, stopped and blank hatch are left alone`() {
        assertEquals(form.copy(arhive = "0", eggAllEND = "5"), form.copy(arhive = "0", eggAllEND = "5").withBalancedCull())
        val stopped = form.copy(endReason = "Свет", eggAllEND = "0")
        assertEquals(stopped, stopped.withBalancedCull())
        assertEquals(form, form.withBalancedCull())
    }
}
