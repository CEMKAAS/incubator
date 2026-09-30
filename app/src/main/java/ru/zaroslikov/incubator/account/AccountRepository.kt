package ru.zaroslikov.incubator.account

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.BuildConfig

/** Вошёл ли человек в аккаунт — для экрана профиля. */
sealed interface AccountState {
    /** В сборке не указан адрес сервера — аккаунтов нет, карточка не рисуется. */
    data object Unavailable : AccountState

    /**
     * Сессия ещё читается с диска. Не «вышли»: карточка в этот миг не рисуется вовсе,
     * иначе вошедший человек видел бы на долю секунды приглашение войти.
     */
    data object Checking : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val email: String, val userId: String) : AccountState
}

/**
 * Аккаунт по почте и паролю — единственный, кто говорит с сервером аккаунтов.
 *
 * Живёт в `AppContainer`, а не во ViewModel, по причине `DataTransferController`: выход
 * и отзыв токена должны доходить до конца, даже если экран закрыли.
 *
 * **Что где.** Refresh-токен и почта — в [AccountStore] (зашифровано, вне резервной
 * копии). Access-токен — только в памяти: живёт пятнадцать минут. Раз за процесс, при
 * запуске, сессия проверяется refresh-ом, заодно ротируя refresh-токен; ответ «сессии нет»
 * (токен отозван, пароль сброшен, аккаунт удалён — в том числе из «Моего хозяйства», с
 * которым аккаунт общий) значит, что человек выходит и здесь.
 *
 * **Ротация диктует два правила.** Всё, что меняет сессию, идёт под [lock]: два
 * параллельных refresh с одним токеном сервер прочёл бы как кражу и отозвал бы всё. И
 * запрос refresh вместе с записью нового токена — **неотменяемая** единица: сервер,
 * ответивший новым токеном, прежний уже погасил, и ответ, выброшенный отменой корутины
 * (человек закрыл диалог в эту секунду), оставил бы на диске погашенный токен — а его
 * следующее предъявление сервер отзывает вместе со всеми сессиями пользователя. То же с
 * входом: код подтверждения уже израсходован, и потерянные токены значили бы вход заново.
 *
 * Сессия читается с диска на [background] при создании, а не в конструкторе: чтение —
 * это Keystore и расшифровка, а создаётся репозиторий на главном потоке.
 */
class AccountRepository(context: Context) {

    private val baseUrl = BuildConfig.ACCOUNT_SERVER_URL.trim()
    private val api = AccountApi(baseUrl)
    private val store = AccountStore(context.applicationContext)
    private val lock = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Сессия этого процесса — источник правды после загрузки; диск — её копия. */
    private var session: AccountSession? = null
    private var accessToken: String? = null
    private var accessExpiresAt: Long = 0

    val isAvailable: Boolean get() = baseUrl.isNotEmpty()

    private val _state = MutableStateFlow(
        if (isAvailable) AccountState.Checking else AccountState.Unavailable
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val loaded = if (isAvailable) {
        background.launch {
            lock.withLock {
                session = store.load()
                publish()
            }
        }
    } else {
        null
    }

    /** Состояние после загрузки с диска — для тех, кто спрашивает не из UI (аналитика). */
    suspend fun currentState(): AccountState {
        loaded?.join()
        return _state.value
    }

    /**
     * Регистрация: сервер шлёт на почту код, вход — после [confirmRegistration]. Повторный
     * вызов с той же парой — это «отправить код ещё раз».
     */
    suspend fun register(email: String, password: String): AccountResult<Unit> =
        api.register(normalize(email), password)

    /** [password] — тот же, что ушёл в [register]: сервер сверяет код с ним. */
    suspend fun confirmRegistration(email: String, code: String, password: String): AccountResult<Unit> =
        signIn(email) { api.confirmRegistration(normalize(email), code.trim(), password) }

    suspend fun login(email: String, password: String): AccountResult<Unit> =
        signIn(email) { api.login(normalize(email), password) }

    suspend fun forgotPassword(email: String): AccountResult<Unit> =
        api.forgotPassword(normalize(email))

    suspend fun resetPassword(email: String, code: String, newPassword: String): AccountResult<Unit> =
        signIn(email) { api.resetPassword(normalize(email), code.trim(), newPassword) }

    /**
     * Проверка сессии при запуске — один refresh на процесс. Аккаунт общий с «Моим
     * хозяйством», и там его могут удалить или сменить пароль; без проверки «Инкубатор»
     * показывал бы вход в аккаунт, которого больше нет. Нет сети — сессия остаётся как была:
     * приложение работает без интернета и ничего не решает по молчанию сервера.
     */
    init {
        if (isAvailable) {
            background.launch {
                loaded?.join()
                if (lock.withLock { session } != null) freshAccessToken()
            }
        }
    }

    /**
     * Выход: сначала здесь, потом на сервере — и всё на [background], а не в области
     * экрана: нажатое «Выйти» обязано выйти, даже если человек тут же ушёл, а замок
     * может быть занят идущим refresh. Отзыв на сервере — с потолком по времени; не
     * дошёл — токен сам истечёт через месяц, и предъявить его отсюда уже некому.
     *
     * Возвращает, была ли сессия, — чтобы событие аналитики не шло на двойное нажатие.
     */
    fun logout(): Boolean {
        if (_state.value !is AccountState.SignedIn) return false
        background.launch {
            val ended = lock.withLock {
                val current = session
                clearLocal()
                current
            } ?: return@launch
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout(ended.refreshToken) }
        }
        return true
    }

    /**
     * Удаляет аккаунт на сервере — для обоих приложений сразу.
     *
     * `DELETE /me` сервера пароля не спрашивает, а удалить аккаунт, которого не вернуть,
     * не должен любой, взявший разблокированный телефон. Поэтому пароль проверяется
     * настоящим входом: [AccountApi.login] с почтой сессии, и удаление идёт под токеном
     * этого входа. Не удалось удалить — этот лишний вход отзывается, а сессия телефона
     * остаётся нетронутой. Всё неотменяемо: вход уже создал на сервере сессию, и
     * брошенная на полпути, она жила бы два месяца.
     */
    suspend fun deleteAccount(password: String): AccountResult<Unit> {
        loaded?.join()
        val email = session?.email
            ?: return AccountResult.Failure(AccountError(401, AccountError.UNAUTHORIZED, "Вы не вошли в аккаунт."))
        return withContext(NonCancellable + Dispatchers.IO) {
            val fresh = when (val login = api.login(email, password)) {
                is AccountResult.Failure -> return@withContext when (login.error.code) {
                    AccountError.INVALID_CREDENTIALS ->
                        AccountResult.Failure(login.error.copy(message = "Неверный пароль."))
                    else -> login
                }
                is AccountResult.Success -> login.value
            }
            val deleted = api.deleteAccount(fresh.accessToken)
            if (deleted is AccountResult.Success) {
                lock.withLock { clearLocal() }
            } else {
                withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout(fresh.refreshToken) }
            }
            deleted
        }
    }

    /** Запрос и запись нового токена — одна неотменяемая единица, см. описание класса. */
    private suspend fun freshAccessToken(): AccountResult<String> {
        loaded?.join()
        return withContext(NonCancellable + Dispatchers.IO) {
            lock.withLock { refreshLocked() }
        }
    }

    /** Под [lock]. */
    private suspend fun refreshLocked(): AccountResult<String> {
        val cached = accessToken
        if (cached != null && System.currentTimeMillis() < accessExpiresAt) {
            return AccountResult.Success(cached)
        }
        val current = session
            ?: return AccountResult.Failure(
                AccountError(401, AccountError.UNAUTHORIZED, "Вы не вошли в аккаунт.")
            )
        return when (val refreshed = api.refresh(current.refreshToken)) {
            is AccountResult.Success -> {
                remember(refreshed.value)
                AccountResult.Success(refreshed.value.accessToken)
            }
            is AccountResult.Failure -> {
                if (refreshed.error.isSessionDead) {
                    clearLocal()
                    AccountResult.Failure(
                        refreshed.error.copy(message = "Сессия закончилась — войдите в аккаунт снова.")
                    )
                } else {
                    refreshed
                }
            }
        }
    }

    /** Запрос, выдающий токены, и их запись — неотменяемо и под [lock], см. описание класса. */
    private suspend fun signIn(
        email: String,
        request: suspend () -> AccountResult<AccountTokens>,
    ): AccountResult<Unit> {
        loaded?.join()
        return withContext(NonCancellable + Dispatchers.IO) {
            lock.withLock {
                when (val result = request()) {
                    is AccountResult.Failure -> result
                    is AccountResult.Success -> {
                        remember(result.value, normalize(email))
                        AccountResult.Success(Unit)
                    }
                }
            }
        }
    }

    /**
     * Под [lock]. Новый refresh-токен заменяет прежний сразу — в памяти и на диске.
     *
     * Не записался на диск (Keystore отказал) — сессия всё равно живёт в памяти до конца
     * процесса: прежний токен сервер уже погасил, и падение здесь не вернуло бы ничего,
     * кроме упавшего приложения. После перезапуска человек просто войдёт снова.
     */
    private fun remember(tokens: AccountTokens, fallbackEmail: String? = null) {
        // Почта из ответа — подтверждённая почта входа; пустой она быть не должна, но если
        // сервер её не прислал, на экране остаётся та, что человек ввёл или что уже была.
        val email = tokens.user.email.ifBlank { fallbackEmail ?: session?.email.orEmpty() }
        val next = AccountSession(tokens.user.id, email, tokens.refreshToken)
        session = next
        if (!store.save(next)) Log.w(TAG, "Сессия аккаунта не записана на диск")
        accessToken = tokens.accessToken
        // Минута запаса: токен, истекающий в дороге до сервера, всё равно получит 401.
        accessExpiresAt = System.currentTimeMillis() +
            (tokens.expiresInSeconds - 60).coerceAtLeast(0) * 1000
        publish()
    }

    /** Под [lock]. */
    private fun clearLocal() {
        session = null
        store.clear()
        accessToken = null
        accessExpiresAt = 0
        publish()
    }

    private fun publish() {
        _state.value = session?.let { AccountState.SignedIn(it.email, it.userId) }
            ?: AccountState.SignedOut
    }

    companion object {
        private const val TAG = "Account"
        private const val LOGOUT_TIMEOUT_MS = 15_000L

        /** Та же нормализация, что на сервере: `Ivan@Mail.ru ` и `ivan@mail.ru` — одна почта. */
        fun normalize(email: String): String = email.trim().lowercase()

        // Тот же запрет, что у сервера: разделители и скобки в адресе — это попытка
        // вписать второго получателя в письмо, а не почта.
        private val EMAIL = Regex("^[^@\\s,;<>()\"\\\\]+@[^@\\s,;<>()\"\\\\]+\\.[^@\\s,;<>()\"\\\\]+$")

        /** Проверка до запроса — те же правила, что у сервера, чтобы не гонять заведомый отказ. */
        fun isValidEmail(email: String): Boolean =
            normalize(email).let { it.length <= 254 && EMAIL.matches(it) }

        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 128

        /**
         * Правило пароля сервера: 8–128 символов, хотя бы одна буква и одна цифра, не сама
         * почта. Список слишком распространённых паролей знает только сервер — его отказ
         * приходит как `WEAK_PASSWORD` с понятным текстом.
         */
        fun isValidPassword(password: String, email: String = ""): Boolean =
            password.codePointCount(0, password.length) in PASSWORD_MIN..PASSWORD_MAX &&
                password.any(Char::isLetter) &&
                password.any(Char::isDigit) &&
                (email.isBlank() || password.trim().lowercase() != normalize(email))
    }
}
