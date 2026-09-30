package ru.zaroslikov.incubator.ui.profile

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountState
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.profile.AvatarImage
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel
import ru.zaroslikov.incubator.vkid.VkAccount
import ru.zaroslikov.incubator.vkid.VkId
import ru.zaroslikov.incubator.vkid.VkLoginResult

sealed interface ProfileIntent {
    /**
     * Сохранить поля профиля. Для гостя это и есть ручная регистрация — отдельного
     * интента у неё нет, различие «создан / изменён» ViewModel видит сама.
     */
    data class Save(val name: String, val farm: String, val city: String) : ProfileIntent

    /** Войти через VK ID — гостю это регистрация, профилю без VK — привязка. */
    data object LoginVk : ProfileIntent

    /** Выйти из VK ID: профиль остаётся, связь с VK — нет. */
    data object UnlinkVk : ProfileIntent

    data class SetAvatar(val uri: Uri) : ProfileIntent
    data object RemoveAvatar : ProfileIntent

    /** Стереть профиль целиком. Хозяйство — инкубаторы, закладки — не трогается. */
    data object Delete : ProfileIntent

    data object DismissNotice : ProfileIntent
}

sealed interface ProfileEffect {
    /** Поля сохранены — окно правки можно закрыть. */
    data object Saved : ProfileEffect
}

/**
 * «Профиль»: необязательная регистрация — вход через VK ID или ручное заполнение.
 *
 * Состояние — профиль из базы плюс два локальных флага (идёт ли вход, что сказать
 * человеку), поэтому `combine` со `stateIn`, как у экранов-списков: своих полей ввода
 * экран не держит — их держит окно правки, пока оно открыто.
 *
 * Три правила держат запись.
 *
 * **Каждое изменение — чтение, правка и запись строки под одним [writeLock].** Строка
 * одна на все поля, и без замка две записи, прочитавшие её одновременно, затёрли бы друг
 * друга: город, сохранённый, пока качается фото VK, пропадал бы при записи привязки.
 *
 * **Сначала база, потом VK.** Выход из VK — сетевой запрос, который SDK делает под своим
 * замком и который без сети висит до таймаута. «Удалить профиль» и «Выйти из VK» ждали
 * его раньше записи, и человек, ушедший с экрана, не дождавшись, оставлял профиль
 * неудалённым. Теперь строка пишется сразу, а выход из VK идёт следом в [background] —
 * области, которая переживает экран, — и его неудача ничего не отменяет.
 *
 * **Запись после ответа VK не отменяется уходом с экрана** (`NonCancellable`): человек
 * уже вошёл в окне VK, и токен уже у SDK — профиль, не узнавший об этом, разошёлся бы
 * с ним.
 */
class ProfileViewModel(
    private val application: Application,
    private val itemsRepository: ItemsRepository,
    private val account: AccountRepository,
) : MviViewModel<ProfileUiState, ProfileIntent, ProfileEffect>() {

    private data class Local(val busy: Boolean = false, val notice: String? = null)

    private val local = MutableStateFlow(Local())

    private val writeLock = Mutex()

    /**
     * Область для выхода из VK: не `viewModelScope`, потому что выход нужно довести до
     * конца и после закрытия экрана, а ждать его экрану незачем.
     */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val state: StateFlow<ProfileUiState> =
        combine(itemsRepository.getUser(), local, account.state) { user, flags, accountState ->
            ProfileUiState(
                user = user,
                account = accountState,
                vkAvailable = VkId.isAvailable,
                busy = flags.busy,
                notice = flags.notice,
                loading = false,
            )
        }.stateIn(viewModelScope, MviSharing.WhileVisible, ProfileUiState())

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Save -> save(intent.name, intent.farm, intent.city)
            ProfileIntent.LoginVk -> loginVk()
            ProfileIntent.UnlinkVk -> unlinkVk()
            is ProfileIntent.SetAvatar -> setAvatar(intent.uri)
            ProfileIntent.RemoveAvatar -> launchEdit { if (it.hasProfile) it.copy(avatar = null) else null }
            ProfileIntent.Delete -> delete()
            ProfileIntent.DismissNotice -> local.update { it.copy(notice = null) }
        }
    }

    /**
     * Читает строку, отдаёт её [transform] и пишет ответ — под [writeLock]. `null` из
     * [transform] значит «писать нечего». Возвращает прежнюю и новую строку.
     */
    private suspend fun edit(transform: (User) -> User?): Pair<User, User>? =
        writeLock.withLock {
            val current = itemsRepository.getUser().first()
            val updated = transform(current) ?: return@withLock null
            if (updated != current) itemsRepository.saveUser(updated)
            current to updated
        }

    private fun launchEdit(transform: (User) -> User?) {
        viewModelScope.launch { edit(transform) }
    }

    /**
     * Сохраняет поля. Имя обязательно всегда — и при ручной регистрации, и в профиле с VK:
     * профиль без имени, отвязанный от VK, превратился бы в гостя, у которого в базе
     * остались хозяйство, город и фото, — невидимые и неудаляемые. Окно правки такую
     * кнопку не даёт нажать, ViewModel повторяет правило, потому что композиция может
     * пережить состояние, из которого была построена.
     *
     * Двойное нажатие «Создать» безвредно: вторая запись ждёт первую на замке, видит уже
     * заведённый профиль и отчитывается «изменён», а не «создан» во второй раз.
     */
    private fun save(name: String, farm: String, city: String) {
        viewModelScope.launch {
            val (before, after) = edit { current ->
                current.copy(
                    name = name.trim().take(NAME_MAX_LENGTH),
                    farm = farm.trim().take(FARM_MAX_LENGTH),
                    city = city.trim().take(CITY_MAX_LENGTH),
                ).takeIf { it.hasName }
            } ?: return@launch
            if (before.hasProfile) {
                Analytics.report(Events.PROFILE_UPDATED, mapOf("Фото" to (after.avatar != null)))
            } else {
                Analytics.report(Events.PROFILE_CREATED, mapOf("Способ" to "вручную"))
            }
            sendEffect(ProfileEffect.Saved)
        }
    }

    /**
     * Вход через VK ID.
     *
     * Имя и фото VK подставляются **только в пустые поля**: имя, которое человек набрал
     * сам, — его выбор, и привязка VK не повод его переписывать. У гостя пусто всё, так
     * что регистрация через VK заполняет профиль целиком.
     *
     * Привязка пишется сразу, фото — отдельной правкой после скачивания: оно может идти
     * секунды, и держать всё это время замок (или устаревшую копию строки) значило бы
     * либо блокировать правку профиля, либо затереть её. Фото ложится, только если его
     * за это время не выбрали вручную. Скачивается один раз и хранится байтами: адрес
     * фото VK со временем протухает, а офлайн-приложение должно показывать аватар и без
     * сети.
     */
    private fun loginVk() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                when (val result = VkId.login()) {
                    is VkLoginResult.Success -> withContext(NonCancellable) {
                        linkAccount(result.account)
                    }

                    VkLoginResult.Cancelled ->
                        Analytics.report(Events.VK_LOGIN, mapOf("Исход" to "отмена"))

                    is VkLoginResult.Failed -> {
                        Analytics.report(Events.VK_LOGIN, mapOf("Исход" to "ошибка"))
                        local.update {
                            it.copy(
                                notice = "Не удалось войти через VK. Проверьте интернет " +
                                    "и попробуйте ещё раз."
                            )
                        }
                    }
                }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun linkAccount(vk: VkAccount) {
        val (before, after) = edit { current ->
            current.copy(
                name = current.name.ifBlank {
                    vk.fullName.ifBlank { VK_FALLBACK_NAME }.take(NAME_MAX_LENGTH)
                },
                vkUserId = vk.userId,
            )
        } ?: return
        Analytics.report(Events.VK_LOGIN, mapOf("Исход" to "вход"))
        if (!before.hasProfile) {
            Analytics.report(Events.PROFILE_CREATED, mapOf("Способ" to "VK"))
        }
        if (after.avatar == null) {
            val photo = vk.photoUrl?.let { AvatarImage.fromUrl(it) } ?: return
            // Только если фото всё ещё нет и профиль тот же: за время скачивания его могли
            // выбрать вручную, отвязать VK или удалить профиль целиком.
            edit { current ->
                if (current.avatar == null && current.vkUserId == vk.userId) {
                    current.copy(avatar = photo)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Отвязывает VK. Имя и фото, пришедшие из VK, остаются: они уже часть профиля, и
     * человек, нажавший «Выйти из VK», не просил остаться безымянным. Кто хочет стереть и
     * их — «Удалить профиль». Имя у профиля есть всегда (см. [save]), так что без VK он
     * остаётся профилем, а не прячет поля за карточкой гостя.
     */
    private fun unlinkVk() {
        viewModelScope.launch {
            edit { current ->
                if (!current.isVkLinked) {
                    null
                } else {
                    current.copy(
                        vkUserId = null,
                        name = current.name.ifBlank { VK_FALLBACK_NAME },
                    )
                }
            } ?: return@launch
            Analytics.report(Events.VK_UNLINKED)
            logoutInBackground()
        }
    }

    private fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val avatar = AvatarImage.fromUri(application, uri)
            if (avatar == null) {
                local.update { it.copy(notice = "Не удалось открыть это фото.") }
                return@launch
            }
            edit { if (it.hasProfile) it.copy(avatar = avatar) else null }
        }
    }

    /**
     * Удаляет профиль: пустая строка вместо прежней, затем выход из VK, если он был.
     *
     * Строка переписывается пустой, а не удаляется: для репозитория это одно и то же
     * (`getUser()` отдаёт `User()` в обоих случаях), а у DAO нет и не нужно второго пути.
     */
    private fun delete() {
        viewModelScope.launch {
            val (before, _) = edit { User() } ?: return@launch
            Analytics.report(Events.PROFILE_DELETED)
            if (before.isVkLinked) logoutInBackground()
        }
    }

    private fun logoutInBackground() {
        background.launch { VkId.logout() }
    }

    companion object {
        /**
         * Имя длиннее прежних 20 символов: у VK оно приходит «имя фамилия», и
         * «Константин Константинопольский» в двадцать не влезает. В шапке оно
         * переносится на вторую строку, а не вытесняет остальное.
         */
        const val NAME_MAX_LENGTH = 40
        const val FARM_MAX_LENGTH = 40
        const val CITY_MAX_LENGTH = 30

        /** Имя на случай, когда VK не отдал ни имени, ни фамилии. */
        const val VK_FALLBACK_NAME = "Пользователь VK"
    }
}

@Immutable
data class ProfileUiState(
    val user: User = User(),
    /** Аккаунт по почте — независим от профиля: можно войти гостем, можно завести профиль без аккаунта. */
    val account: AccountState = AccountState.Unavailable,
    /** Настроен ли вход через VK ID в этой сборке — см. `VkId.isAvailable`. */
    val vkAvailable: Boolean = false,
    /** Идёт вход через VK — кнопки VK погашены. */
    val busy: Boolean = false,
    /** Сообщение для человека — ошибка входа, неоткрывшееся фото. */
    val notice: String? = null,
    /** База ещё не ответила: гость и «ещё неизвестно» — разные экраны. */
    val loading: Boolean = true,
)
