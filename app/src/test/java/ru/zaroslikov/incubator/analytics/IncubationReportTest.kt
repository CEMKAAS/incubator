package ru.zaroslikov.incubator.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.stoppedEarly

/**
 * Отчёт об инкубаторе по итогам закладки — событие «Итог инкубации».
 *
 * Тест держится в `:app` рядом с самим `IncubationReport.kt`: андроидного и сетевого
 * там ничего нет — [incubationReport] чистая, — и `gradlew :app:test` гоняет его на JVM
 * без эмулятора и без AppMetrica, как и `BatchAnalyticsTest`.
 *
 * Проверяется ровно то, из-за чего отчёт вообще заведён и что можно тихо сломать
 * правкой: **какое число уезжает под именем «Эффективность, %»**. Оно должно быть тем
 * же, что показывает вкладка «Статистика», то есть считаться только по завершённым
 * закладкам; идущая закладка, попавшая в знаменатель, занизила бы его вдвое, и отчёт
 * начал бы спорить с экраном, не сломав при этом ни одного теста.
 */
class IncubationReportTest {

    private val device = Incubator(
        id = 7,
        name = "Домашний",
        capacity = 100,
        brand = "Несушка",
        model = "БИ-2",
        price = 8_000,
        autoTurn = true,
        autoAiring = false,
    )

    private fun batch(
        id: Long,
        eggAll: Int,
        hatched: Int,
        arhive: String,
        endReason: String = "",
        eggRejected: Int = 0,
        type: String = "Курица",
        breed: String = "",
        data: String = "01.08.2026",
        dateEnd: String = "",
    ) = Batch(
        id = id,
        title = "Закладка $id",
        type = type,
        data = data,
        eggAll = eggAll,
        eggAllEND = hatched,
        airing = "false",
        over = "true",
        arhive = arhive,
        dateEnd = dateEnd,
        note = "",
        incubatorId = device.id,
        breed = breed,
        eggRejected = eggRejected,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.incubator(): Map<String, Any> =
        get("Инкубатор") as Map<String, Any>

    // --- Эффективность устройства ------------------------------------------------------

    @Test
    fun `эффективность считается только по завершённым закладкам`() {
        val finished = batch(1, eggAll = 100, hatched = 80, arhive = "1", dateEnd = "22.08.2026")
        // Идущая закладка: её сотня яиц в знаменатель попасть не должна, иначе 80 %
        // превратились бы в 40 — ровно та ошибка, от которой `StatsSlice.rate` и
        // отгораживается.
        val active = batch(2, eggAll = 100, hatched = 0, arhive = "0")

        val report = incubationReport(finished, device, listOf(finished, active), emptyList())

        assertEquals(80, report.incubator()["Эффективность, %"])
        assertEquals(1, report.incubator()["Закладок завершено"])
        assertEquals(100, report.incubator()["Яиц завершено"])
    }

    @Test
    fun `без завершённых закладок эффективности в отчёте нет`() {
        // Так не бывает на практике — отчёт шлётся после записи итога, — но «ноль
        // процентов» вместо «неизвестно» смешал бы в разрезе новичка с тем, у кого не
        // вывелось ничего, поэтому параметр именно отсутствует.
        val active = batch(1, eggAll = 50, hatched = 0, arhive = "0")

        val report = incubationReport(active, device, listOf(active), emptyList())

        assertFalse(report.incubator().containsKey("Эффективность, %"))
        assertEquals("Не завершена", report["Исход"])
    }

    @Test
    fun `порода закладки едет строкой «Порода»`() {
        val single = batch(1, eggAll = 40, hatched = 31, arhive = "1", breed = "Ломан Браун")

        val report = incubationReport(single, device, listOf(single), emptyList())

        assertEquals("Ломан Браун", report["Порода"])
    }

    // --- Исход и причина ---------------------------------------------------------------

    @Test
    fun `доведённая до срока закладка отчитывается как «В срок» и без причины`() {
        val hatched = batch(1, eggAll = 40, hatched = 31, arhive = "1", dateEnd = "22.08.2026")

        val report = incubationReport(hatched, device, listOf(hatched), emptyList())

        assertEquals("В срок", report["Исход"])
        // Пустая причина у завершённой закладки и означает «в срок»; в отчёт она не
        // уезжает — `Analytics.report` выбрасывает пустые значения.
        assertEquals("", report["Причина"])
        assertEquals(77, report["Вывод, %"])
        assertEquals(21, report["Дней"])
    }

    @Test
    fun `прерванная отчитывается как «Досрочно» и несёт причину`() {
        val stopped = batch(1, eggAll = 40, hatched = 0, arhive = "0")
            .stoppedEarly("Отключали свет", "10.08.2026")

        val report = incubationReport(stopped, device, listOf(stopped), emptyList())

        assertEquals("Досрочно", report["Исход"])
        assertEquals("Отключали свет", report["Причина"])
        assertEquals(0, report["Выведено"])
        assertEquals(0, report["Вывод, %"])
        assertEquals(9, report["Дней"])
    }

    // --- Отбраковка: два учёта, а не один ----------------------------------------------

    @Test
    fun `отбраковка складывает графу закладки и итоги её овоскопирований`() {
        val finished = batch(
            id = 1, eggAll = 100, hatched = 70, arhive = "1",
            eggRejected = 4, dateEnd = "22.08.2026",
        )
        val candlings = listOf(
            Candling(id = 1, idPT = 1, day = 7, date = "08.08.2026", rejected = 6),
            Candling(id = 2, idPT = 1, day = 14, date = "15.08.2026", rejected = 5),
            // Овоскопирование чужой закладки: приходит в общем списке инкубатора и
            // попасть в эту цифру не должно.
            Candling(id = 3, idPT = 99, day = 7, date = "08.08.2026", rejected = 30),
        )

        val report = incubationReport(finished, device, listOf(finished), candlings)

        assertEquals(15, report["Отбраковано"])
    }

    // --- Блок устройства ---------------------------------------------------------------

    @Test
    fun `блок инкубатора несёт бренд, модель и загрузку`() {
        val finished = batch(1, eggAll = 75, hatched = 60, arhive = "1", dateEnd = "22.08.2026")

        val block = incubationReport(finished, device, listOf(finished), emptyList()).incubator()

        assertEquals("Несушка", block["Бренд"])
        assertEquals("БИ-2", block["Модель"])
        assertEquals(100, block["Вместимость"])
        assertEquals(75, block["Загрузка, %"])
        assertEquals(true, block["Умеет переворачивать"])
        assertEquals(false, block["Умеет проветривать"])
    }

    @Test
    fun `незаполненные бренд и модель в блок не попадают`() {
        // Оба поля в форме инкубатора необязательны, и пустая строка в отчёте — лишняя
        // строка разреза, по которой ничего не сгруппировать.
        val plain = device.copy(brand = "", model = "", capacity = 0)
        val finished = batch(1, eggAll = 30, hatched = 25, arhive = "1", dateEnd = "22.08.2026")

        val block = incubationReport(finished, plain, listOf(finished), emptyList()).incubator()

        assertFalse(block.containsKey("Бренд"))
        assertFalse(block.containsKey("Модель"))
        // Вместимости нет — нет и загрузки: делить не на что.
        assertFalse(block.containsKey("Вместимость"))
        assertFalse(block.containsKey("Загрузка, %"))
    }

    @Test
    fun `без прочитанного устройства отчёт всё равно уходит`() {
        // Чтение базы для отчёта может не получиться, и итог закладки от этого не
        // пропадает: событие уйдёт с тем, что известно наверняка.
        val finished = batch(1, eggAll = 40, hatched = 30, arhive = "1", dateEnd = "22.08.2026")

        val report = incubationReport(finished, incubator = null, listOf(finished), emptyList())

        assertNull(report["Инкубатор"])
        assertEquals("В срок", report["Исход"])
        assertEquals(75, report["Вывод, %"])
    }

    // --- Флаги автоматики: у закладки — факт, у устройства — возможность ----------------

    @Test
    fun `флаги автоматики берутся у закладки, а не у устройства`() {
        // Устройство умеет переворачивать и не умеет проветривать; закладка сгенерирована
        // наоборот. Переписывать её флаги по нынешним возможностям устройства нельзя —
        // в её строках расписания запечено именно то, что было при создании.
        val finished = batch(1, eggAll = 40, hatched = 30, arhive = "1", dateEnd = "22.08.2026")
            .copy(over = "false", airing = "true")

        val report = incubationReport(finished, device, listOf(finished), emptyList())

        assertEquals(false, report["Автопереворот"])
        assertEquals(true, report["Автопроветривание"])
        assertTrue(report.incubator()["Умеет переворачивать"] as Boolean)
    }

    // --- Даты первых версий ------------------------------------------------------------

    @Test
    fun `без разбираемых дат параметра «Дней» нет`() {
        // У закладок первых версий даты завершения может не быть вовсе.
        val finished = batch(1, eggAll = 40, hatched = 30, arhive = "1", dateEnd = "")

        val report = incubationReport(finished, device, listOf(finished), emptyList())

        assertFalse(report.containsKey("Дней"))
    }
}
