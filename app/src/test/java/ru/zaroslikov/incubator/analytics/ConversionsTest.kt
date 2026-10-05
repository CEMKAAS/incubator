package ru.zaroslikov.incubator.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionsTest {

    @Test
    fun firstEventOfAStepIsAConversion() {
        assertEquals(listOf(Milestone.FIRST_MEASUREMENT), milestonesFor(Events.MEASUREMENT_SAVED, emptySet()))
        assertEquals(listOf(Milestone.FIRST_INCUBATOR), milestonesFor(Events.INCUBATOR_CREATED, emptySet()))
    }

    @Test
    fun aReachedStepIsNotReportedAgain() {
        val reached = setOf(Milestone.FIRST_MEASUREMENT)
        assertTrue(milestonesFor(Events.MEASUREMENT_SAVED, reached).isEmpty())
    }

    @Test
    fun bothFinishPathsCountAsTheFirstFinish() {
        assertEquals(listOf(Milestone.FIRST_FINISH), milestonesFor(Events.FINISH_ON_TIME, emptySet()))
        assertEquals(listOf(Milestone.FIRST_FINISH), milestonesFor(Events.FINISH_EARLY, emptySet()))
    }

    @Test
    fun repeatBatchNeedsAFinishedIncubationFirst() {
        // Партия из двух пород — два «Инкубатор» подряд: второй не повторная закладка.
        val afterFirst = setOf(Milestone.FIRST_BATCH)
        assertTrue(milestonesFor(Events.BATCH_CREATED, afterFirst).isEmpty())

        val afterFinish = setOf(Milestone.FIRST_BATCH, Milestone.FIRST_FINISH)
        assertEquals(listOf(Milestone.REPEAT_BATCH), milestonesFor(Events.BATCH_CREATED, afterFinish))
    }

    @Test
    fun unrelatedEventsAreNotConversions() {
        assertTrue(milestonesFor(Events.OPEN_SETTINGS, emptySet()).isEmpty())
        assertTrue(milestonesFor(Events.BATCH_EDITED, emptySet()).isEmpty())
    }

    @Test
    fun databaseSeedMarksWhatIsAlreadyThere() {
        assertTrue(milestonesInDatabase(0, 0, false, 0, 0, 0).isEmpty())
        assertEquals(
            setOf(Milestone.FIRST_INCUBATOR, Milestone.FIRST_BATCH, Milestone.FIRST_MEASUREMENT),
            milestonesInDatabase(1, 1, true, 0, 0, 0),
        )
        assertEquals(
            Milestone.entries.toSet(),
            milestonesInDatabase(2, 3, true, 4, 1, 1),
        )
        // Одна закладка, и та завершена, — повторной ещё не было.
        assertTrue(Milestone.REPEAT_BATCH !in milestonesInDatabase(1, 1, true, 1, 1, 1))
    }

    @Test
    fun milestoneKeysRoundTrip() {
        Milestone.entries.forEach { assertEquals(it, Milestone.fromKey(it.event)) }
        assertEquals(null, Milestone.fromKey("UNKNOWN"))
    }

    @Test
    fun daysSinceInstallCountsWholeDaysAndNeverGoesNegative() {
        val day = 24 * 60 * 60 * 1000L
        assertEquals(0, daysSinceInstall(1_000, 1_000 + day - 1))
        assertEquals(3, daysSinceInstall(1_000, 1_000 + 3 * day))
        assertEquals(0, daysSinceInstall(10 * day, day))
    }
}
