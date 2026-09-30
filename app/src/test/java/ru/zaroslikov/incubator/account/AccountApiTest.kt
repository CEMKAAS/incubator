package ru.zaroslikov.incubator.account

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ответов `server_ferma` и проверки до запроса — по контракту из CLAUDE.md,
 * раздел «Account».
 */
class AccountApiTest {

    @Test
    fun tokensTakeTheLoginEmail() {
        val json = JSONObject(
            """
            {"accessToken":"a.b.c","refreshToken":"r1","tokenType":"Bearer","expiresIn":900,
             "isNewUser":true,"isNewInApp":true,
             "user":{"id":"u-1","vkUserId":null,"hasVk":false,"loginEmail":"ivan@mail.ru",
                     "hasPassword":true,"email":"other@vk.com","createdAt":"2026-09-30T10:00:00Z"}}
            """.trimIndent()
        )
        val tokens = AccountApi.parseTokens(json)
        assertEquals("a.b.c", tokens.accessToken)
        assertEquals("r1", tokens.refreshToken)
        assertEquals(900L, tokens.expiresInSeconds)
        assertEquals(AccountUser("u-1", "ivan@mail.ru", "2026-09-30T10:00:00Z"), tokens.user)
    }

    @Test
    fun tokensFallBackToProfileEmailThenToNothing() {
        val withProfile = JSONObject(
            """{"accessToken":"a","refreshToken":"r","expiresIn":900,
                "user":{"id":"u","loginEmail":null,"email":"p@mail.ru","createdAt":""}}"""
        )
        assertEquals("p@mail.ru", AccountApi.parseTokens(withProfile).user.email)

        val bare = JSONObject(
            """{"accessToken":"a","refreshToken":"r","expiresIn":900,
                "user":{"id":"u","loginEmail":null,"email":null,"createdAt":""}}"""
        )
        assertEquals("", AccountApi.parseTokens(bare).user.email)
    }

    @Test
    fun nestedErrorKeepsItsCode() {
        val error = AccountApi.parseError(
            401,
            """{"error":{"code":"INVALID_CREDENTIALS","message":"Неверная почта или пароль"}}""",
        )
        assertEquals(401, error.status)
        assertEquals(AccountError.INVALID_CREDENTIALS, error.code)
        assertEquals("Неверная почта или пароль.", error.message)
        assertFalse(error.isNetwork)
    }

    @Test
    fun englishServerTextIsReplacedWithDetails() {
        val code = AccountApi.parseError(
            400,
            """{"error":{"code":"INVALID_CODE","message":"Invalid or expired code","details":{"attemptsLeft":3}}}""",
        )
        assertEquals("Неверный код. Осталось попыток: 3.", code.message)

        val spent = AccountApi.parseError(
            400,
            """{"error":{"code":"INVALID_CODE","message":"Invalid or expired code","details":{"attemptsLeft":0}}}""",
        )
        assertTrue(spent.message.contains("Запросите новый"))

        val throttled = AccountApi.parseError(
            429,
            """{"error":{"code":"TOO_MANY_CODE_REQUESTS","message":"Too many code requests, retry in 42 s","details":{"retryAfter":42}}}""",
        )
        assertTrue(throttled.message.contains("42 с"))

        val weak = AccountApi.parseError(
            400,
            """{"error":{"code":"WEAK_PASSWORD","message":"…","details":{"reason":"common"}}}""",
        )
        assertTrue(weak.message.contains("распространён"))
    }

    @Test
    fun unknownCodeKeepsRussianServerTextOnly() {
        val russian = AccountApi.parseError(
            409,
            """{"error":{"code":"SOMETHING_NEW","message":"Что-то новое"}}""",
        )
        assertEquals("Что-то новое", russian.message)

        val english = AccountApi.parseError(
            409,
            """{"error":{"code":"SOMETHING_NEW","message":"Something new"}}""",
        )
        assertEquals("Не получилось. Попробуйте ещё раз.", english.message)
    }

    @Test
    fun nonJsonErrorBodyFallsBackByStatus() {
        val proxy = AccountApi.parseError(502, "<html>Bad Gateway</html>")
        assertEquals(AccountError.UNKNOWN, proxy.code)
        assertTrue(proxy.message.contains("недоступен"))

        val limited = AccountApi.parseError(429, "")
        assertTrue(limited.message.contains("Слишком много"))
    }

    @Test
    fun sessionCodesAreClassified() {
        fun error(code: String) = AccountError(401, code, "")
        assertTrue(error("REFRESH_TOKEN_REUSED").isSessionDead)
        assertTrue(error("INVALID_REFRESH_TOKEN").isSessionDead)
        assertTrue(error("USER_NOT_FOUND").isSessionDead)
        // Сеть и перегрузка — не повод выходить из аккаунта.
        assertFalse(AccountError.network().isSessionDead)
        assertFalse(error("BUSY").isSessionDead)
    }

    @Test
    fun emailIsNormalisedLikeOnTheServer() {
        assertEquals("ivan@mail.ru", AccountRepository.normalize("  Ivan@Mail.RU "))
    }

    @Test
    fun emailValidation() {
        assertTrue(AccountRepository.isValidEmail("ivan@mail.ru"))
        assertTrue(AccountRepository.isValidEmail(" Ivan@Mail.ru "))
        assertFalse(AccountRepository.isValidEmail("ivan@mail"))
        assertFalse(AccountRepository.isValidEmail("ivan mail@ru.ru"))
        assertFalse(AccountRepository.isValidEmail("@mail.ru"))
        assertFalse(AccountRepository.isValidEmail("a".repeat(250) + "@m.ru"))
        assertFalse(AccountRepository.isValidEmail("a@b.ru,postmaster@b.ru"))
        assertFalse(AccountRepository.isValidEmail("<a@b.ru>"))
        assertTrue(AccountRepository.isValidEmail("ivan+farm@mail.ru"))
    }

    @Test
    fun passwordFollowsTheServerPolicy() {
        assertFalse(AccountRepository.isValidPassword("abc1234"))
        assertTrue(AccountRepository.isValidPassword("abcd1234"))
        assertTrue(AccountRepository.isValidPassword("пароль12"))
        assertFalse(AccountRepository.isValidPassword("12345678"))
        assertFalse(AccountRepository.isValidPassword("abcdefgh"))
        assertTrue(AccountRepository.isValidPassword("a1" + "x".repeat(126)))
        assertFalse(AccountRepository.isValidPassword("a1" + "x".repeat(127)))
        assertFalse(AccountRepository.isValidPassword("ivan1@mail.ru", "Ivan1@Mail.ru"))
        assertTrue(AccountRepository.isValidPassword("ivan1@mail.ru", "other@mail.ru"))
    }
}
