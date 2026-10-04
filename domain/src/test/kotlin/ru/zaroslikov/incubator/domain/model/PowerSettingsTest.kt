package ru.zaroslikov.incubator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Закрепляет [PowerSettings.withoutInherited]: при сохранении формы правки закладка не
 * присваивает себе то, что форма лишь показала из инкубатора, — по тем же частям, что
 * [PowerSettings.over]: мощность порознь, тариф целиком.
 */
class PowerSettingsTest {

    private val device = PowerSettings(
        watts = 100, dayPrice = 6.43, nightPrice = 3.2, nightStart = "23:00", nightEnd = "07:00",
    )

    @Test
    fun `untouched inherited block stays blank`() {
        val own = PowerSettings()
        val shown = own.over(device)
        assertEquals(own, shown.withoutInherited(own, shown))
    }

    @Test
    fun `editing the watts keeps the inherited tariff blank`() {
        val own = PowerSettings()
        val shown = own.over(device)
        val saved = shown.copy(watts = 80).withoutInherited(own, shown)
        assertEquals(PowerSettings(watts = 80), saved)
    }

    @Test
    fun `editing the tariff keeps the inherited watts blank and takes the whole tariff`() {
        val own = PowerSettings()
        val shown = own.over(device)
        val saved = shown.copy(dayPrice = 7.0).withoutInherited(own, shown)
        assertEquals(null, saved.watts)
        assertEquals(7.0, saved.dayPrice!!, 0.0)
        // Тариф целиком: ночь, показанная из инкубатора, уходит вместе с новой ценой дня.
        assertEquals(3.2, saved.nightPrice!!, 0.0)
        assertEquals("23:00", saved.nightStart)
    }

    @Test
    fun `own values are kept even when they equal the device`() {
        val own = PowerSettings(watts = 100)
        val shown = own.over(device)
        assertEquals(100, shown.withoutInherited(own, shown).watts)
    }

    @Test
    fun `own watts with an inherited tariff`() {
        val own = PowerSettings(watts = 60)
        val shown = own.over(device)
        assertEquals(own, shown.withoutInherited(own, shown))
    }
}
