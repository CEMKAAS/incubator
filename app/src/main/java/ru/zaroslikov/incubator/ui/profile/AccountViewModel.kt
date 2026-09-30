package ru.zaroslikov.incubator.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.account.AccountError
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountResult
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.vkid.VkId
import ru.zaroslikov.incubator.vkid.VkLoginResult

/** Шаг формы аккаунта. Коды — два разных шага: после них происходит разное. */
enum class AccountStep {
    Login,
    Register,

    /** Код из письма после регистрации. */
    Verify,

    /** «Забыли пароль» — почта, на которую выслать код. */
    Forgot,

    /** Код из письма и новый пароль. */
    Reset,
}

sealed interface AccountIntent {
    /** Открыть форму на шаге: «Войти» — [AccountStep.Login], «Создать аккаунт» — [AccountStep.Register]. */
    data class Open(val step: AccountStep) : AccountIntent
    data class GoTo(val step: AccountStep) : AccountIntent
    data class EmailChanged(val value: String) : AccountIntent
    data class PasswordChanged(val value: String) : AccountIntent {
        override fun toString() = "PasswordChanged(***)"
    }
    /** Повтор пароля при регистрации — опечатку в скрытом поле иначе не увидеть. */
    data class PasswordRepeatChanged(val value: String) : AccountIntent {
        override fun toString() = "PasswordRepeatChanged(***)"
    }
    data class CodeChanged(val value: String) : AccountIntent
    data object Submit : AccountIntent
    data object ResendCode : AccountIntent
    /** Открывается диалог удаления — старая ошибка из прошлого раза в нём не нужна. */
    data object StartDelete : AccountIntent
    /** Удалить аккаунт, вошедший через VK, — подтверждение новым входом в VK. */
    data object DeleteWithVk : AccountIntent
    data class DeleteAccount(val password: String) : AccountIntent {
        override fun toString() = "DeleteAccount(***)"
    }
}

sealed interface AccountEffect {
    /**
     * Вошли. [needsName] — имени нет ни на сервере, ни на телефоне: экран сразу предлагает
     * заполнить профиль: «вы вошли» без имени читается как половина дела.
     */
    data class SignedIn(val needsName: Boolean) : AccountEffect
    data object Deleted : AccountEffect
}

/**
 * Форма аккаунта по почте: вход, регистрация, код, сброс пароля — и удаление аккаунта с
 * карточки профиля.
 *
 * Пароль держится только здесь, в памяти ViewModel, и не попадает ни в `rememberSaveable`,
 * ни в `Bundle`: сохранённое состояние активности пишется на диск системой, а пароль там
 * не нужен никому. Поворот экрана его переживает (ViewModel живёт), смерть процесса — нет,
 * и это правильно.
 *
 * Проверки почты и пароля — те же, что у сервера ([AccountRepository.isValidEmail] и
 * соседи), и сделаны до запроса: отказ, известный заранее, не стоит поездки на сервер,
 * а ошибка сразу под полем понятнее ответа через секунду.
 */
class AccountViewModel(
    private val account: AccountRepository,
) : StatefulMviViewModel<AccountFormState, AccountIntent, AccountEffect>(AccountFormState()) {

    override fun onIntent(intent: AccountIntent) {
        when (intent) {
            // busy переносится: запрос, начатый до закрытия шторки, ещё идёт, и повторное
            // открытие не должно разрешать второй такой же параллельно.
            is AccountIntent.Open -> reduce {
                AccountFormState(step = intent.step, email = email, busy = busy)
            }
            AccountIntent.StartDelete -> reduce { copy(deleteError = null) }
            is AccountIntent.GoTo -> if (!current.busy) reduce {
                copy(
                    step = intent.step,
                    password = "",
                    passwordRepeat = "",
                    code = "",
                    error = null,
                    info = null,
                    // Пароль для кода нужен только на шаге кода; уход с него его забывает.
                    pendingPassword = if (intent.step == AccountStep.Verify) pendingPassword else "",
                )
            }
            is AccountIntent.EmailChanged -> reduce { copy(email = intent.value.trim(), error = null) }
            is AccountIntent.PasswordChanged -> reduce {
                copy(password = intent.value.takeCodePoints(AccountRepository.PASSWORD_MAX), error = null)
            }
            is AccountIntent.PasswordRepeatChanged -> reduce {
                copy(passwordRepeat = intent.value.takeCodePoints(AccountRepository.PASSWORD_MAX), error = null)
            }
            is AccountIntent.CodeChanged -> reduce {
                copy(code = intent.value.filter(Char::isDigit).take(CODE_LENGTH), error = null)
            }
            AccountIntent.Submit -> submit()
            AccountIntent.ResendCode -> resend()
            AccountIntent.DeleteWithVk -> deleteWithVk()
            is AccountIntent.DeleteAccount -> delete(intent.password)
        }
    }

    private fun submit() {
        val form = current
        if (form.busy || !form.canSubmit) return
        reduce { copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            when (form.step) {
                AccountStep.Login -> handleLogin(account.login(form.email, form.password))
                AccountStep.Register -> handle(account.register(form.email, form.password)) {
                    Analytics.report(Events.ACCOUNT_REGISTERED)
                    reduce {
                        copy(
                            step = AccountStep.Verify,
                            password = "",
                            passwordRepeat = "",
                            pendingPassword = form.password,
                            info = "Мы отправили код на ${form.email}. Письмо может прийти " +
                                "через минуту — загляните и в «Спам». Если вы уже " +
                                "регистрировались с этой почтой, кода не будет — войдите или " +
                                "сбросьте пароль.",
                        )
                    }
                }
                AccountStep.Verify -> handle(
                    account.confirmRegistration(form.email, form.code, form.pendingPassword)
                ) {
                    Analytics.report(Events.ACCOUNT_VERIFIED)
                    reduce { AccountFormState() }
                    sendEffect(AccountEffect.SignedIn(needsName = !account.profile().hasName))
                }
                AccountStep.Forgot -> handle(account.forgotPassword(form.email)) {
                    reduce {
                        copy(
                            step = AccountStep.Reset,
                            info = "Если аккаунт с почтой ${form.email} есть, на неё ушёл " +
                                "код для сброса пароля.",
                        )
                    }
                }
                AccountStep.Reset -> handle(account.resetPassword(form.email, form.code, form.password)) {
                    Analytics.report(Events.ACCOUNT_PASSWORD_RESET)
                    reduce { AccountFormState() }
                    sendEffect(AccountEffect.SignedIn(needsName = !account.profile().hasName))
                }
            }
        }
    }

    private suspend fun handleLogin(result: AccountResult<Unit>) {
        when (result) {
            is AccountResult.Success -> {
                Analytics.report(Events.ACCOUNT_LOGIN, mapOf("Исход" to "вход"))
                reduce { AccountFormState() }
                sendEffect(AccountEffect.SignedIn(needsName = !account.profile().hasName))
            }
            is AccountResult.Failure -> {
                Analytics.report(Events.ACCOUNT_LOGIN, mapOf("Исход" to "ошибка"))
                reduce { copy(busy = false, error = result.error.message) }
            }
        }
    }

    private inline fun handle(result: AccountResult<Unit>, onSuccess: () -> Unit) {
        when (result) {
            is AccountResult.Success -> {
                reduce { copy(busy = false) }
                onSuccess()
            }
            is AccountResult.Failure -> reduce { copy(busy = false, error = result.error.message) }
        }
    }

    /**
     * Повторная отправка кода. Ответ сервера одинаков, был ли код отправлен (он не
     * говорит, есть ли такая почта), поэтому и текст — «если», а не «отправили».
     */
    private fun resend() {
        val form = current
        if (form.busy || !AccountRepository.isValidEmail(form.email)) return
        if (form.step == AccountStep.Verify && form.pendingPassword.isEmpty()) return
        reduce { copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            // У сервера нет отдельной «повторной отправки»: код регистрации запрашивается
            // той же регистрацией, с тем же паролем, — и прежний код при этом не гаснет.
            val result = when (form.step) {
                AccountStep.Reset -> account.forgotPassword(form.email)
                else -> account.register(form.email, form.pendingPassword)
            }
            handle(result) {
                reduce { copy(info = "Если код можно отправить, он придёт в течение минуты.") }
            }
        }
    }

    private fun delete(password: String) {
        runDelete { account.deleteAccount(password) }
    }

    /**
     * Удаление аккаунта, в который входили через VK: пароля у него может не быть, и личность
     * подтверждает новый вход в VK. Отказ в окне VK — не ошибка, диалог просто остаётся.
     */
    private fun deleteWithVk() {
        runDelete {
            when (val vk = VkId.login()) {
                is VkLoginResult.Success -> account.deleteAccountWithVk(vk.account.accessToken)
                VkLoginResult.Cancelled -> null
                is VkLoginResult.Failed -> AccountResult.Failure(
                    AccountError(0, AccountError.NETWORK, "Не удалось войти через VK. Попробуйте ещё раз.")
                )
            }
        }
    }

    private fun runDelete(request: suspend () -> AccountResult<Unit>?) {
        if (current.busy) return
        reduce { copy(busy = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = request()) {
                null -> reduce { copy(busy = false) }
                is AccountResult.Success -> {
                    Analytics.report(Events.ACCOUNT_DELETED)
                    reduce { copy(busy = false) }
                    sendEffect(AccountEffect.Deleted)
                }
                is AccountResult.Failure -> reduce {
                    copy(busy = false, deleteError = result.error.message)
                }
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}

@Immutable
data class AccountFormState(
    val step: AccountStep = AccountStep.Login,
    val email: String = "",
    /** Пароль — на входе и регистрации, новый пароль — на [AccountStep.Reset]. */
    val password: String = "",
    /** Повтор пароля — только на [AccountStep.Register]. */
    val passwordRepeat: String = "",
    val code: String = "",
    val busy: Boolean = false,
    /** Отказ сервера или сети — красным над кнопкой. */
    val error: String? = null,
    /** Что произошло — «код отправлен», — обычным текстом. */
    val info: String? = null,
    /** Ошибка удаления аккаунта — показывается в его диалоге, а не в форме. */
    val deleteError: String? = null,
    /**
     * Пароль, под которым регистрировались, — для шага кода. Сервер подтверждает код, только
     * если пароль совпадает с привязанным к нему: так код из письма, вызванного чужой
     * повторной регистрацией той же почты, не подтвердит чужой пароль. Только в памяти.
     */
    val pendingPassword: String = "",
) {
    // Сгенерированный toString напечатал бы пароль в любой лог или отчёт о сбое, куда
    // попадёт состояние.
    override fun toString() =
        "AccountFormState(step=$step, busy=$busy, error=$error, info=$info, deleteError=$deleteError)"

    val emailValid: Boolean get() = AccountRepository.isValidEmail(email)
    val passwordValid: Boolean get() = AccountRepository.isValidPassword(password, email)
    val passwordsMatch: Boolean get() = password == passwordRepeat
    val codeComplete: Boolean get() = code.length == AccountViewModel.CODE_LENGTH

    val canSubmit: Boolean
        get() = when (step) {
            AccountStep.Login -> emailValid && password.isNotEmpty()
            AccountStep.Register -> emailValid && passwordValid && passwordsMatch
            AccountStep.Verify -> emailValid && codeComplete && pendingPassword.isNotEmpty()
            AccountStep.Forgot -> emailValid
            AccountStep.Reset -> emailValid && codeComplete && passwordValid
        }
}

/** Обрезка по кодовым точкам, как считает длину сервер: эмодзи — один символ, а не два. */
private fun String.takeCodePoints(max: Int): String =
    if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max))
