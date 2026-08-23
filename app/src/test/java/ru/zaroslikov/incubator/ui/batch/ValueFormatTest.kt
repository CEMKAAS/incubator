package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Правила ввода и показа температуры и влажности.
 *
 * Тест держится в `:app`, а не в `:domain`, потому что там же живёт и сам
 * `ValueFormat.kt`; андроидного в нём ничего нет, и `gradlew :app:test` гоняет его
 * на JVM без эмулятора.
 */
class ValueFormatTest {

    @Test
    fun `в замер помещаются две цифры до точки и две после`() {
        assertEquals("37.55", "37.55".filterMeasureInput())
        assertEquals("37.5", "37.5".filterMeasureInput())
        assertEquals("9.5", "9.5".filterMeasureInput())
        assertEquals("60", "60".filterMeasureInput())
    }

    @Test
    fun `лишние цифры отсекаются`() {
        // Ровно то, что просили запретить.
        assertEquals("33.44", "333.44".filterMeasureInput())
        assertEquals("33.33", "33.333".filterMeasureInput())
        // Целая часть двузначна, поэтому «100» целиком не проходит — см. KDoc правила.
        assertEquals("10", "100".filterMeasureInput())
    }

    @Test
    fun `разделитель один и всегда точка`() {
        assertEquals("37.5", "37,5".filterMeasureInput())
        assertEquals("37.5", "37..5".filterMeasureInput())
        assertEquals("37.5", "37,.5".filterMeasureInput())
    }

    @Test
    fun `с разделителя ввод не начинается`() {
        assertEquals("5", ".5".filterMeasureInput())
        assertEquals("", ".".filterMeasureInput())
    }

    @Test
    fun `буквы и знаки во ввод не проходят`() {
        assertEquals("37.5", "t37.5°C".filterMeasureInput())
        assertEquals("", "нет".filterMeasureInput())
        assertEquals("38", "-38".filterMeasureInput())
    }

    @Test
    fun `на счётчики правило замера не распространяется`() {
        assertEquals("2", "2.5".filterCountInput())
        assertEquals("37", "37,55".filterCountInput())
        assertEquals("38", "-38".filterCountInput())
        assertEquals("12", "12".filterCountInput())
        assertEquals("999", "9999".filterCountInput())
    }

    @Test
    fun `температура показывается с одним знаком, а при нужде с двумя`() {
        assertEquals("37.0", 37.0.formatTemp())
        assertEquals("37.5", 37.5.formatTemp())
        assertEquals("37.55", 37.55.formatTemp())
        assertEquals("37.05", 37.05.formatTemp())
        assertEquals("—", (null as Double?).formatTempOrDash())
    }

    @Test
    fun `влажность целая, если целая`() {
        assertEquals("60", 60.0.formatDamp())
        assertEquals("37.5", 37.5.formatDamp())
        assertEquals("55.25", 55.25.formatDamp())
        assertEquals("—", (null as Double?).formatDampOrDash())
    }

    @Test
    fun `в поле ввода хвостового нуля нет`() {
        assertEquals("38", 38.0.toFieldText())
        assertEquals("37.5", 37.5.toFieldText())
        assertEquals("37.55", 37.55.toFieldText())
        assertEquals("", (null as Double?).toFieldText())
    }

    @Test
    fun `разбор принимает запятую, а пустое и мусор дают null`() {
        assertEquals(37.55, "37,55".toMeasureOrNull()!!, 0.0001)
        assertEquals(37.5, "37.5".toMeasureOrNull()!!, 0.0001)
        assertEquals(37.0, "37.".toMeasureOrNull()!!, 0.0001)
        assertNull("".toMeasureOrNull())
        assertNull("нет".toMeasureOrNull())
    }

    @Test
    fun `заметка начинается с заглавной буквы`() {
        assertEquals("Долил воды", "долил воды".capitalizeFirst())
        assertEquals("Долил воды", "Долил воды".capitalizeFirst())
        // Меняется ровно первый символ, поэтому «pH» в начале строки его и получает.
        assertEquals("PH в норме", "pH в норме".capitalizeFirst())
        assertEquals("", "".capitalizeFirst())
    }
}
