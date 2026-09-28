package ru.zaroslikov.incubator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Статус закладки выводится из двух полей, и ровно поэтому его стоит закрепить тестом:
 * пары `arhive` × `endReason` немного, а перепутать их местами при следующей правке
 * «Завершённых» легко.
 */
class BatchStatusTest {

    private fun batch(arhive: String, endReason: String = "") = Batch(
        title = "Курицы",
        type = "Курицы",
        data = "01.08.2026",
        eggAll = 30,
        eggAllEND = 0,
        airing = "false",
        over = "false",
        arhive = arhive,
        dateEnd = "",
        note = "",
        endReason = endReason,
    )

    @Test
    fun `running batch is active`() {
        assertEquals(BatchStatus.Active, batch(arhive = "0").status)
    }

    @Test
    fun `finished batch without a reason ran its term`() {
        assertEquals(BatchStatus.Hatched, batch(arhive = "1").status)
    }

    @Test
    fun `finished batch with a reason was stopped early`() {
        assertEquals(BatchStatus.Stopped, batch(arhive = "1", endReason = "Нет оплода").status)
    }

    /** Ноль птенцов — исход завершённой в срок закладки, а не признак прерванной. */
    @Test
    fun `zero hatched alone does not make a batch stopped`() {
        assertEquals(BatchStatus.Hatched, batch(arhive = "1").copy(eggAllEND = 0).status)
    }

    /**
     * Причина у идущей закладки — состояние, которого [reopened] не оставляет; но если
     * оно всё же встретится, закладка идёт. Проверка порядка веток в `when`.
     */
    @Test
    fun `arhive wins over a stale reason`() {
        assertEquals(BatchStatus.Active, batch(arhive = "0", endReason = "Сбой инкубатора").status)
    }

    @Test
    fun `reopening a stopped batch makes it active again`() {
        val stopped = batch(arhive = "1", endReason = "Отключали свет")
        assertEquals(BatchStatus.Stopped, stopped.status)
        assertEquals(BatchStatus.Active, stopped.reopened().status)
    }

    // --- Экспорт ---

    /** Отдать файлом можно только доведённую до срока закладку с птенцами. */
    @Test
    fun `only a hatched batch with chicks is exportable`() {
        assertTrue(batch(arhive = "1").copy(eggAllEND = 20).exportable)
    }

    @Test
    fun `a running batch is not exportable`() {
        assertFalse(batch(arhive = "0").copy(eggAllEND = 20).exportable)
    }

    @Test
    fun `a stopped batch is not exportable`() {
        assertFalse(batch(arhive = "1", endReason = "Отключали свет").copy(eggAllEND = 5).exportable)
    }

    /** Ноль птенцов — итог, но не образец: делиться нечем. */
    @Test
    fun `a hatched batch with zero chicks is not exportable`() {
        assertFalse(batch(arhive = "1").copy(eggAllEND = 0).exportable)
    }
}
