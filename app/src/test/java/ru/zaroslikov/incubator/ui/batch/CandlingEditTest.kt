package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.ui.parseDate

/**
 * Закрепляет строки овоскопирования в форме правки ([candlingEditRows]) и то, что из
 * них уходит в базу ([candlingWrites]).
 */
class CandlingEditTest {

    private val catalog = SpeciesCatalog.EMPTY
    private val term = catalog.incubationDays("Курицы")!!
    private val candlingDays = (1..term).filter { catalog.isCandlingDay("Курицы", it) }

    private val batch = Batch(
        id = 1,
        title = "Kury",
        type = "Курицы",
        data = "01.09.2026",
        eggAll = 20,
        eggAllEND = 5,
        airing = "false",
        over = "false",
        arhive = "1",
        dateEnd = "22.09.2026",
        note = "",
        time = "10:00",
    )

    @Test
    fun `hatched batch shows every candling day of its term`() {
        val rows = candlingEditRows(batch, catalog, emptyList(), now = parseDate("01.10.2026")!!)
        assertEquals(candlingDays, rows.map { it.day })
        assertTrue(rows.all { it.rejected.isEmpty() && it.saved == null })
        assertEquals((1..candlingDays.size).toList(), rows.map { it.stage })
    }

    @Test
    fun `running batch shows only the days it has lived`() {
        val running = batch.copy(arhive = "0", dateEnd = "")
        val firstDay = candlingDays.first()
        // Утро дня овоскопирования: закладка дожила до него с 10:00 первого дня.
        val now = parseDate("01.09.2026")!!.time + (firstDay - 1) * DAY + 11 * HOUR
        val rows = candlingEditRows(running, catalog, emptyList(), now = java.util.Date(now))
        assertEquals(listOf(firstDay), rows.map { it.day })
    }

    @Test
    fun `a saved record on another day stays in the list`() {
        val odd = Candling(id = 5, idPT = 1, day = 2, date = "02.09.2026", rejected = 1)
        val rows = candlingEditRows(batch, catalog, listOf(odd), now = parseDate("01.10.2026")!!)
        val row = rows.first { it.day == 2 }
        assertEquals("1", row.rejected)
        assertEquals(odd, row.saved)
        assertEquals(1, rows.rejectedSum())
    }

    @Test
    fun `writes only what changed`() {
        val kept = Candling(id = 1, idPT = 1, day = 7, date = "07.09.2026", rejected = 2)
        val edited = Candling(id = 2, idPT = 1, day = 11, date = "11.09.2026", rejected = 1)
        val cleared = Candling(id = 3, idPT = 1, day = 18, date = "18.09.2026", rejected = 4)
        val rows = listOf(
            CandlingEditRow(day = 7, stage = 1, rejected = "2", saved = kept),
            CandlingEditRow(day = 11, stage = 2, rejected = "3", saved = edited),
            CandlingEditRow(day = 18, stage = 3, rejected = "", saved = cleared),
            CandlingEditRow(day = 19, stage = 0, rejected = "0", saved = null),
            CandlingEditRow(day = 20, stage = 0, rejected = "", saved = null),
        )
        val writes = candlingWrites(batchId = 1, rows = rows, today = "01.10.2026")
        assertEquals(
            listOf(
                edited.copy(rejected = 3),
                Candling(idPT = 1, day = 19, date = "01.10.2026", rejected = 0),
            ),
            writes.save,
        )
        assertEquals(listOf(cleared), writes.delete)
    }

    @Test
    fun `draft total adds both parts`() {
        val draft = RejectedDraft(
            manual = "8",
            candlings = listOf(
                CandlingEditRow(day = 7, stage = 1, rejected = "", saved = null),
                CandlingEditRow(day = 16, stage = 3, rejected = "2", saved = null),
            ),
        )
        assertEquals(2, draft.candlingSum)
        assertEquals(10, draft.total)
        assertEquals(2, draft.copy(manual = "").total)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
