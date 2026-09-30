package ru.zaroslikov.incubator.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** Инициалы в кружке аватара, когда фото нет. */
class InitialsTest {

    @Test
    fun twoWordsGiveTwoLetters() {
        assertEquals("СЗ", initials("Семён Заросликов"))
    }

    @Test
    fun oneWordGivesOneLetter() {
        assertEquals("С", initials("семён"))
    }

    @Test
    fun extraSpacesAndWordsAreIgnored() {
        assertEquals("ИИ", initials("  Иван   Иванович  Иванов "))
    }

    @Test
    fun blankNameGivesNothing() {
        assertEquals("", initials("   "))
    }
}
