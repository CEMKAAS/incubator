package ru.zaroslikov.incubator.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.ui.incubator.formatMoney
import ru.zaroslikov.incubator.ui.incubator.formatMoneySigned
import ru.zaroslikov.incubator.ui.incubator.plural

/**
 * Правило «у каждого числа есть разделитель разрядов» — и то, что оно достаётся всем
 * трём способам напечатать число, а не одному.
 *
 * Разделитель тут проверяется буквально: это **неразрывный** пробел (U+00A0), а не
 * обычный. Подмена читается глазом одинаково и ловится только так — а цена её видна
 * лишь в узкой ячейке, где обычный пробел разрешает переносу разорвать «12 400» надвое.
 */
class NumberFormatTest {

    /** Неразрывный пробел — тот самый символ, который должен стоять между разрядами. */
    private val nbsp = '\u00A0'

    @Test
    fun `трёхзначные и меньше остаются как есть`() {
        assertEquals("0", formatCount(0))
        assertEquals("7", formatCount(7))
        assertEquals("72", formatCount(72))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `разряды отбиваются неразрывным пробелом`() {
        assertEquals("1${nbsp}000", formatCount(1000))
        assertEquals("12${nbsp}400", formatCount(12400))
        assertEquals("123${nbsp}456", formatCount(123456))
        assertEquals("1${nbsp}234${nbsp}567", formatCount(1234567))
    }

    @Test
    fun `минус типографский, а не дефис`() {
        assertEquals("−3${nbsp}400", formatCount(-3400))
        assertEquals("−7", formatCount(-7))
    }

    @Test
    fun `склонение печатает число через тот же разделитель`() {
        assertEquals("1${nbsp}200 яиц", plural(1200, "яйцо", "яйца", "яиц"))
        // И само склонение на месте: 11–14 берут форму «многих» вопреки последней цифре,
        // а 21 возвращается к единственному числу.
        assertEquals("1 яйцо", plural(1, "яйцо", "яйца", "яиц"))
        assertEquals("11 яиц", plural(11, "яйцо", "яйца", "яиц"))
        assertEquals("21 яйцо", plural(21, "яйцо", "яйца", "яиц"))
        assertEquals("1${nbsp}002 яйца", plural(1002, "яйцо", "яйца", "яиц"))
    }

    @Test
    fun `деньги — то же число плюс знак валюты`() {
        assertEquals("11${nbsp}250 ₽", formatMoney(11250, Currency.RUB))
        assertEquals("0 ₽", formatMoney(0, Currency.RUB))
        assertEquals("−3${nbsp}400 ₽", formatMoney(-3400, Currency.RUB))
        // Валюта — только знак после числа; разряды и минус те же.
        assertEquals("11${nbsp}250 $", formatMoney(11250, Currency.USD))
        assertEquals("−3${nbsp}400 Br", formatMoney(-3400, Currency.BYN))
    }

    @Test
    fun `знак у денег ставится только положительным и только в знаковом варианте`() {
        assertEquals("+11${nbsp}250 ₽", formatMoneySigned(11250, Currency.RUB))
        assertEquals("−3${nbsp}400 ₽", formatMoneySigned(-3400, Currency.RUB))
        // Ноль без знака: «+0 ₽» обещало бы прибавку, которой нет.
        assertEquals("0 ₽", formatMoneySigned(0, Currency.RUB))
    }
}
