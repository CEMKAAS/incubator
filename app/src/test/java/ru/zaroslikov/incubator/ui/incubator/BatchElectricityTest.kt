package ru.zaroslikov.incubator.ui.incubator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.ui.components.PowerFormState
import ru.zaroslikov.incubator.ui.components.toFormState
import ru.zaroslikov.incubator.ui.components.toSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Закрепляет, как закладки становятся промежутками для счёта за свет: от момента закладки
 * до завершения (в час закладки) или до «сейчас», и с настройками закладки поверх
 * инкубаторных. Сама арифметика — в `:domain` (`ElectricityCostTest`).
 */
class BatchElectricityTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun moment(text: String): Date =
        Date.from(LocalDateTime.parse(text).atZone(zone).toInstant())

    private val incubator = Incubator(
        id = 1, name = "Блиц", capacity = 72, autoTurn = false, autoAiring = false,
        power = PowerSettings(watts = 100, dayPrice = 6.0),
    )

    private fun batch(
        id: Long,
        arhive: String = "1",
        dateEnd: String = "03.03.2026",
        power: PowerSettings = PowerSettings(),
    ) = Batch(
        id = id, title = "Закладка $id", type = "Курицы", data = "01.03.2026", eggAll = 10,
        eggAllEND = 0, airing = "false", over = "false", arhive = arhive, dateEnd = dateEnd,
        note = "", incubatorId = 1, time = "10:00", power = power,
    )

    @Test
    fun finishedBatch_runsUntilItsEndDateAtTheLayingHour() {
        // Двое суток при 100 Вт по 6 ₽: 4,8 кВт·ч и 28,8 ₽.
        val cost = batchElectricity(listOf(batch(1)), mapOf(1L to incubator), Date(), zone)
        assertEquals(4.8, cost.getValue(1).kwh, 1e-9)
        assertEquals(29, cost.getValue(1).roundedRubles)
    }

    @Test
    fun activeBatch_runsUntilNow() {
        val now = moment("2026-03-02T10:00")
        val cost = batchElectricity(listOf(batch(1, arhive = "0", dateEnd = "")), mapOf(1L to incubator), now, zone)
        assertEquals(2.4, cost.getValue(1).kwh, 1e-9)
    }

    @Test
    fun batchSettings_winOverTheIncubator() {
        val own = batch(1, power = PowerSettings(watts = 200))
        val cost = batchElectricity(listOf(own), mapOf(1L to incubator), Date(), zone)
        assertEquals(9.6, cost.getValue(1).kwh, 1e-9)
    }

    @Test
    fun finishedWithoutEndDate_isNotCounted() {
        val cost = batchElectricity(listOf(batch(1, dateEnd = "")), mapOf(1L to incubator), Date(), zone)
        assertNull(cost[1])
    }

    @Test
    fun formState_roundTripsAndDropsTheNightWhenSwitchedOff() {
        val settings = PowerSettings(
            watts = 45, dayPrice = 6.43, nightPrice = 3.21, nightStart = "23:00", nightEnd = "07:00",
        )
        val form = settings.toFormState()
        assertTrue(form.twoTariffs)
        assertEquals("6.43", form.dayPrice)
        assertEquals(settings, form.toSettings())

        val single = form.copy(twoTariffs = false).toSettings()
        assertNull(single.nightPrice)
        assertEquals("", single.nightStart)
        assertFalse(single.twoTariffs)
    }

    @Test
    fun formState_acceptsCommaAndTreatsBlankAsUnset() {
        val settings = PowerFormState(watts = "", dayPrice = "5,5").toSettings()
        assertNull(settings.watts)
        assertEquals(5.5, settings.dayPrice!!, 0.0)
        assertTrue(PowerFormState().isBlank)
    }

    @Test
    fun finishedBatch_runsUntilTheEndHourItWasSwitchedOff() {
        // Закончили 03.03 в 16:00 вместо 10:00 — на шесть часов больше: 4,8 + 0,6 кВт·ч.
        val switchedOff = batch(1).copy(timeEnd = "16:00")
        val cost = batchElectricity(listOf(switchedOff), mapOf(1L to incubator), Date(), zone)
        assertEquals(5.4, cost.getValue(1).kwh, 1e-9)
    }

    @Test
    fun archivedNeighbour_doesNotShareTheHours() {
        // Сосед по тем же суткам убран в архив — закладка платит за всё сама, как одна.
        val mine = batch(1)
        val archived = batch(2).copy(hidden = true)
        val cost = electricityOfFinished(mine, incubator, listOf(mine, archived), Date())
        assertEquals(4.8, cost!!.kwh, 1e-9)
        assertFalse(cost.shared)

        val shared = electricityOfFinished(mine, incubator, listOf(mine, batch(2)), Date())
        assertEquals(2.4, shared!!.kwh, 1e-9)
    }

    @Test
    fun finishedBatch_justArchived_stillGetsItsOwnCost() {
        // «Убрать в архив» из меню завершает и прячет одной записью — поздравлению свет нужен.
        val mine = batch(1).copy(hidden = true)
        val cost = electricityOfFinished(mine, incubator, listOf(mine), Date())
        assertEquals(4.8, cost!!.kwh, 1e-9)
    }
}
