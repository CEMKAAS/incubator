package ru.zaroslikov.incubator.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Закрепляет, что считается заведённым профилем, и сравнение [User] по содержимому
 * аватара, а не по ссылке на массив.
 */
class UserTest {

    @Test
    fun emptyUserIsGuest() {
        assertFalse(User().hasProfile)
        assertFalse(User(name = "   ").hasProfile)
    }

    @Test
    fun farmAndCityAloneDoNotMakeAProfile() {
        assertFalse(User(farm = "Рассвет", city = "Тамбов").hasProfile)
    }

    @Test
    fun nameOrVkMakesAProfile() {
        assertTrue(User(name = "Семён").hasProfile)
        // Имя стёрли после входа через VK — связь с VK человек не отменял.
        assertTrue(User(vkUserId = 42L).hasProfile)
        assertTrue(User(vkUserId = 42L).isVkLinked)
        assertFalse(User(name = "Семён").isVkLinked)
    }

    @Test
    fun avatarIsComparedByContent() {
        val a = User(name = "Семён", avatar = byteArrayOf(1, 2, 3))
        val b = User(name = "Семён", avatar = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b.copy(avatar = byteArrayOf(1, 2, 4)))
        assertNotEquals(a, b.copy(avatar = null))
    }
}
