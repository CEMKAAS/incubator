package ru.zaroslikov.incubator.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Пользователь сервера аккаунтов — то, что клиенту из него нужно. */
data class AccountUser(val id: String, val email: String, val createdAt: String)

/** Ответ входа, подтверждения регистрации, сброса пароля и refresh. */
data class AccountTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: AccountUser,
)

/**
 * Ошибка сервера или сети.
 *
 * [code] — машинный код сервера (`INVALID_CREDENTIALS`, `INVALID_CODE`, …); по нему
 * репозиторий решает, что делать с сессией. [message] — русский текст для человека: сервер
 * говорит по-русски не везде, поэтому для известных кодов текст пишет клиент
 * ([AccountApi.parseError]). `status == 0` — ответа не было вовсе.
 */
data class AccountError(val status: Int, val code: String, val message: String) {
    val isNetwork: Boolean get() = status == 0

    /** Refresh-токен мёртв — сессии больше нет, остаётся войти заново. */
    val isSessionDead: Boolean get() = code in SESSION_DEAD

    companion object {
        const val NETWORK = "NETWORK"
        const val UNKNOWN = "UNKNOWN"
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val INVALID_TOKEN = "INVALID_TOKEN"
        const val SESSION_EXPIRED = "SESSION_EXPIRED"
        const val USER_NOT_FOUND = "USER_NOT_FOUND"
        const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
        const val INVALID_CODE = "INVALID_CODE"
        const val WEAK_PASSWORD = "WEAK_PASSWORD"

        // SESSION_EXPIRED на refresh — токен выдан до сброса пароля; USER_NOT_FOUND —
        // аккаунт удалили, в том числе из «Моего хозяйства»: аккаунт у приложений общий.
        private val SESSION_DEAD = setOf(
            "REFRESH_TOKEN_REUSED",
            "REFRESH_TOKEN_EXPIRED",
            "INVALID_REFRESH_TOKEN",
            SESSION_EXPIRED,
            USER_NOT_FOUND,
        )

        fun network() = AccountError(
            0,
            NETWORK,
            "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
        )
    }
}

sealed interface AccountResult<out T> {
    data class Success<T>(val value: T) : AccountResult<T>
    data class Failure(val error: AccountError) : AccountResult<Nothing>
}

/**
 * HTTP-клиент сервера аккаунтов — `server_ferma`, общего с «Моим хозяйством». Контракт —
 * в CLAUDE.md, «Account», и в `docs/CLIENT_INTEGRATION.md` самого сервера.
 *
 * Каждый запрос несёт `X-App-Id: incubator`: без него сервер считает запрос запросом
 * «Моего хозяйства» — письма с кодом пришли бы от чужого приложения.
 *
 * `HttpURLConnection` и платформенный `org.json`, а не Ktor или Retrofit: запросов
 * десяток, все — короткий JSON туда и обратно, и библиотека ради них добавила бы в APK
 * больше, чем весь этот файл. Разбор ответов вынесен в чистые функции ([parseTokens],
 * [parseError]) — их проверяет JVM-тест.
 *
 * Все вызовы — на `Dispatchers.IO` и не бросают: и сетевой сбой, и ответ с ошибкой
 * приходят как [AccountResult.Failure].
 */
class AccountApi(baseUrl: String) {

    private val base = baseUrl.trimEnd('/') + "/api/v1"

    /**
     * Регистрация: `202`, на почту уходит код. Ответ одинаков и для уже занятой почты — туда
     * вместо кода придёт письмо «вы уже зарегистрированы». Повторный вызов с той же парой —
     * это и есть «отправить код ещё раз»: прежние коды при этом не гаснут.
     */
    suspend fun register(email: String, password: String): AccountResult<Unit> =
        call("POST", "/auth/register", json("email" to email, "password" to password)) { }

    /**
     * Подтверждение кода. [password] обязателен и должен быть тем, с которым код запрашивали:
     * код подтверждает только свою регистрацию, и чужая повторная регистрация той же почты
     * не подменит пароль владельца.
     */
    suspend fun confirmRegistration(email: String, code: String, password: String): AccountResult<AccountTokens> =
        call(
            "POST",
            "/auth/register/confirm",
            json("email" to email, "code" to code, "password" to password),
            ::parseTokens,
        )

    suspend fun login(email: String, password: String): AccountResult<AccountTokens> =
        call("POST", "/auth/login", json("email" to email, "password" to password), ::parseTokens)

    suspend fun refresh(refreshToken: String): AccountResult<AccountTokens> =
        call("POST", "/auth/refresh", json("refreshToken" to refreshToken), ::parseTokens)

    suspend fun logout(refreshToken: String): AccountResult<Unit> =
        call("POST", "/auth/logout", json("refreshToken" to refreshToken)) { }

    suspend fun forgotPassword(email: String): AccountResult<Unit> =
        call("POST", "/auth/password/forgot", json("email" to email)) { }

    suspend fun resetPassword(email: String, code: String, newPassword: String): AccountResult<AccountTokens> =
        call(
            "POST",
            "/auth/password/reset",
            json("email" to email, "code" to code, "newPassword" to newPassword),
            ::parseTokens,
        )

    /** Удаляет аккаунт целиком — для обоих приложений. Пароль сервер здесь не спрашивает. */
    suspend fun deleteAccount(accessToken: String): AccountResult<Unit> =
        call("DELETE", "/me", null, accessToken) { }

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject?,
        parse: (JSONObject) -> T,
    ): AccountResult<T> = call(method, path, body, null, parse)

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String?,
        parse: (JSONObject) -> T,
    ): AccountResult<T> = withContext(Dispatchers.IO) {
        val connection = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext AccountResult.Failure(AccountError.network())
        } catch (e: ClassCastException) {
            // Адрес сервера в сборке — не http(s). Ошибка настройки, а не сети.
            return@withContext AccountResult.Failure(
                AccountError(-1, AccountError.UNKNOWN, "Адрес сервера аккаунтов указан неверно.")
            )
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(APP_ID_HEADER, APP_ID)
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status in 200..299) {
                val jsonBody = if (text.isBlank()) JSONObject() else JSONObject(text)
                AccountResult.Success(parse(jsonBody))
            } else {
                AccountResult.Failure(parseError(status, text))
            }
        } catch (e: IOException) {
            AccountResult.Failure(AccountError.network())
        } catch (e: org.json.JSONException) {
            AccountResult.Failure(
                AccountError(-1, AccountError.UNKNOWN, "Сервер ответил непонятно. Попробуйте позже.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // «Не бросает» — это обещание всем вызовам: исключение здесь всплыло бы в
            // viewModelScope и уронило бы приложение из-за одного странного ответа.
            AccountResult.Failure(
                AccountError(-1, AccountError.UNKNOWN, "Не получилось. Попробуйте ещё раз.")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun json(vararg pairs: Pair<String, String>) = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
        const val APP_ID_HEADER = "X-App-Id"
        const val APP_ID = "incubator"

        /**
         * `AuthResponse` сервера. Почта — `loginEmail`, подтверждённая почта входа: у аккаунта
         * по почте она есть всегда. `email` — почта профиля, может прийти из VK и не быть
         * подтверждённой, поэтому она только запасной вариант.
         */
        fun parseTokens(json: JSONObject): AccountTokens {
            val user = json.getJSONObject("user")
            return AccountTokens(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresInSeconds = json.getLong("expiresIn"),
                user = AccountUser(
                    id = user.getString("id"),
                    email = user.stringOrNull("loginEmail") ?: user.stringOrNull("email").orEmpty(),
                    createdAt = user.optString("createdAt"),
                ),
            )
        }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }

        /**
         * Ошибка из тела `{"error": {"code", "message", "details"}}`. Текст для человека —
         * свой для известных кодов: сервер пишет часть сообщений по-английски
         * («Invalid or expired code»), а `details` несут то, чего в его тексте нет, —
         * сколько ждать и сколько попыток осталось. Для неизвестного кода — текст сервера,
         * а если тело не такое (прокси вернул HTML) — общий, но по статусу.
         */
        fun parseError(status: Int, body: String): AccountError {
            val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
            val code = error?.optString("code").orEmpty().ifBlank { AccountError.UNKNOWN }
            val serverMessage = error?.optString("message").orEmpty()
            val details = error?.optJSONObject("details")
            val message = messageFor(code, details)
                ?: serverMessage.takeIf { it.isNotBlank() && it.any { c -> c in 'а'..'я' || c in 'А'..'Я' } }
                ?: when {
                    status == 429 -> "Слишком много попыток. Подождите минуту и попробуйте снова."
                    status >= 500 -> "Сервер временно недоступен. Попробуйте позже."
                    else -> "Не получилось. Попробуйте ещё раз."
                }
            return AccountError(status, code, message)
        }

        private fun messageFor(code: String, details: JSONObject?): String? {
            val retryAfter = details?.optLong("retryAfter", 0L)?.takeIf { it > 0 }
            val wait = retryAfter?.let { " Попробуйте через ${waitPhrase(it)}." } ?: " Попробуйте позже."
            return when (code) {
                AccountError.INVALID_CREDENTIALS -> "Неверная почта или пароль."
                AccountError.INVALID_CODE -> {
                    val left = details?.takeIf { it.has("attemptsLeft") }?.optInt("attemptsLeft")
                    when {
                        left == null || left <= 0 -> "Код неверный или устарел. Запросите новый."
                        else -> "Неверный код. Осталось попыток: $left."
                    }
                }
                AccountError.WEAK_PASSWORD -> when (details?.optString("reason")) {
                    "common" -> "Этот пароль слишком распространён — придумайте другой."
                    "equals_email" -> "Пароль не должен совпадать с почтой."
                    else -> "Пароль — от 8 до 128 символов, хотя бы одна буква и одна цифра."
                }
                "TOO_MANY_LOGIN_ATTEMPTS" -> "Слишком много неудачных попыток входа.$wait " +
                    "Сбросить пароль можно через «Забыли пароль?»."
                "TOO_MANY_CODE_REQUESTS" -> "Код уже отправлен недавно.$wait"
                "RATE_LIMITED" -> "Слишком много запросов.$wait"
                "MAIL_QUOTA_EXHAUSTED" -> "Отправка писем временно недоступна. Попробуйте позже."
                "MAIL_SEND_FAILED" -> "Не удалось отправить письмо. Попробуйте позже."
                "EMAIL_ALREADY_USED" -> "Эта почта уже занята другим аккаунтом. Войдите или сбросьте пароль."
                "BUSY" -> "Сервер перегружен. Попробуйте через несколько секунд."
                "VALIDATION_ERROR" -> "Сервер не принял введённые данные — проверьте их."
                "UNKNOWN_APP" -> "Сервер не узнал приложение. Обновите «Инкубатор»."
                AccountError.USER_NOT_FOUND -> "Аккаунт удалён."
                AccountError.SESSION_EXPIRED -> "Сессия закончилась — войдите в аккаунт снова."
                else -> null
            }
        }

        private fun waitPhrase(seconds: Long): String = when {
            seconds < 60 -> "$seconds с"
            seconds < 3600 -> "${(seconds + 59) / 60} мин"
            else -> "${(seconds + 3599) / 3600} ч"
        }
    }
}
