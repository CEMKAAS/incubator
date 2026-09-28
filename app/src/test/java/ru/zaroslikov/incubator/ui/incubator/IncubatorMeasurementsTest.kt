package ru.zaroslikov.incubator.ui.incubator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.batch.ChartPlanLine

/**
 * Замер по инкубатору: как он раскладывается по закладкам и собирается обратно.
 * Чистая арифметика из `IncubatorMeasurements.kt`, без базы и без Compose.
 */
class IncubatorMeasurementsTest {

    @Test
    fun `копия ложится в строку сегодняшнего дня каждой цели со строкой`() {
        val targets = listOf(
            target(batchId = 1, valueId = 11),
            target(batchId = 2, valueId = 22),
            target(batchId = 3, valueId = null),
        )
        val copies = measurementCopies(targets, template(), "g1")
        assertEquals(listOf(11L, 22L), copies.map { it.idValue })
        assertEquals(listOf("g1", "g1"), copies.map { it.groupId })
        assertEquals(listOf(0L, 0L), copies.map { it.id })
    }

    @Test
    fun `автоматика закладки снимает свои колонки только с её копии`() {
        val targets = listOf(
            target(batchId = 1, valueId = 11),
            target(batchId = 2, valueId = 22, autoTurn = true),
            target(batchId = 3, valueId = 33, autoAiring = true),
        )
        val copies = measurementCopies(targets, template(over = 1, airingCount = 1, airingTime = 10), "g")
        assertEquals(listOf(1, null, 1), copies.map { it.over })
        assertEquals(listOf(10, 10, null), copies.map { it.airingTime })
        assertEquals(listOf(1, 1, null), copies.map { it.airingCount })
        // Температура общая для всех: термометр один.
        assertEquals(listOf(37.6, 37.6, 37.6), copies.map { it.temp })
    }

    @Test
    fun `журнал показывает группу одной строкой и не показывает замеры закладок`() {
        val rows = listOf(
            measurement(id = 5, idValue = 22, groupId = "b"),
            measurement(id = 4, idValue = 11, groupId = "b"),
            measurement(id = 3, idValue = 11, groupId = null),
            measurement(id = 2, idValue = 22, groupId = "a"),
            measurement(id = 1, idValue = 11, groupId = "a"),
        )
        assertEquals(listOf(5L, 2L), deviceMeasurements(rows).map { it.id })
    }

    @Test
    fun `строка группы сшивается из копий, а не берётся у первой попавшейся`() {
        // Первая копия — закладки на автоперевороте и автопроветривании: в ней пусто
        // и то и другое; число живёт во второй. Строка журнала обязана его вернуть.
        val rows = listOf(
            Measurement(id = 9, idValue = 22, time = "08:00", temp = 37.5, groupId = "g"),
            Measurement(
                id = 8, idValue = 11, time = "08:00", temp = 37.5,
                over = 1, airingCount = 1, airingTime = 10, groupId = "g",
            ),
        )
        val line = deviceMeasurements(rows).single()
        assertEquals(9L, line.id)
        assertEquals(1, line.over)
        assertEquals(1, line.airingCount)
        assertEquals(10, line.airingTime)
    }

    @Test
    fun `общая цель — среднее по целям, пустые нормы в среднее не входят`() {
        val targets = listOf(
            target(batchId = 1, valueId = 11, temp = 37.8, damp = 55.0, over = 3),
            target(batchId = 2, valueId = 22, temp = 37.5, damp = 60.0, over = null),
            target(batchId = 3, valueId = 33, temp = 37.6, damp = null, over = 4),
        )
        val plan = checkNotNull(averagePlan(targets))
        assertEquals(37.63, plan.temp)
        assertEquals("влажность усредняется по тем, у кого задана", 57.5, plan.damp)
        assertEquals("автоматика не тянет норму к нулю", 4, plan.over)
        assertEquals(2, plan.airingCount)
        assertEquals(5, plan.airingTime)
    }

    @Test
    fun `цель без ни одной нормы — пустая, а не ноль`() {
        val targets = listOf(target(batchId = 1, valueId = 11, temp = null, damp = null, over = null))
        val plan = checkNotNull(averagePlan(targets))
        assertNull(plan.temp)
        assertNull(plan.damp)
        assertNull(plan.over)
    }

    @Test
    fun `по умолчанию выбраны все цели со строкой дня, снятые не получают копию`() {
        val targets = listOf(
            target(batchId = 1, valueId = 11),
            target(batchId = 2, valueId = 22),
            target(batchId = 3, valueId = null),
        )
        assertEquals(listOf(1L, 2L), selectedTargets(targets, emptySet()).map { it.batch.id })
        assertEquals(listOf(2L), selectedTargets(targets, setOf(1L)).map { it.batch.id })
        // Снятая закладка, которой уже нет среди целей, ничего не ломает.
        assertEquals(listOf(1L, 2L), selectedTargets(targets, setOf(9L)).map { it.batch.id })
    }

    @Test
    fun `без строк дня общей цели нет`() {
        assertNull(averagePlan(listOf(target(batchId = 1, valueId = null))))
        assertNull(averagePlan(emptyList()))
    }

    @Test
    fun `правка группы переписывает показания и сохраняет привязку копий`() {
        val targets = listOf(
            target(batchId = 1, valueId = 11),
            target(batchId = 2, valueId = 22, autoTurn = true),
        )
        val copies = listOf(
            measurement(id = 4, idValue = 11, groupId = "g"),
            measurement(id = 5, idValue = 22, groupId = "g"),
            // Закладку 3 успели завершить: среди целей её нет, но копия пришла по метке
            // из базы и правится вместе с остальными, оставаясь при своём дне.
            measurement(id = 6, idValue = 33, groupId = "g"),
        )
        val updated = updatedCopies(copies, targets, template(temp = 38.0, over = 1, note = "поправил"))
        assertEquals(listOf(4L, 5L, 6L), updated.map { it.id })
        assertEquals(listOf(11L, 22L, 33L), updated.map { it.idValue })
        assertEquals(listOf("g", "g", "g"), updated.map { it.groupId })
        assertEquals(listOf(38.0, 38.0, 38.0), updated.map { it.temp })
        assertEquals(listOf(1, null, 1), updated.map { it.over })
        assertEquals("поправил", updated[0].note)
    }

    private fun target(
        batchId: Long,
        valueId: Long?,
        autoTurn: Boolean = false,
        autoAiring: Boolean = false,
        temp: Double? = 37.8,
        damp: Double? = 55.0,
        over: Int? = 3,
    ) = MeasurementTarget(
        batch = Batch(
            id = batchId,
            title = "Закладка $batchId",
            type = "Курицы",
            data = "01.08.2026",
            eggAll = 10,
            eggAllEND = 0,
            airing = autoAiring.toString(),
            over = autoTurn.toString(),
            arhive = "0",
            dateEnd = "",
            note = "",
            incubatorId = 1,
        ),
        day = 6,
        plan = valueId?.let {
            Value(
                id = it,
                day = 6,
                temp = temp,
                damp = damp,
                over = over,
                airingCount = 2,
                airingTime = 5,
                note = "",
                idPT = batchId,
            )
        },
    )

    private fun template(
        temp: Double? = 37.6,
        damp: Double? = 57.0,
        over: Int? = null,
        airingCount: Int? = null,
        airingTime: Int? = null,
        note: String = "",
    ) = Measurement(
        idValue = 0,
        time = "08:15",
        temp = temp,
        damp = damp,
        over = over,
        airingCount = airingCount,
        airingTime = airingTime,
        note = note,
    )

    private fun measurement(id: Long, idValue: Long, groupId: String?) =
        Measurement(id = id, idValue = idValue, time = "08:00", temp = 37.5, groupId = groupId)

    @Test
    fun `линии плана — по одной на цель со строкой дня, подпись — название или вид`() {
        val named = target(batchId = 1, valueId = 10, temp = 37.8, damp = 55.0)
        val nameless = target(batchId = 2, valueId = 20, temp = 37.5, damp = null)
            .let { it.copy(batch = it.batch.copy(title = "")) }
        val noRow = target(batchId = 3, valueId = null)
        val lines = planLinesOf(listOf(named, nameless, noRow))
        assertEquals(2, lines.size)
        assertEquals("Закладка 1", lines[0].label)
        assertEquals(37.8, lines[0].temp)
        assertEquals(55.0, lines[0].damp)
        assertEquals("Курицы", lines[1].label)
        assertNull(lines[1].damp)
    }
}
