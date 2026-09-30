package ru.zaroslikov.incubator.ui.profile

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountResult
import ru.zaroslikov.incubator.account.AccountState
import ru.zaroslikov.incubator.account.AccountSubscription
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.profile.AvatarImage
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel
import ru.zaroslikov.incubator.vkid.VkId
import ru.zaroslikov.incubator.vkid.VkLoginResult

sealed interface ProfileIntent {
    /** Сохранить поля профиля. Имя уходит на сервер, хозяйство и город остаются на телефоне. */
    data class Save(val name: String, val farm: String, val city: String) : ProfileIntent

    /** Войти в аккаунт через VK ID — первый вход заводит аккаунт. */
    data object LoginVk : ProfileIntent

    data class SetAvatar(val uri: Uri) : ProfileIntent
    data object RemoveAvatar : ProfileIntent

    /** Выйти из аккаунта — профиль с телефона стирается. */
    data object Logout : ProfileIntent

    data object DismissNotice : ProfileIntent

    /** Экран открыт — спросить у сервера свежий статус подписки. */
    data object RefreshSubscription : ProfileIntent
}

sealed interface ProfileEffect {
    /** Поля сохранены — окно правки можно закрыть. */
    data object Saved : ProfileEffect

    /** Вошли через VK; [needsName] — имени нет ни на сервере, ни в VK, его стоит спросить. */
    data class SignedInWithVk(val needsName: Boolean) : ProfileEffect
}

/**
 * «Профиль» — это аккаунт: вошедший видит свои имя, фото, хозяйство и город, остальные —
 * приглашение войти.
 *
 * Состояние — строка профиля из базы, состояние аккаунта и два локальных флага (идёт ли
 * вход, что сказать человеку), поэтому `combine` со `stateIn`, как у экранов-списков.
 * Писать в строку профиля эта ViewModel сама не умеет — только через
 * [AccountRepository.editProfile], под одним замком со входом и выходом: иначе фото,
 * докачавшееся после входа, затирало бы город, сохранённый в ту же секунду.
 */
class ProfileViewModel(
    private val application: Application,
    private val itemsRepository: ItemsRepository,
    private val account: AccountRepository,
) : MviViewModel<ProfileUiState, ProfileIntent, ProfileEffect>() {

    private data class Local(val busy: Boolean = false, val notice: String? = null)

    private val local = MutableStateFlow(Local())

    override val state: StateFlow<ProfileUiState> =
        combine(
            itemsRepository.getUser(),
            local,
            account.state,
            account.subscription,
        ) { user, flags, accountState, subscription ->
            ProfileUiState(
                // Строка без сессии — остаток: прежняя версия, импортированная база. Профилем
                // она станет только после входа, а до того не показывается.
                user = if (accountState is AccountState.SignedIn) user else User(),
                account = accountState,
                subscription = subscription.takeIf { accountState is AccountState.SignedIn },
                vkAvailable = VkId.isAvailable,
                busy = flags.busy,
                notice = flags.notice,
                loading = accountState == AccountState.Checking,
            )
        }.stateIn(viewModelScope, MviSharing.WhileVisible, ProfileUiState())

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Save -> save(intent.name, intent.farm, intent.city)
            ProfileIntent.LoginVk -> loginVk()
            is ProfileIntent.SetAvatar -> setAvatar(intent.uri)
            ProfileIntent.RemoveAvatar -> viewModelScope.launch {
                account.editProfile { it.copy(avatar = null) }
            }
            ProfileIntent.Logout -> if (account.logout()) Analytics.report(Events.ACCOUNT_LOGOUT)
            ProfileIntent.DismissNotice -> local.update { it.copy(notice = null) }
            ProfileIntent.RefreshSubscription -> viewModelScope.launch { account.refreshSubscription() }
        }
    }

    /**
     * Сохраняет поля. Имя обязательно; если оно изменилось, сначала уходит на сервер — оно
     * общее с «Моим хозяйством», — и без ответа сервера не сохраняется ничего: окно правки
     * остаётся открытым с сообщением, а не закрывается, делая вид, что всё записано.
     */
    private fun save(name: String, farm: String, city: String) {
        if (local.value.busy) return
        val newName = name.trim().take(NAME_MAX_LENGTH)
        if (newName.isBlank()) return
        local.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                if (newName != state.value.user.name) {
                    val renamed = account.updateName(newName)
                    if (renamed is AccountResult.Failure) {
                        local.update { it.copy(notice = "Имя не сохранено: ${renamed.error.message}") }
                        return@launch
                    }
                }
                val (before, after) = withContext(NonCancellable) {
                    account.editProfile {
                        it.copy(
                            name = newName,
                            farm = farm.trim().take(FARM_MAX_LENGTH),
                            city = city.trim().take(CITY_MAX_LENGTH),
                        )
                    }
                } ?: return@launch
                if (before.hasName) {
                    Analytics.report(Events.PROFILE_UPDATED, mapOf("Фото" to (after.avatar != null)))
                } else {
                    Analytics.report(Events.PROFILE_CREATED, mapOf("Способ" to profileMethod()))
                }
                sendEffect(ProfileEffect.Saved)
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Вход в аккаунт через VK ID: окно VK, затем его токен — серверу, который выдаёт свои.
     * Запись после ответа VK не отменяется уходом с экрана (`NonCancellable`): человек уже
     * вошёл в окне VK, и брошенный на полпути вход оставил бы токен у SDK без аккаунта.
     */
    private fun loginVk() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                when (val vk = VkId.login()) {
                    is VkLoginResult.Success -> {
                        val result = withContext(NonCancellable) {
                            account.loginWithVk(vk.account.accessToken)
                        }
                        when (result) {
                            is AccountResult.Success -> {
                                Analytics.report(Events.VK_LOGIN, mapOf("Исход" to "вход"))
                                sendEffect(ProfileEffect.SignedInWithVk(needsName = !hasName()))
                            }
                            is AccountResult.Failure -> {
                                Analytics.report(Events.VK_LOGIN, mapOf("Исход" to "ошибка"))
                                local.update { it.copy(notice = result.error.message) }
                                // Сервер вход не принял — токен у SDK ни к чему.
                                VkId.logout()
                            }
                        }
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

    /** Имя из базы прямо сейчас: `state` после входа мог ещё не дождаться ответа Room. */
    private suspend fun hasName(): Boolean = itemsRepository.getUser().first().hasName

    private fun profileMethod(): String =
        if ((state.value.account as? AccountState.SignedIn)?.viaVk == true) "VK" else "почта"

    private fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val avatar = AvatarImage.fromUri(application, uri)
            if (avatar == null) {
                local.update { it.copy(notice = "Не удалось открыть это фото.") }
                return@launch
            }
            account.editProfile { it.copy(avatar = avatar) }
        }
    }

    companion object {
        /**
         * Имя длиннее прежних 20 символов: у VK оно приходит «имя фамилия», и
         * «Константин Константинопольский» в двадцать не влезает.
         */
        const val NAME_MAX_LENGTH = AccountRepository.NAME_MAX_LENGTH
        const val FARM_MAX_LENGTH = 40
        const val CITY_MAX_LENGTH = 30
    }
}

@Immutable
data class ProfileUiState(
    /** Профиль вошедшего; у невошедшего — пустой, чем бы ни была заполнена строка в базе. */
    val user: User = User(),
    val account: AccountState = AccountState.Unavailable,
    /** Подписка вошедшего с сервера; `null` — ещё не известна, и экран о ней молчит. */
    val subscription: AccountSubscription? = null,
    /** Настроен ли вход через VK ID в этой сборке — см. `VkId.isAvailable`. */
    val vkAvailable: Boolean = false,
    /** Идёт вход или сохранение — кнопки погашены. */
    val busy: Boolean = false,
    /** Сообщение для человека — ошибка входа, неоткрывшееся фото. */
    val notice: String? = null,
    /** Сессия ещё читается с диска. */
    val loading: Boolean = true,
)
