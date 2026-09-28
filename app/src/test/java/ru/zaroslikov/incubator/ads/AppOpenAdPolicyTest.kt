package ru.zaroslikov.incubator.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило молчания рекламы при запуске: первый запуск, первый запуск после обновления
 * и инструкция на экране — молчим; обычный запуск той же версии — показываем.
 */
class AppOpenAdPolicyTest {

    @Test
    fun `first launch of an installation is muted`() {
        assertTrue(shouldMuteAppOpenAd(seenVersion = null, currentVersion = 7, guideShowing = false))
    }

    @Test
    fun `first launch after an update is muted`() {
        assertTrue(shouldMuteAppOpenAd(seenVersion = 6, currentVersion = 7, guideShowing = false))
    }

    @Test
    fun `guide on screen mutes even a seen version`() {
        assertTrue(shouldMuteAppOpenAd(seenVersion = 7, currentVersion = 7, guideShowing = true))
    }

    @Test
    fun `ordinary launch of a seen version shows`() {
        assertFalse(shouldMuteAppOpenAd(seenVersion = 7, currentVersion = 7, guideShowing = false))
    }
}
