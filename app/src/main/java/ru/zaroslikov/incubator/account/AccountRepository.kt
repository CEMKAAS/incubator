package ru.zaroslikov.incubator.account

import android.content.Context
import android.util.Log
import ru.zaroslikov.incubator.ads.AdFree
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.profile.AvatarImage
import ru.zaroslikov.incubator.vkid.VkId

/** Вошёл ли человек в аккаунт — а значит, есть ли у него профиль. */
sealed interface AccountState {
    /** В сборке не указан адрес сервера — аккаунтов, а с ними и профиля, нет. */
    data object Unavailable : AccountState

    /**
     * Сессия ещё читается с диска. Не «вышли»: экран в этот миг ждёт, иначе вошедший
     * человек видел бы на долю секунды приглашение войти.
     */
    data object Checking : AccountState
    data object SignedOut : AccountState

    /** [email] пуст у аккаунта, заведённого через VK; [viaVk] — как вошли на этом телефоне. */
    data class SignedIn(val email: String, val userId: String, val viaVk: Boolean) : AccountState
}

/**
 * Аккаунт — и профиль, который без него не существует.
 *
 * **Профиль — это аккаунт.** Имя, хозяйство, город и фото показываются только вошедшему;
 * вход (почта или VK) заводит профиль, выход и удаление аккаунта его стирают. Имя хранится
 * на сервере — оно общее с «Моим хозяйством», — и в строку `User` ложится копией:
 * при каждом входе и при проверке сессии на запуске побеждает серверное. Хозяйство, город
 * и фото — только на телефоне, в той же строке. Все записи в неё идут через [editProfile]
 * под [profileLock], чтобы вход, правка на экране и скачанное фото не затирали друг друга.
 *
 * Живёт в `AppContainer`, а не во ViewModel, по причине `DataTransferController`: выход,
 * отзыв токена и стирание профиля должны доходить до конца, даже если экран закрыли.
 *
 * **Что где.** Refresh-токен и почта — в [AccountStore] (зашифровано, вне резервной
 * копии). Access-токен — только в памяти: живёт пятнадцать минут. Раз за процесс, при
 * запуске, сессия проверяется refresh-ом, заодно ротируя refresh-токен; ответ «сессии нет»
 * (токен отозван, пароль сброшен, аккаунт удалён — в том числе из «Моего хозяйства», с
 * которым аккаунт общий) значит, что человек выходит и здесь — и профиль стирается.
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
class AccountRepository(
    context: Context,
    private val items: ItemsRepository,
) {

    private val baseUrl = BuildConfig.ACCOUNT_SERVER_URL.trim()
    private val api = AccountApi(baseUrl)
    private val store = AccountStore(context.applicationContext)
    private val lock = Mutex()
    private val profileLock = Mutex()
    // Фоновая работа аккаунта — стирание профиля, фото VK, отзыв токена — не должна ронять
    // процесс: её сбой оставляет всё как было, а следующий вход или запуск доделает.
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "Фоновая работа аккаунта не удалась", e) }
    )

    /** Сессия этого процесса — источник правды после загрузки; диск — её копия. */
    private var session: AccountSession? = null
    private var accessToken: String? = null
    private var accessExpiresAt: Long = 0

    val isAvailable: Boolean get() = baseUrl.isNotEmpty()

    private val _state = MutableStateFlow(
        if (isAvailable) AccountState.Checking else AccountState.Unavailable
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _subscription = MutableStateFlow<AccountSubscription?>(null)

    /**
     * Подписка вошедшего — с сервера, в памяти процесса. `null` — ещё не узнали (нет сети,
     * не вошли): экран тогда о подписке молчит, а не говорит «нет подписки» наугад.
     */
    val subscription: StateFlow<AccountSubscription?> = _subscription.asStateFlow()

    /** Спрашивает у сервера подписку вошедшего. Нет сети — остаётся прежний ответ. */
    suspend fun refreshSubscription() {
        loaded?.join()
        val userId = lock.withLock { session?.userId } ?: return
        val result = withAccess { token -> api.subscription(token) }
        if (result is AccountResult.Success && lock.withLock { session?.userId } == userId) {
            applySubscription(result.value)
        }
    }

    /**
     * Ответ сервера о подписке — в состояние экрана и в выключатель рекламы. Срок «без
     * рекламы» ещё и кэшируется на диск: без сети и на первом кадре следующего запуска
     * ответа сервера нет, а реклама при запуске грузится именно тогда.
     */
    private fun applySubscription(subscription: AccountSubscription) {
        _subscription.value = subscription
        val until = subscription.adFreeUntil()
        store.setAdFreeUntil(until)
        AdFree.set(adFreeAt(until, System.currentTimeMillis()))
    }

    /** Выход или отсутствие сессии: реклама снова для всех, кэш срока стёрт. */
    private fun forgetAdFree() {
        store.setAdFreeUntil(0L)
        AdFree.set(false)
    }

    /** Тарифы Premium — публичный список, вход для него не нужен. */
    suspend fun premiumPlans(): AccountResult<List<PremiumPlan>> = api.premiumPlans()

    /** Платёж за [planId] от имени вошедшего; ответ несёт ссылку на страницу оплаты. */
    suspend fun checkout(planId: String): AccountResult<PremiumCheckout> =
        withAccess { token -> api.checkout(token, planId) }

    /**
     * Статус платежа. Подписка из ответа сразу ложится в [subscription] — значок Premium
     * появляется в ту же секунду, как оплата прошла, без отдельного `GET /me`.
     */
    suspend fun paymentStatus(paymentId: String): AccountResult<PremiumPayment> {
        loaded?.join()
        val userId = lock.withLock { session?.userId } ?: return AccountResult.Failure(
            AccountError(401, AccountError.UNAUTHORIZED, "Войдите в аккаунт.")
        )
        val result = withAccess { token -> api.payment(token, paymentId) }
        if (result is AccountResult.Success && lock.withLock { session?.userId } == userId) {
            applySubscription(result.value.subscription)
        }
        return result
    }

    private val loaded = if (isAvailable) {
        background.launch {
            lock.withLock {
                session = store.load()
                // Срок без рекламы без сессии ничей: файл сессии не расшифровался или
                // вышли в другой версии — реклама возвращается.
                if (session == null) forgetAdFree()
                publish()
            }
        }
    } else {
        null
    }

    /**
     * Проверка сессии при запуске — один refresh на процесс. Аккаунт общий с «Моим
     * хозяйством», и там его могут удалить, сменить пароль или имя; без проверки
     * «Инкубатор» показывал бы профиль аккаунта, которого больше нет. Нет сети — сессия
     * остаётся как была: приложение работает без интернета и ничего не решает по молчанию
     * сервера.
     */
    init {
        if (isAvailable) {
            background.launch {
                loaded?.join()
                if (lock.withLock { session } != null) {
                    freshAccessToken()
                    refreshSubscription()
                }
            }
        }
    }

    /** Состояние после загрузки с диска — для тех, кто спрашивает не из UI (аналитика). */
    suspend fun currentState(): AccountState {
        loaded?.join()
        return _state.value
    }

    /**
     * Access-токен вошедшего — для запросов, которым вход не обязателен (истории): с ним
     * сервер помнит просмотры за аккаунтом. `null` — не вошли или токен не добыть (нет
     * сети для refresh); такой запрос просто уходит без него.
     */
    suspend fun optionalAccessToken(): String? {
        loaded?.join()
        if (lock.withLock { session } == null) return null
        return (freshAccessToken() as? AccountResult.Success)?.value
    }

    /**
     * Регистрация: сервер шлёт на почту код, вход — после [confirmRegistration]. Повторный
     * вызов с той же парой — это «отправить код ещё раз».
     */
    suspend fun register(email: String, password: String): AccountResult<Unit> =
        api.register(normalize(email), password)

    /** [password] — тот же, что ушёл в [register]: сервер сверяет код с ним. */
    suspend fun confirmRegistration(email: String, code: String, password: String): AccountResult<Unit> =
        signIn(email, viaVk = false) { api.confirmRegistration(normalize(email), code.trim(), password) }

    suspend fun login(email: String, password: String): AccountResult<Unit> =
        signIn(email, viaVk = false) { api.login(normalize(email), password) }

    /** Вход через VK: токен VK ID SDK сервер проверяет сам. Первый вход — это и регистрация. */
    suspend fun loginWithVk(vkAccessToken: String): AccountResult<Unit> =
        signIn(email = null, viaVk = true) { api.loginWithVk(vkAccessToken) }

    suspend fun forgotPassword(email: String): AccountResult<Unit> =
        api.forgotPassword(normalize(email))

    suspend fun resetPassword(email: String, code: String, newPassword: String): AccountResult<Unit> =
        signIn(email, viaVk = false) { api.resetPassword(normalize(email), code.trim(), newPassword) }

    /**
     * Меняет имя — сначала на сервере, потом в строке профиля. Имя общее с «Моим
     * хозяйством», и записанное только на телефоне при следующем входе затёрлось бы
     * серверным; поэтому без ответа сервера оно не меняется вовсе.
     */
    suspend fun updateName(name: String): AccountResult<Unit> {
        val trimmed = name.trim()
        val result = withAccess { token -> api.updateName(token, trimmed) }
        if (result is AccountResult.Success) {
            withContext(NonCancellable) { editProfile { it.copy(name = trimmed) } }
        }
        return result
    }

    /** Строка профиля как она есть сейчас — например, чтобы после входа понять, есть ли имя. */
    suspend fun profile(): User = items.getUser().first()

    /**
     * Читает строку профиля, отдаёт её [transform] и пишет ответ — под [profileLock].
     * `null` из [transform] значит «писать нечего». Возвращает прежнюю и новую строку.
     * Без сессии не пишет ничего: профиль есть только у вошедшего.
     */
    suspend fun editProfile(transform: (User) -> User?): Pair<User, User>? =
        editProfileOf(expectedUserId = null, transform)

    /**
     * [editProfile], но только если вошёл именно [expectedUserId] (или любой, если `null`).
     * Сессия сверяется **под** [profileLock], а не до него: иначе между проверкой и записью
     * успели бы выйти и войти другим аккаунтом, и имя одного легло бы в профиль другого.
     * Порядок замков — всегда `profileLock`, потом `lock`; обратного пути нет ни у кого.
     */
    private suspend fun editProfileOf(
        expectedUserId: String?,
        transform: (User) -> User?,
    ): Pair<User, User>? {
        loaded?.join()
        return profileLock.withLock {
            val signedIn = lock.withLock { session?.userId } ?: return@withLock null
            if (expectedUserId != null && signedIn != expectedUserId) return@withLock null
            val current = items.getUser().first()
            val updated = transform(current) ?: return@withLock null
            if (updated != current) items.saveUser(updated)
            current to updated
        }
    }

    /**
     * Выход: сначала здесь, потом на сервере — и всё на [background], а не в области
     * экрана: нажатое «Выйти» обязано выйти, даже если человек тут же ушёл, а замок
     * может быть занят идущим refresh. Профиль стирается вместе с сессией ([clearLocal]);
     * [eraseProfile] = `false` — только для «Удалить все данные», которое удаляет файл базы
     * целиком и писать в неё уже нельзя. Отзыв на сервере — с потолком по времени.
     *
     * Сессия, ещё читающаяся с диска (`Checking`), тоже выходит: решение принимается после
     * загрузки, а не по состоянию на момент нажатия. Возвращает, была ли сессия известна
     * как открытая, — чтобы событие аналитики не шло на двойное нажатие.
     */
    fun logout(eraseProfile: Boolean = true): Boolean {
        val known = _state.value
        if (known == AccountState.SignedOut || known == AccountState.Unavailable) return false
        background.launch {
            loaded?.join()
            val ended = lock.withLock {
                val current = session
                if (current != null) clearLocal(eraseProfile)
                current
            } ?: return@launch
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout(ended.refreshToken) }
        }
        return known is AccountState.SignedIn
    }

    /**
     * Удаляет аккаунт на сервере — для обоих приложений сразу — и профиль на телефоне.
     *
     * `DELETE /me` сервера пароля не спрашивает, а удалить аккаунт, которого не вернуть,
     * не должен любой, взявший разблокированный телефон. Поэтому личность подтверждается
     * настоящим входом — паролем здесь или VK в [deleteAccountWithVk], — и удаление идёт
     * под токеном этого входа.
     */
    suspend fun deleteAccount(password: String): AccountResult<Unit> {
        loaded?.join()
        val email = lock.withLock { session?.email }?.takeIf { it.isNotBlank() }
            ?: return AccountResult.Failure(
                AccountError(401, AccountError.UNAUTHORIZED, "У этого аккаунта нет входа по почте.")
            )
        return deleteConfirmedBy {
            when (val login = api.login(email, password)) {
                is AccountResult.Failure -> if (login.error.code == AccountError.INVALID_CREDENTIALS) {
                    AccountResult.Failure(login.error.copy(message = "Неверный пароль."))
                } else {
                    login
                }
                is AccountResult.Success -> login
            }
        }
    }

    /** Удаление для аккаунта, в который входили через VK: подтверждается новым входом в VK. */
    suspend fun deleteAccountWithVk(vkAccessToken: String): AccountResult<Unit> =
        deleteConfirmedBy { api.loginWithVk(vkAccessToken) }

    /**
     * Общая часть удаления. Вход, подтверждающий личность, должен оказаться **этим же**
     * аккаунтом: VK, привязанный к другому аккаунту, иначе удалил бы тот. Не удалось
     * удалить — лишний вход отзывается, сессия телефона остаётся нетронутой. Всё
     * неотменяемо: вход уже создал на сервере сессию, и брошенная на полпути, она жила бы
     * два месяца.
     */
    private suspend fun deleteConfirmedBy(
        confirm: suspend () -> AccountResult<AccountTokens>,
    ): AccountResult<Unit> {
        loaded?.join()
        val userId = lock.withLock { session?.userId }
            ?: return AccountResult.Failure(AccountError(401, AccountError.UNAUTHORIZED, "Вы не вошли в аккаунт."))
        return withContext(NonCancellable + Dispatchers.IO) {
            val fresh = when (val login = confirm()) {
                is AccountResult.Failure -> return@withContext login
                is AccountResult.Success -> login.value
            }
            if (fresh.user.id != userId) {
                // Сервер заводит аккаунт на любой незнакомый VK — и такой, только что
                // рождённый подтверждением, остался бы пустым аккаунтом, общим с «Моим
                // хозяйством». Его и удаляем; чужой, существовавший до нас, — только выходим.
                if (fresh.isNewUser) {
                    api.deleteAccount(fresh.accessToken)
                } else {
                    withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { api.logout(fresh.refreshToken) }
                }
                return@withContext AccountResult.Failure(
                    AccountError(-1, AccountError.UNKNOWN, "Этот вход ведёт в другой аккаунт — удалять его не будем.")
                )
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

    /**
     * Выполняет [block] с живым access-токеном, получив его через refresh, если нужно.
     * Отказ сервера по токену — его отозвали между делом: одна повторная попытка со свежим.
     */
    private suspend fun <T> withAccess(block: suspend (String) -> AccountResult<T>): AccountResult<T> {
        val first = when (val token = freshAccessToken()) {
            is AccountResult.Failure -> return token
            is AccountResult.Success -> block(token.value)
        }
        if (first !is AccountResult.Failure || !first.error.isAccessRejected) return first
        lock.withLock { accessToken = null }
        return when (val token = freshAccessToken()) {
            is AccountResult.Failure -> token
            is AccountResult.Success -> block(token.value)
        }
    }

    /** Запрос и запись нового токена — одна неотменяемая единица, см. описание класса. */
    private suspend fun freshAccessToken(): AccountResult<String> {
        loaded?.join()
        return withContext(NonCancellable + Dispatchers.IO) {
            val (result, tokens) = lock.withLock { refreshLocked() }
            // Имя с сервера — не под замком сессии: запись строки профиля ждёт свой замок.
            if (tokens != null) background.launch { syncProfile(tokens) }
            result
        }
    }

    /** Под [lock]. Вторым — токены, если refresh их выдал: по ним сверяется профиль. */
    private suspend fun refreshLocked(): Pair<AccountResult<String>, AccountTokens?> {
        val cached = accessToken
        if (cached != null && System.currentTimeMillis() < accessExpiresAt) {
            return AccountResult.Success(cached) to null
        }
        val current = session
            ?: return AccountResult.Failure(
                AccountError(401, AccountError.UNAUTHORIZED, "Вы не вошли в аккаунт.")
            ) to null
        return when (val refreshed = api.refresh(current.refreshToken)) {
            is AccountResult.Success -> {
                remember(refreshed.value, viaVk = current.viaVk)
                AccountResult.Success(refreshed.value.accessToken) to refreshed.value
            }
            is AccountResult.Failure -> {
                if (refreshed.error.isSessionDead) {
                    clearLocal()
                    AccountResult.Failure(
                        refreshed.error.copy(message = "Сессия закончилась — войдите в аккаунт снова.")
                    ) to null
                } else {
                    refreshed to null
                }
            }
        }
    }

    /**
     * Запрос, выдающий токены, их запись и копия имени в профиль — неотменяемо, см.
     * описание класса. Профиль сверяется до возврата, а не в фоне: экран, узнав о входе,
     * смотрит, есть ли имя, и без этого предложил бы завести его человеку, чьё имя уже
     * лежит на сервере.
     */
    private suspend fun signIn(
        email: String?,
        viaVk: Boolean,
        request: suspend () -> AccountResult<AccountTokens>,
    ): AccountResult<Unit> {
        loaded?.join()
        return withContext(NonCancellable + Dispatchers.IO) {
            val tokens = lock.withLock {
                when (val result = request()) {
                    is AccountResult.Failure -> return@withContext result
                    is AccountResult.Success -> {
                        remember(result.value, viaVk, email?.let(::normalize))
                        result.value
                    }
                }
            }
            syncProfile(tokens)
            background.launch { refreshSubscription() }
            AccountResult.Success(Unit)
        }
    }

    /**
     * Копирует в строку профиля то, что о человеке знает сервер: имя (серверное побеждает)
     * и id VK. Имени на сервере нет, а на телефоне оно есть — это имя из прежних версий,
     * где профиль жил без аккаунта; оно уходит на сервер, чтобы не пропасть. Фото VK
     * скачивается следом в фоне и ложится, только если своё за это время не выбрали.
     */
    private suspend fun syncProfile(tokens: AccountTokens) {
        val remote = tokens.user
        // Строка принадлежит другому аккаунту — стёрлась не до конца (процесс убит между
        // выходом и стиранием, Keystore потерял ключ сессии): ничего из неё не переносится.
        // Хозяин неизвестен (`null`) — это строка прежней версии, её имя подхватывается.
        val owner = store.profileOwner()
        val foreign = owner != null && owner != remote.id
        var adoptedName = ""
        val (_, after) = editProfileOf(remote.id) { current ->
            val base = if (foreign) User() else current
            if (owner == null) adoptedName = base.name.trim()
            base.copy(
                name = remote.name.take(NAME_MAX_LENGTH).ifBlank { base.name },
                vkUserId = remote.vkUserId,
            )
        } ?: return
        store.setProfileOwner(remote.id)
        if (remote.name.isBlank() && adoptedName.isNotBlank()) {
            api.updateName(tokens.accessToken, adoptedName)
        }
        val url = remote.avatarUrl
        if (after.avatar == null && url != null) {
            background.launch {
                val photo = AvatarImage.fromUrl(url) ?: return@launch
                editProfileOf(remote.id) { if (it.avatar == null) it.copy(avatar = photo) else null }
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
    private fun remember(tokens: AccountTokens, viaVk: Boolean, fallbackEmail: String? = null) {
        // Почта из ответа — подтверждённая почта входа. У аккаунта из VK её может не быть
        // вовсе, и тогда на экране остаётся та, что уже была, или ничего.
        val email = tokens.user.email.ifBlank { fallbackEmail ?: session?.email.orEmpty() }
        val next = AccountSession(tokens.user.id, email, tokens.refreshToken, viaVk)
        session = next
        if (!store.save(next)) Log.w(TAG, "Сессия аккаунта не записана на диск")
        accessToken = tokens.accessToken
        // Минута запаса: токен, истекающий в дороге до сервера, всё равно получит 401.
        accessExpiresAt = System.currentTimeMillis() +
            (tokens.expiresInSeconds - 60).coerceAtLeast(0) * 1000
        publish()
    }

    /**
     * Под [lock]. Сессия уходит, а с ней — профиль: он есть только у вошедшего, и
     * следующий, кто войдёт на этом телефоне, не должен увидеть чужие имя и фото. Строка
     * стирается и VK забывает токен — в фоне, потому что под [lock] ждать базу и сеть
     * незачем.
     *
     * Отметка хозяина ([AccountStore.profileOwner]) снимается только **после** стирания:
     * умри процесс раньше, строка осталась бы с чужим профилем, но и с отметкой, по
     * которой следующий вход другим аккаунтом её не примет. Запись в базу может и не
     * пройти — база как раз подменяется импортом, — и падать из-за этого процессу незачем:
     * отметка тогда остаётся, и дело доделает следующий вход.
     */
    private fun clearLocal(eraseProfile: Boolean = true) {
        session = null
        _subscription.value = null
        forgetAdFree()
        store.clear()
        accessToken = null
        accessExpiresAt = 0
        publish()
        background.launch {
            if (eraseProfile) {
                runCatching { profileLock.withLock { items.saveUser(User()) } }
                    .onSuccess { store.setProfileOwner(null) }
                    .onFailure { Log.w(TAG, "Профиль не стёрт при выходе", it) }
            }
            VkId.logout()
        }
    }

    private fun publish() {
        _state.value = session?.let { AccountState.SignedIn(it.email, it.userId, it.viaVk) }
            ?: AccountState.SignedOut
    }

    companion object {
        private const val TAG = "Account"
        private const val LOGOUT_TIMEOUT_MS = 15_000L

        /** Потолок имени в профиле; на сервере — 100, в шапке экрана больше не помещается. */
        const val NAME_MAX_LENGTH = 40

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
