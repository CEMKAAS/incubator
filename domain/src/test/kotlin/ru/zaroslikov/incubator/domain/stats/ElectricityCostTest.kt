package ru.zaroslikov.incubator.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.PowerSettings
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Закрепляет счёт за электричество: часы ночного тарифа, деление общих часов между
 * закладками одного инкубатора и то, как свет входит в расход «Финансов».
 */
class ElectricityCostTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val single = PowerSettings(watts = 100, dayPrice = 6.0)
    private val twoRate = PowerSettings(
        watts = 100,
        dayPrice = 6.0,
        nightPrice = 3.0,
        nightStart = "23:00",
        nightEnd = "07:00",
    )

    private fun run(id: Long, from: String, to: String, power: PowerSettings, device: Long = 1) =
        PoweredRun(batchId = id, deviceId = device, start = at(from), end = at(to), power = power)

    @Test
    fun singleTariff_isHoursTimesPower() {
        // 100 Вт × 24 ч = 2,4 кВт·ч по 6 ₽.
        val cost = electricityCosts(listOf(run(1, "2026-03-01T10:00", "2026-03-02T10:00", single)), zone)
        assertEquals(2.4, cost.getValue(1).kwh, 1e-9)
        assertEquals(14.4, cost.getValue(1).rubles, 1e-9)
        assertFalse(cost.getValue(1).shared)
    }

    @Test
    fun nightWindow_crossesMidnight() {
        // Сутки с 10:00 до 10:00: ночь 23:00–07:00 целиком внутри — 8 ч.
        assertEquals(
            8 * 3_600_000L,
            nightMillis(at("2026-03-01T10:00"), at("2026-03-02T10:00"), 23 * 60, 7 * 60, zone),
        )
        // Начали в 02:00 — ночь, начавшаяся накануне, ещё идёт: 5 ч до 07:00.
        assertEquals(
            5 * 3_600_000L,
            nightMillis(at("2026-03-01T02:00"), at("2026-03-01T12:00"), 23 * 60, 7 * 60, zone),
        )
    }

    @Test
    fun nightWindow_insideOneDay() {
        // Ночной тариф с 01:00 до 05:00, без перехода через полночь.
        assertEquals(
            4 * 3_600_000L,
            nightMillis(at("2026-03-01T00:00"), at("2026-03-02T00:00"), 60, 300, zone),
        )
    }

    @Test
    fun twoTariffs_chargeNightHoursCheaper() {
        // 16 ч дня по 6 ₽ и 8 ч ночи по 3 ₽ при 0,1 кВт: 9,6 + 2,4.
        val cost = electricityCosts(listOf(run(1, "2026-03-01T10:00", "2026-03-02T10:00", twoRate)), zone)
        assertEquals(2.4, cost.getValue(1).kwh, 1e-9)
        assertEquals(12.0, cost.getValue(1).rubles, 1e-9)
        // По тарифам: ночь — 8 ч × 0,1 кВт = 0,8 кВт·ч по 3 ₽, день — остальное.
        assertTrue(cost.getValue(1).twoTariffs)
        assertEquals(0.8, cost.getValue(1).nightKwh, 1e-9)
        assertEquals(2.4, cost.getValue(1).nightRubles, 1e-9)
        assertEquals(1.6, cost.getValue(1).dayKwh, 1e-9)
        assertEquals(9.6, cost.getValue(1).dayRubles, 1e-9)
    }

    @Test
    fun singleTariff_hasNoNightPart() {
        val cost = electricityCosts(listOf(run(1, "2026-03-01T10:00", "2026-03-02T10:00", single)), zone)
        assertFalse(cost.getValue(1).twoTariffs)
        assertEquals(0.0, cost.getValue(1).nightRubles, 0.0)
    }

    @Test
    fun twoTariffs_sharedHours_splitNightPartToo() {
        // Две закладки сутки вместе на двух тарифах: каждой — половина и ночи тоже.
        val cost = electricityCosts(
            listOf(
                run(1, "2026-03-01T10:00", "2026-03-02T10:00", twoRate),
                run(2, "2026-03-01T10:00", "2026-03-02T10:00", twoRate),
            ),
            zone,
        )
        assertEquals(0.4, cost.getValue(1).nightKwh, 1e-9)
        assertEquals(1.2, cost.getValue(1).nightRubles, 1e-9)
        assertEquals(6.0, cost.getValue(1).rubles, 1e-9)
        // Время не делится: ночь у каждой — все 8 часов.
        assertEquals(8.0, cost.getValue(1).nightHours, 1e-9)
        assertEquals(8.0, cost.getValue(2).nightHours, 1e-9)
    }

    @Test
    fun overlappingBatches_shareTheCommonHours() {
        // Первая идёт двое суток, вторая — вторые сутки вместе с ней. Розетка взяла
        // 4,8 кВт·ч, и сумма двух счетов ровно столько же, а не 7,2.
        val cost = electricityCosts(
            listOf(
                run(1, "2026-03-01T10:00", "2026-03-03T10:00", single),
                run(2, "2026-03-02T10:00", "2026-03-03T10:00", single),
            ),
            zone,
        )
        assertEquals(2.4 + 1.2, cost.getValue(1).kwh, 1e-9)
        assertEquals(1.2, cost.getValue(2).kwh, 1e-9)
        assertEquals(4.8, cost.values.sumOf { it.kwh }, 1e-9)
        assertTrue(cost.getValue(1).shared)
        assertTrue(cost.getValue(2).shared)
    }

    @Test
    fun differentDevices_doNotShare() {
        val cost = electricityCosts(
            listOf(
                run(1, "2026-03-01T10:00", "2026-03-02T10:00", single, device = 1),
                run(2, "2026-03-01T10:00", "2026-03-02T10:00", single, device = 2),
            ),
            zone,
        )
        assertEquals(2.4, cost.getValue(1).kwh, 1e-9)
        assertEquals(2.4, cost.getValue(2).kwh, 1e-9)
    }

    @Test
    fun uncountableRun_isAbsent_andTakesNoShare() {
        val cost = electricityCosts(
            listOf(
                run(1, "2026-03-01T10:00", "2026-03-02T10:00", single),
                run(2, "2026-03-01T10:00", "2026-03-02T10:00", PowerSettings(watts = 100)),
            ),
            zone,
        )
        assertNull(cost[2])
        assertEquals(2.4, cost.getValue(1).kwh, 1e-9)
    }

    @Test
    fun batchSettings_winOverIncubator_tariffAsAWhole() {
        val device = twoRate.copy(watts = 60)
        // Своя мощность, тариф — инкубатора.
        val ownWatts = PowerSettings(watts = 120).over(device)
        assertEquals(120, ownWatts.watts)
        assertEquals(3.0, ownWatts.nightPrice!!, 0.0)
        // Свой тариф — однотарифный: ночь инкубатора к нему не примешивается.
        val ownTariff = PowerSettings(dayPrice = 5.0).over(device)
        assertEquals(60, ownTariff.watts)
        assertEquals(5.0, ownTariff.dayPrice!!, 0.0)
        assertNull(ownTariff.nightPrice)
        assertFalse(ownTariff.twoTariffs)
    }

    @Test
    fun finance_addsElectricityToExpense_andToBatchProfit() {
        val finished = Batch(
            id = 1, title = "А", type = "Курицы", data = "01.03.2026", eggAll = 10, eggAllEND = 8,
            airing = "false", over = "false", arhive = "1", dateEnd = "22.03.2026", note = "",
            price = 10, chickPrice = 50,
        )
        val active = finished.copy(id = 2, arhive = "0", dateEnd = "", eggAllEND = 0, chickPrice = 0)
        val finance = incubatorFinance(
            incubatorPrice = 1000,
            batches = listOf(finished, active),
            electricity = mapOf(
                1L to ElectricityCost(kwh = 50.0, rubles = 300.4),
                2L to ElectricityCost(kwh = 10.0, rubles = 59.6),
            ),
        )
        assertEquals(360, finance.electricityExpense)
        assertEquals(60, finance.activeElectricity)
        // Яйца 100 + 100, свет 360, техника 1000.
        assertEquals(1560, finance.expense)
        // Прибыль завершённой: 400 − 100 − 300.
        assertEquals(0, finance.batches.first { it.batchId == 1L }.profit)
        // Окупаемость — только по завершённым: идущая со своим светом не мешает.
        assertEquals(0, finance.profitOnBatches)
        // Себестоимость: (яйца + свет) / птенцы = 400 / 8.
        assertEquals(50, finance.chickCost)
        assertEquals(2, finance.batchesWithElectricity)
    }

    @Test
    fun finance_withoutElectricity_isUnchanged() {
        val batch = Batch(
            id = 1, title = "А", type = "Курицы", data = "01.03.2026", eggAll = 10, eggAllEND = 8,
            airing = "false", over = "false", arhive = "1", dateEnd = "22.03.2026", note = "",
            price = 10,
        )
        val finance = incubatorFinance(0, listOf(batch))
        assertEquals(100, finance.expense)
        assertFalse(finance.electricityKnown)
        assertNull(finance.batches.single().electricity)
    }
}
