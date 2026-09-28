package ru.zaroslikov.incubator.ui.incubator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Когда закладке пора предлагать итог.
 *
 * Тест держится в `:app`, как и `ValueFormatTest`: сам расчёт лежит в
 * `FinishedBatchPrompt.kt`, андроидного в нём ничего нет, и `gradlew :app:test` гоняет
 * его на JVM без эмулятора.
 */
class FinishedBatchPromptTest {

    @Test
    fun `срок истекает в дату вывода и в час закладки`() {
        val batch = batch(start = "01.08.2026", time = "08:00")
        assertEquals(moment("22.08.2026 08:00"), batchFinishMoment(batch, SpeciesCatalog.EMPTY))
    }

    @Test
    fun `без времени закладки срок истекает в полночь дня вывода`() {
        // Так ведут себя закладки, заведённые до появления поля времени (схема v10).
        val batch = batch(start = "01.08.2026", time = "")
        assertEquals(moment("22.08.2026 00:00"), batchFinishMoment(batch, SpeciesCatalog.EMPTY))
    }

    @Test
    fun `у неизвестного вида срока нет`() {
        assertNull(batchFinishMoment(batch(start = "01.08.2026", type = "Цесарки"), SpeciesCatalog.EMPTY))
        assertNull(batchFinishMoment(batch(start = "", time = "08:00"), SpeciesCatalog.EMPTY))
    }

    @Test
    fun `до часа закладки в день вывода итог не предлагается`() {
        val batches = listOf(batch(start = "01.08.2026", time = "08:00"))
        assertNull(batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("22.08.2026 07:59")))
        assertEquals(1L, batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("22.08.2026 08:00"))?.batch?.id)
        // Днём раньше срок не вышел, сколько бы времени ни было.
        assertNull(batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("21.08.2026 23:59")))
    }

    @Test
    fun `просроченная закладка предлагается и через неделю`() {
        val batches = listOf(batch(start = "01.08.2026", time = "08:00"))
        assertEquals(1L, batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("29.08.2026 10:00"))?.batch?.id)
    }

    @Test
    fun `завершённая закладка итога больше не ждёт`() {
        val batches = listOf(batch(start = "01.08.2026", time = "08:00", arhive = "1"))
        assertNull(batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("29.08.2026 10:00")))
    }

    @Test
    fun `из нескольких просроченных берётся самая давняя`() {
        val batches = listOf(
            batch(id = 1, start = "05.08.2026", time = "08:00"),
            batch(id = 2, start = "01.08.2026", time = "08:00"),
        )
        assertEquals(2L, batchDueToFinish(batches, SpeciesCatalog.EMPTY, moment("29.08.2026 10:00"))?.batch?.id)
    }

    // --- Помощники --------------------------------------------------------------------------

    private fun batch(
        id: Long = 1,
        start: String,
        type: String = "Курицы",
        time: String = "",
        arhive: String = "0",
    ) = Batch(
        id = id,
        title = "Закладка",
        type = type,
        data = start,
        eggAll = 30,
        eggAllEND = 0,
        airing = "false",
        over = "false",
        arhive = arhive,
        dateEnd = "",
        note = "",
        time = time,
    )

    private fun moment(text: String): Date =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).parse(text)!!
}
