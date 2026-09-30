package ru.zaroslikov.incubator.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Пользователь сервера аккаунтов — то, что клиенту из него нужно.
 *
 * [email] — почта входа (пустая у аккаунта, заведённого через VK). [name] — имя, общее с
 * «Моим хозяйством»: `displayName`, а если его не задавали — «имя фамилия», пришедшие из
 * VK. [avatarUrl] — фото из VK, если вход был через него.
 */
data class AccountUser(
    val id: String,
    val email: String,
    val createdAt: String,
    val name: String = "",
    val vkUserId: Long? = null,
    val avatarUrl: String? = null,
)

/**
 * Подписка аккаунта, как её видит сервер. [active] — действует сейчас (сервер сам сверил
 * срок); [expiresAt] — конец срока в ISO-8601, есть и у истёкшей, `null` — подписки не было.
 */
data class AccountSubscription(
    val active: Boolean,
    val planId: String?,
    val expiresAt: String?,
    /**
     * Реклама отключена — `adsDisabled` из `GET /me`, решение сервера. Сейчас он ставит его
     * ровно по [active], но флаг отдельный: условие может расшириться, и клиент тогда
     * узнает об этом без обновления. Где флага нет (ответ платежа), — по [active].
     */
    val adsDisabled: Boolean = active,
)

/**
 * До какого момента (мс эпохи) реклама отключена по этому ответу; `0` — не отключена.
 * Действующая подписка без читаемого срока — отключена «пока сервер не скажет иначе»:
 * следующий ответ о подписке перезапишет срок.
 */
fun AccountSubscription.adFreeUntil(): Long {
    if (!adsDisabled) return 0L
    return expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: Long.MAX_VALUE
}

/** Действует ли отключение рекламы с кэшированным сроком [until] в момент [now]. */
fun adFreeAt(until: Long, now: Long): Boolean = until > now

/**
 * Тариф Premium с сервера (`GET /subscription/plans`). [priceDisplay] — цена, уже
 * написанная сервером для человека («299 ₽»): клиент её не форматирует, чтобы валюта и
 * копейки выглядели одинаково в обоих приложениях.
 */
data class PremiumPlan(
    val id: String,
    val title: String,
    val priceDisplay: String,
    val durationDays: Int,
    /** Цена в копейках и валюта — для «≈ 83 ₽ в месяц» у длинных тарифов. */
    val amountKopecks: Long = 0,
    val currency: String = "",
)

/** Созданный платёж: [confirmationUrl] открывается в браузере, дальше — опрос по [paymentId]. */
data class PremiumCheckout(
    val paymentId: String,
    val status: String,
    val confirmationUrl: String?,
)

/**
 * Состояние платежа при опросе. [status] — `pending`, `waiting_for_capture`, `succeeded`,
 * `canceled`; [subscription] — подписка аккаунта на момент ответа.
 */
data class PremiumPayment(
    val status: String,
    val subscription: AccountSubscription,
) {
    val isFinal: Boolean get() = status == SUCCEEDED || status == CANCELED

    companion object {
        const val SUCCEEDED = "succeeded"
        const val CANCELED = "canceled"
    }
}

/** Ответ входа, подтверждения регистрации, сброса пароля и refresh. */
data class AccountTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: AccountUser,
    /** Аккаунт заведён этим самым входом — первый вход через незнакомый VK. */
    val isNewUser: Boolean = false,
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

    /** Access-токен больше не годится — поможет refresh. */
    val isAccessRejected: Boolean get() = code in ACCESS_REJECTED

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

        private val ACCESS_REJECTED = setOf(UNAUTHORIZED, INVALID_TOKEN, SESSION_EXPIRED)

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
 * OkHttp, а не `HttpURLConnection`: имя меняется `PATCH /me`, а `HttpURLConnection` метода
 * PATCH не знает. Сам OkHttp в APK уже был — его приносит AppMetrica, — так что размер от
 * этого не вырос. Retrofit ради десятка коротких JSON-запросов не нужен; ответы разбирает
 * платформенный `org.json`, и разбор вынесен в чистые функции ([parseTokens],
 * [parseError]) — их проверяет JVM-тест.
 *
 * Все вызовы — на `Dispatchers.IO` и не бросают: и сетевой сбой, и ответ с ошибкой
 * приходят как [AccountResult.Failure].
 */
class AccountApi(baseUrl: String) {

    private val base = baseUrl.trimEnd('/') + "/api/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Потолок на весь запрос: `execute()` блокирует поток и отмену корутины не слышит,
        // так что `withTimeoutOrNull` вокруг вызова сам по себе ничего не ограничивал бы.
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

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

    /**
     * Вход через VK: access-токен VK ID SDK, сервер проверяет его у VK сам. Первый вход —
     * это и регистрация; аккаунт тот же, что у «Моего хозяйства», если там входили через
     * тот же VK.
     */
    suspend fun loginWithVk(vkAccessToken: String): AccountResult<AccountTokens> =
        call("POST", "/auth/vk", json("accessToken" to vkAccessToken), ::parseTokens)

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

    /** Имя аккаунта — общее с «Моим хозяйством». Сервер обрезает пробелы и берёт 1–100 символов. */
    suspend fun updateName(accessToken: String, name: String): AccountResult<Unit> =
        call("PATCH", "/me", json("displayName" to name), accessToken) { }

    /** Подписка аккаунта — из `GET /me`, где она лежит рядом с пользователем. */
    suspend fun subscription(accessToken: String): AccountResult<AccountSubscription> =
        call("GET", "/me", null, accessToken, ::parseMeSubscription)

    /** Тарифы Premium. Эндпоинт публичный, токен ему не нужен. */
    suspend fun premiumPlans(): AccountResult<List<PremiumPlan>> =
        callArray("/subscription/plans", ::parsePlans)

    /** Создаёт платёж за [planId]; сервер отвечает ссылкой на страницу оплаты. */
    suspend fun checkout(accessToken: String, planId: String): AccountResult<PremiumCheckout> =
        call("POST", "/subscription/checkout", json("planId" to planId), accessToken, ::parseCheckout)

    /** Статус платежа; сервер сам сверяется с платёжной системой, пока платёж не завершён. */
    suspend fun payment(accessToken: String, paymentId: String): AccountResult<PremiumPayment> =
        call("GET", "/subscription/payments/${encodePath(paymentId)}", null, accessToken, ::parsePayment)

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
    ): AccountResult<T> = request(method, path, body, bearer) { text ->
        parse(if (text.isBlank()) JSONObject() else JSONObject(text))
    }

    /** GET без токена, чей ответ — JSON-массив, а не объект. */
    private suspend fun <T> callArray(path: String, parse: (JSONArray) -> T): AccountResult<T> =
        request("GET", path, null, null) { text -> parse(JSONArray(text)) }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String?,
        parse: (String) -> T,
    ): AccountResult<T> = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .header(APP_ID_HEADER, APP_ID)
                .apply { bearer?.let { header("Authorization", "Bearer $it") } }
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
        } catch (e: IllegalArgumentException) {
            // Адрес сервера в сборке — не http(s). Ошибка настройки, а не сети.
            return@withContext AccountResult.Failure(
                AccountError(-1, AccountError.UNKNOWN, "Адрес сервера аккаунтов указан неверно.")
            )
        }
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    AccountResult.Success(parse(text))
                } else {
                    AccountResult.Failure(parseError(response.code, text))
                }
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
        }
    }

    private fun json(vararg pairs: Pair<String, String>) = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 15L
        private const val CALL_TIMEOUT_SECONDS = 20L
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val APP_ID_HEADER = "X-App-Id"
        const val APP_ID = "incubator"

        /** `AuthResponse` сервера — токены и пользователь. */
        fun parseTokens(json: JSONObject): AccountTokens = AccountTokens(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresInSeconds = json.getLong("expiresIn"),
            user = parseUser(json.getJSONObject("user")),
            isNewUser = json.optBoolean("isNewUser", false),
        )

        /**
         * `UserDto` сервера. Почта — `loginEmail`, подтверждённая почта входа; `email` —
         * почта профиля, может прийти из VK неподтверждённой, поэтому она только запасной
         * вариант. Имя — `displayName`, а без него «имя фамилия» из VK.
         */
        fun parseUser(user: JSONObject): AccountUser = AccountUser(
            id = user.getString("id"),
            email = user.stringOrNull("loginEmail") ?: user.stringOrNull("email").orEmpty(),
            createdAt = user.optString("createdAt"),
            name = user.stringOrNull("displayName")
                ?: listOfNotNull(user.stringOrNull("firstName"), user.stringOrNull("lastName"))
                    .joinToString(" "),
            vkUserId = user.stringOrNull("vkUserId")?.toLongOrNull(),
            avatarUrl = user.stringOrNull("avatarUrl"),
        )

        /** `subscription` из `GET /me`: `{active, planId, expiresAt}`, срок — ISO-8601 или `null`. */
        fun parseSubscription(json: JSONObject): AccountSubscription = AccountSubscription(
            active = json.optBoolean("active", false),
            planId = json.stringOrNull("planId"),
            expiresAt = json.stringOrNull("expiresAt"),
        )

        /** Весь ответ `GET /me`: подписка плюс `adsDisabled`, лежащий рядом с ней. */
        fun parseMeSubscription(json: JSONObject): AccountSubscription {
            val subscription = parseSubscription(json.getJSONObject("subscription"))
            return subscription.copy(
                adsDisabled = if (json.has("adsDisabled")) json.optBoolean("adsDisabled") else subscription.active
            )
        }

        /** `GET /subscription/plans`: `[{id, title, price: {display, …}, durationDays}]`. */
        fun parsePlans(json: JSONArray): List<PremiumPlan> = (0 until json.length()).map { i ->
            val plan = json.getJSONObject(i)
            val price = plan.optJSONObject("price")
            PremiumPlan(
                id = plan.getString("id"),
                title = plan.optString("title").trim(),
                priceDisplay = price?.optString("display").orEmpty().trim(),
                durationDays = plan.optInt("durationDays", 0),
                amountKopecks = price?.optLong("amountKopecks", 0L) ?: 0L,
                currency = price?.optString("currency").orEmpty(),
            )
        }

        /** `POST /subscription/checkout` → `201 {paymentId, status, confirmationUrl}`. */
        fun parseCheckout(json: JSONObject): PremiumCheckout = PremiumCheckout(
            paymentId = json.getString("paymentId"),
            status = json.optString("status"),
            confirmationUrl = json.stringOrNull("confirmationUrl"),
        )

        /** `GET /subscription/payments/{id}` → `{status, subscription, …}`. */
        fun parsePayment(json: JSONObject): PremiumPayment = PremiumPayment(
            status = json.optString("status"),
            subscription = parseSubscription(json.getJSONObject("subscription")),
        )

        private fun encodePath(segment: String): String =
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

        private fun JSONObject.stringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).trim().ifBlank { null }

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
                "VK_AUTH_FAILED" -> "VK не подтвердил вход. Попробуйте войти ещё раз."
                "VK_UNAVAILABLE" -> "VK сейчас не отвечает. Попробуйте позже."
                "VK_NOT_CONFIGURED" -> "Вход через VK на сервере пока не настроен."
                "VALIDATION_ERROR" -> "Сервер не принял введённые данные — проверьте их."
                "UNKNOWN_APP" -> "Сервер не узнал приложение. Обновите «Инкубатор»."
                "PLAN_NOT_FOUND" -> "Этот тариф больше недоступен. Откройте Premium заново."
                "PAYMENT_NOT_FOUND" -> "Платёж не найден."
                "PAYMENT_PROVIDER_ERROR" -> "Платёжная система не отвечает. Попробуйте позже."
                "TOO_MANY_PENDING_PAYMENTS" -> "Уже есть неоплаченные платежи. Завершите их или подождите " +
                    "несколько минут."
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
