package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Масштаб и сдвиг графика на весь экран — арифметика жестов без Compose, на JVM.
 */
class ChartViewportTest {

    private val w = 300f
    private val h = 200f

    @Test
    fun `без масштаба содержимое совпадает с областью`() {
        val v = ChartViewport.IDENTITY
        assertEquals(0f, v.x(0f, w))
        assertEquals(w, v.x(1f, w))
        assertEquals(0f, v.yFromBottom(0f, h))
        assertEquals(-h, v.yFromBottom(1f, h))
        assertFalse(v.zoomed)
        assertEquals(1, v.subdivisions)
    }

    @Test
    fun `щипок вокруг точки оставляет её на месте`() {
        // Точка содержимого под пальцами: доля 0.5 по времени, 0.25 по значению.
        val v = ChartViewport.IDENTITY.transformed(
            zoom = 2f, cx = 150f, cy = -50f, panX = 0f, panY = 0f, plotWidth = w, plotHeight = h,
        )
        assertEquals(2f, v.scale)
        assertEquals(150f, v.x(0.5f, w), 1e-3f)
        assertEquals(-50f, v.yFromBottom(0.25f, h), 1e-3f)
        assertTrue(v.zoomed)
        assertEquals(2, v.subdivisions)
    }

    @Test
    fun `сдвиг не выпускает содержимое за края области`() {
        val zoomed = ChartViewport(scale = 2f, offsetX = 0f, offsetY = 0f)
        // Влево дальше, чем есть содержимого, — упирается в правый край.
        val left = zoomed.transformed(1f, 0f, 0f, panX = -10_000f, panY = 0f, plotWidth = w, plotHeight = h)
        assertEquals(-w, left.offsetX)
        assertEquals(w, left.x(1f, w))
        // Вправо от начала — начало остаётся у левого края.
        val right = zoomed.transformed(1f, 0f, 0f, panX = 10_000f, panY = 0f, plotWidth = w, plotHeight = h)
        assertEquals(0f, right.offsetX)
        // По вертикали то же: низ содержимого не поднимается над низом области,
        // верх не опускается ниже верха.
        val down = zoomed.transformed(1f, 0f, 0f, panX = 0f, panY = 10_000f, plotWidth = w, plotHeight = h)
        assertEquals(h, down.offsetY)
        assertEquals(-h, down.yFromBottom(1f, h))
        val up = zoomed.transformed(1f, 0f, 0f, panX = 0f, panY = -10_000f, plotWidth = w, plotHeight = h)
        assertEquals(0f, up.offsetY)
    }

    @Test
    fun `масштаб держится между единицей и пределом`() {
        val tooSmall = ChartViewport.IDENTITY.transformed(0.1f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(ChartViewport.MIN_SCALE, tooSmall.scale)
        assertEquals(ChartViewport.IDENTITY, tooSmall)
        val tooBig = ChartViewport.IDENTITY.transformed(100f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(ChartViewport.MAX_SCALE, tooBig.scale)
    }

    @Test
    fun `деление шага сетки — степень двойки не выше масштаба`() {
        assertEquals(1, ChartViewport(scale = 1.9f).subdivisions)
        assertEquals(2, ChartViewport(scale = 2f).subdivisions)
        assertEquals(2, ChartViewport(scale = 3.9f).subdivisions)
        assertEquals(4, ChartViewport(scale = 4f).subdivisions)
        assertEquals(8, ChartViewport(scale = 8f).subdivisions)
    }
}
