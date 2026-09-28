package ru.zaroslikov.incubator.rustore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило «сборка для RuStore»: буква «r» в конце названия версии — и только она.
 *
 * Проверяется отдельно от `BuildConfig`, потому что это единственный выключатель всего,
 * что приложение умеет через магазин, и ошибка в нём видна не в сборке, а у человека:
 * либо предложение обновиться из магазина, откуда приложение не брали, либо молчащий
 * RuStore там, где он есть.
 */
class RuStoreBuildTest {

    @Test
    fun `version with the r suffix is a rustore build`() {
        assertTrue(isRuStoreVersion("1.1.0r"))
    }

    @Test
    fun `plain version is not a rustore build`() {
        assertFalse(isRuStoreVersion("1.1.0"))
    }

    @Test
    fun `suffix case does not matter`() {
        assertTrue(isRuStoreVersion("1.1.0R"))
    }

    @Test
    fun `the letter must be the last one`() {
        assertFalse(isRuStoreVersion("1.1.0r-debug"))
        assertFalse(isRuStoreVersion("1.1.0-release"))
    }

    @Test
    fun `trailing space does not hide the suffix`() {
        assertTrue(isRuStoreVersion("1.1.0r "))
    }

    @Test
    fun `empty version is not a rustore build`() {
        assertFalse(isRuStoreVersion(""))
    }
}
