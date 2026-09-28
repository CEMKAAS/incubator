package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Подпись под полосой заполнения в форме новой закладки ([fillCaption]): что уже
 * лежит в инкубаторе и сколько станет с новыми яйцами.
 */
class IncubatorFillTest {

    @Test
    fun `empty incubator needs no caption`() {
        assertNull(fillCaption(occupied = 0, adding = 30, split = false))
    }

    @Test
    fun `blank egg field names only what is already there`() {
        assertEquals("20 яиц уже в инкубаторе", fillCaption(occupied = 20, adding = 0, split = false))
    }

    @Test
    fun `one batch adds up with this batch`() {
        assertEquals(
            "21 яйцо уже в инкубаторе, с этой закладкой — 51",
            fillCaption(occupied = 21, adding = 30, split = false),
        )
    }

    @Test
    fun `split tray speaks of several batches`() {
        assertEquals(
            "1 200 яиц уже в инкубаторе, с новыми закладками — 1 250",
            fillCaption(occupied = 1_200, adding = 50, split = true),
        )
    }
}
