package ru.zaroslikov.incubator.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Единицы из «Настроек»: перевод градусов туда и обратно и разбор сохранённых имён.
 *
 * Главное здесь — что перевод в Фаренгейты **обратим на точности приложения**: в базе
 * температура хранится до сотых в Цельсиях, и режим, показанный в °F и сохранённый
 * обратно нетронутым, обязан вернуться в базу тем же числом, а не 37.78 вместо 37.8.
 */
class UnitsTest {

    @Test
    fun `цельсий ничего не переводит`() {
        assertEquals(37.8, TemperatureUnit.CELSIUS.fromCelsius(37.8), 0.0)
        assertEquals(37.8, TemperatureUnit.CELSIUS.toCelsius(37.8), 0.0)
        assertEquals(0.5, TemperatureUnit.CELSIUS.scale(0.5), 0.0)
    }

    @Test
    fun `фаренгейт переводит по формуле и до сотых`() {
        assertEquals(100.04, TemperatureUnit.FAHRENHEIT.fromCelsius(37.8), 0.0)
        assertEquals(99.5, TemperatureUnit.FAHRENHEIT.fromCelsius(37.5), 0.0)
        assertEquals(32.0, TemperatureUnit.FAHRENHEIT.fromCelsius(0.0), 0.0)
        assertEquals(37.8, TemperatureUnit.FAHRENHEIT.toCelsius(100.04), 0.0)
        assertEquals(37.53, TemperatureUnit.FAHRENHEIT.toCelsius(99.55), 0.0)
    }

    @Test
    fun `режим, показанный в фаренгейтах, возвращается в базу тем же числом`() {
        val unit = TemperatureUnit.FAHRENHEIT
        listOf(37.0, 37.2, 37.5, 37.55, 37.8, 38.0, 38.3, 36.9).forEach { celsius ->
            assertEquals(celsius, unit.toCelsius(unit.fromCelsius(celsius)), 0.0)
        }
    }

    @Test
    fun `разница температур переводится без сдвига на 32`() {
        assertEquals(0.9, TemperatureUnit.FAHRENHEIT.scale(0.5), 1e-9)
        assertEquals(-2.7, TemperatureUnit.FAHRENHEIT.scale(-1.5), 1e-9)
        assertEquals(0.0, TemperatureUnit.FAHRENHEIT.scale(0.0), 0.0)
    }

    @Test
    fun `фаренгейту нужна третья цифра до точки, цельсию нет`() {
        assertEquals(2, TemperatureUnit.CELSIUS.integerDigits)
        assertEquals(3, TemperatureUnit.FAHRENHEIT.integerDigits)
    }

    @Test
    fun `незнакомое или пустое имя — значения по умолчанию`() {
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromName(null))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.fromName("KELVIN"))
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.fromName("FAHRENHEIT"))
        assertEquals(Currency.RUB, Currency.fromName(null))
        assertEquals(Currency.RUB, Currency.fromName("GBP"))
        assertEquals(Currency.KZT, Currency.fromName("KZT"))
        assertEquals(Units.DEFAULT, Units(TemperatureUnit.CELSIUS, Currency.RUB))
    }

    @Test
    fun `у каждой валюты свой знак`() {
        assertEquals("₽", Currency.RUB.symbol)
        assertEquals("$", Currency.USD.symbol)
        assertEquals("€", Currency.EUR.symbol)
        assertEquals("₸", Currency.KZT.symbol)
        assertEquals("Br", Currency.BYN.symbol)
        assertEquals("¥", Currency.CNY.symbol)
        assertEquals(Currency.entries.size, Currency.entries.map { it.symbol }.toSet().size)
    }
}
