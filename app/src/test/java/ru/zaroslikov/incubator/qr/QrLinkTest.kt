package ru.zaroslikov.incubator.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Формат ссылки в QR-коде инкубатора: что кладётся в код и что из него читается.
 *
 * Пришпилен отдельно от кодирования самого QR: ссылка — это договор между наклейкой,
 * напечатанной год назад, и приложением, которое её читает сегодня, и менять её
 * можно только зная, что старые наклейки останутся читаемыми.
 */
class QrLinkTest {

    @Test
    fun `encode writes the scheme, the host and the id`() {
        assertEquals("incubator://measure/12", QrLink.encode(12L))
    }

    @Test
    fun `parse reads back what encode wrote`() {
        for (id in listOf(1L, 7L, 12L, 123_456L, Long.MAX_VALUE)) {
            assertEquals(id, QrLink.parse(QrLink.encode(id)))
        }
    }

    @Test
    fun `scheme and host are matched without regard to case`() {
        assertEquals(12L, QrLink.parse("INCUBATOR://Measure/12"))
    }

    @Test
    fun `surrounding whitespace and a trailing slash are tolerated`() {
        assertEquals(12L, QrLink.parse("  incubator://measure/12/ \n"))
    }

    @Test
    fun `a foreign text is not a link`() {
        assertNull(QrLink.parse(null))
        assertNull(QrLink.parse(""))
        assertNull(QrLink.parse("https://example.com/measure/12"))
        assertNull(QrLink.parse("WIFI:T:WPA;S:home;P:secret;;"))
        assertNull(QrLink.parse("incubator://batch/12"))
    }

    @Test
    fun `a link without an id or with junk after it is refused whole`() {
        assertNull(QrLink.parse("incubator://measure/"))
        assertNull(QrLink.parse("incubator://measure"))
        assertNull(QrLink.parse("incubator://measure/12abc"))
        assertNull(QrLink.parse("incubator://measure/12?x=1"))
        assertNull(QrLink.parse("incubator://measure/-5"))
        assertNull(QrLink.parse("incubator://measure/0"))
    }
}
