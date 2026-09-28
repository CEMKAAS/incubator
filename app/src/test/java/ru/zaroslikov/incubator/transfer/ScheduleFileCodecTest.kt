package ru.zaroslikov.incubator.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.CustomSpeciesDay
import ru.zaroslikov.incubator.domain.model.Value

/**
 * Файл расписания туда и обратно. Тест на JVM, без эмулятора: кодек и шифр — чистые
 * функции над байтами, андроидного в них ничего нет (org.json для теста подключён
 * отдельно, см. `app/build.gradle.kts`).
 */
class ScheduleFileCodecTest {

    private fun day(day: Int, temp: Double? = 37.8, over: Int? = 6, airingCount: Int? = 2) = Value(
        id = 0, day = day, temp = temp, damp = 55.0, over = over,
        airingCount = airingCount, airingTime = 5, note = "", idPT = 0,
    )

    private val export = ScheduleExport(
        type = "Курицы",
        // День на автоматике: нормы нет, и null обязан вернуться null, а не нулём.
        plan = listOf(day(1), day(2, temp = null, over = null, airingCount = null), day(3)),
        fact = listOf(day(1, temp = 37.6), day(2, temp = 37.7), day(3, temp = 37.9)),
        measurementCount = 12,
        customSpecies = null,
        breed = "Ломан Браун",
        incubatorBrand = "Блиц",
        incubatorModel = "Норма 72",
        appVersion = "1.1.0r",
        exportedAt = "12.09.2026",
    )

    @Test
    fun `round trip keeps the species, the plan and the measured average`() {
        assertEquals(export, ScheduleFileCodec.decode(ScheduleFileCodec.encode(export)))
    }

    /** Порода и инкубатор — на показ; не указаны — пусто, и подпись из них не собирается. */
    @Test
    fun `breed and incubator travel for display only`() {
        val decoded = ScheduleFileCodec.decode(ScheduleFileCodec.encode(export))
        assertEquals("Ломан Браун", decoded.breed)
        assertEquals("Блиц Норма 72", decoded.incubatorLabel)
        val bare = ScheduleFileCodec.decode(
            ScheduleFileCodec.encode(export.copy(breed = "", incubatorBrand = "", incubatorModel = " "))
        )
        assertEquals("", bare.breed)
        assertEquals("", bare.incubatorLabel)
        assertEquals("Норма 72", export.copy(incubatorBrand = "").incubatorLabel)
    }

    /** Без замеров нет и среднего, а число замеров без среднего — ноль, что бы ни стояло в файле. */
    @Test
    fun `no measurements means no fact`() {
        val decoded = ScheduleFileCodec.decode(ScheduleFileCodec.encode(export.copy(fact = null, measurementCount = 5)))
        assertNull(decoded.fact)
        assertEquals(0, decoded.measurementCount)
    }

    /** Идентификаторы дней — чужие, в файл они не попадают; порядок — по дню. */
    @Test
    fun `ids do not travel and days come back sorted`() {
        val decoded = ScheduleFileCodec.decode(
            ScheduleFileCodec.encode(
                export.copy(plan = listOf(day(2).copy(id = 9, idPT = 7), day(1).copy(id = 8, idPT = 7)))
            )
        )
        assertEquals(listOf(1, 2), decoded.plan.map { it.day })
        assertEquals(listOf(0L, 0L), decoded.plan.map { it.id })
        assertEquals(listOf(0L, 0L), decoded.plan.map { it.idPT })
    }

    @Test
    fun `a custom species travels with its days`() {
        val species = CustomSpecies(
            id = 4,
            name = "Цесарки",
            days = listOf(
                CustomSpeciesDay(id = 9, speciesId = 4, day = 1, temp = 37.8, damp = 60.0, over = 4, airingCount = null, airingTime = null),
                CustomSpeciesDay(id = 10, speciesId = 4, day = 2, temp = 37.8, damp = 60.0, over = 4, airingCount = 2, airingTime = 10, candling = true),
            ),
        )
        val decoded = ScheduleFileCodec.decode(
            ScheduleFileCodec.encode(export.copy(type = "Цесарки", customSpecies = species))
        )
        val expectedDays = species.days.map { it.copy(id = 0, speciesId = 0) }
        assertEquals(expectedDays, decoded.customSpecies?.days)
        assertEquals(listOf(2), decoded.customSpecies?.candlingDays)
        // Вид, который заведут у получателя, — из описания, с именем закладки.
        assertEquals(expectedDays, decoded.speciesToCreate().days)
        assertEquals("Цесарки", decoded.speciesToCreate().name)
    }

    /** Без описания вида он собирается из плана — сроком в число дней, без овоскопирований. */
    @Test
    fun `a species without a description is built from the plan`() {
        val species = export.speciesToCreate()
        assertEquals("Курицы", species.name)
        assertEquals(3, species.length)
        assertEquals(listOf(37.8, null, 37.8), species.days.map { it.temp })
        assertTrue(species.candlingDays.isEmpty())
        assertNull(export.customSpecies)
    }

    /** Два файла одного расписания не совпадают байт в байт — nonce у каждого свой. */
    @Test
    fun `every file is sealed with its own nonce`() {
        val a = ScheduleFileCodec.encode(export)
        val b = ScheduleFileCodec.encode(export)
        assertFalse(a.contentEquals(b))
        assertEquals(ScheduleFileCodec.decode(a), ScheduleFileCodec.decode(b))
    }

    @Test
    fun `the file is not readable as text`() {
        val text = ScheduleFileCodec.encode(export).toString(Charsets.ISO_8859_1)
        assertFalse(text.contains("Курицы"))
        assertFalse(text.contains("\"plan\""))
    }

    @Test
    fun `a changed byte is refused`() {
        val bytes = ScheduleFileCodec.encode(export)
        bytes[bytes.size - 20] = (bytes[bytes.size - 20] + 1).toByte()
        assertRefused(bytes, "повреждён")
    }

    @Test
    fun `a foreign file is refused by its first bytes`() {
        assertRefused("{\"plan\":[]}".toByteArray(), "не файл расписания")
        assertRefused(ByteArray(3), "не файл расписания")
    }

    private fun assertRefused(bytes: ByteArray, expectedWord: String) {
        try {
            ScheduleFileCodec.decode(bytes)
            fail("файл должен быть отклонён")
        } catch (e: ScheduleFileException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(expectedWord))
        }
    }

    @Test
    fun `file name carries the species and the batch title`() {
        val batch = Batch(
            title = "Весенняя партия", type = "Курицы", data = "01.03.2026", eggAll = 60,
            eggAllEND = 45, airing = "false", over = "false", arhive = "1", dateEnd = "22.03.2026", note = "",
        )
        assertEquals("Расписание Курицы — Весенняя партия.incs", ScheduleTransferController.fileNameFor(batch))
        assertEquals("Расписание Курицы.incs", ScheduleTransferController.fileNameFor(batch.copy(title = " ")))
        assertEquals("Расписание Курицы.incs", ScheduleTransferController.fileNameFor(batch.copy(title = "Курицы")))
        assertEquals(
            "Расписание Курицы — Партия 1 2.incs",
            ScheduleTransferController.fileNameFor(batch.copy(title = "Партия 1/2?")),
        )
    }
}
