package ru.zaroslikov.incubator.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountState
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.incubator.CardHeader
import ru.zaroslikov.incubator.ui.incubator.TabCard
import ru.zaroslikov.incubator.ui.menu.MenuRow

/**
 * Карточка «Аккаунт» на экране профиля.
 *
 * Аккаунт и профиль — разные вещи, и карточка это держит: профиль — имя и фото на этом
 * телефоне, аккаунт — почта и пароль на сервере. Гость может войти в аккаунт, не заводя
 * профиля, и завести профиль, не входя в аккаунт. В сборке без адреса сервера
 * ([AccountState.Unavailable]) карточки нет вовсе.
 */
@Composable
internal fun AccountCard(
    state: AccountState,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit,
) {
    when (state) {
        AccountState.Unavailable, AccountState.Checking -> Unit
        AccountState.SignedOut -> TabCard {
            CardHeader(
                title = "Аккаунт",
                subtitle = "Почта и пароль — необязательно",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Аккаунт понадобится, чтобы в будущем переносить хозяйство между " +
                    "телефонами без файлов. Сейчас он хранит только почту. Аккаунт общий с " +
                    "приложением «Моё хозяйство»: зарегистрировались в одном — входите в оба.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = accentButtonColors(),
            ) {
                Text("Войти по почте", style = DesignType.ButtonLabel)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.IncomeSurface,
                    contentColor = DesignPalette.Accent,
                ),
            ) {
                Text("Создать аккаунт", style = DesignType.ButtonLabel)
            }
        }
        is AccountState.SignedIn -> TabCard {
            CardHeader(title = "Аккаунт", subtitle = "Вы вошли по почте")
            Spacer(Modifier.height(16.dp))
            MenuRow(title = state.email, description = "Почта аккаунта")
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.IncomeSurface,
                    contentColor = DesignPalette.Accent,
                ),
            ) {
                Text("Выйти из аккаунта", style = DesignType.ButtonLabel)
            }
            // Удаление — текстовой кнопкой, а не второй красной плашкой: красная уже стоит
            // ниже, у профиля, и две подряд читались бы как одно и то же действие.
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Удалить аккаунт", style = DesignType.ButtonLabel, color = DesignPalette.Expense)
            }
        }
    }
}

/**
 * Форма аккаунта — вход, регистрация, код из письма, сброс пароля — одной шторкой.
 *
 * Шаги переключаются внутри, а не открывают новые шторки: почта, набранная на первом,
 * нужна на всех следующих, и человек, перешедший ко «Забыли пароль?», не должен набирать
 * её снова. Закрытие шторки ничего не отменяет на сервере — код, отправленный на почту,
 * просто истечёт.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .clearFocusOnTap()
                .padding(horizontal = SheetPadding)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            SheetHeader(title = stepTitle(state.step), onClose = onDismiss)
            Spacer(Modifier.height(16.dp))

            state.info?.let {
                Text(it, style = DesignType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }

            val needsEmailField = state.step == AccountStep.Login ||
                state.step == AccountStep.Register ||
                state.step == AccountStep.Forgot
            if (needsEmailField) {
                FieldLabel("Почта", required = true)
                SheetTextField(
                    value = state.email,
                    onValueChange = { send(AccountIntent.EmailChanged(it)) },
                    placeholder = "name@mail.ru",
                    keyboardType = KeyboardType.Email,
                    imeAction = if (state.step == AccountStep.Forgot) ImeAction.Done else ImeAction.Next,
                )
                if (state.email.isNotEmpty() && !state.emailValid) {
                    FieldError("Похоже, в адресе опечатка")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.step == AccountStep.Verify || state.step == AccountStep.Reset) {
                FieldLabel("Код из письма", required = true)
                SheetTextField(
                    value = state.code,
                    onValueChange = { send(AccountIntent.CodeChanged(it)) },
                    placeholder = "6 цифр",
                    numeric = true,
                    imeAction = if (state.step == AccountStep.Verify) ImeAction.Done else ImeAction.Next,
                )
                TextButton(onClick = { send(AccountIntent.ResendCode) }, enabled = !state.busy) {
                    Text(
                        "Отправить код ещё раз",
                        style = DesignType.ButtonLabel,
                        color = DesignPalette.Accent,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (state.step == AccountStep.Login ||
                state.step == AccountStep.Register ||
                state.step == AccountStep.Reset
            ) {
                FieldLabel(
                    if (state.step == AccountStep.Reset) "Новый пароль" else "Пароль",
                    required = true,
                )
                PasswordField(
                    value = state.password,
                    onValueChange = { send(AccountIntent.PasswordChanged(it)) },
                )
                // Правило пароля — заранее, а не отказом: на входе его не показывают —
                // там пароль уже есть, и подсказка о длине только путала бы.
                if (state.step != AccountStep.Login) {
                    Text(
                        text = "От ${AccountRepository.PASSWORD_MIN} символов, хотя бы одна буква и одна цифра",
                        style = DesignType.Micro,
                        color = if (state.password.isNotEmpty() && !state.passwordValid) {
                            DesignPalette.Expense
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            state.error?.let {
                Text(it, style = DesignType.Body, color = DesignPalette.Expense)
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { send(AccountIntent.Submit) },
                enabled = state.canSubmit && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = accentButtonColors(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        color = DesignPalette.OnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(submitLabel(state.step), style = DesignType.ButtonLabel)
                }
            }

            Spacer(Modifier.height(8.dp))
            StepLinks(step = state.step, enabled = !state.busy, onGoTo = { send(AccountIntent.GoTo(it)) })
        }
    }
}

/** Ссылки между шагами — под кнопкой, как их привыкли видеть в любом входе. */
@Composable
private fun StepLinks(step: AccountStep, enabled: Boolean, onGoTo: (AccountStep) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (step) {
            AccountStep.Login -> {
                LinkButton("Нет аккаунта? Создать", enabled) { onGoTo(AccountStep.Register) }
                LinkButton("Забыли пароль?", enabled) { onGoTo(AccountStep.Forgot) }
            }
            AccountStep.Register -> LinkButton("Уже есть аккаунт? Войти", enabled) { onGoTo(AccountStep.Login) }
            AccountStep.Verify, AccountStep.Forgot, AccountStep.Reset ->
                LinkButton("Назад ко входу", enabled) { onGoTo(AccountStep.Login) }
        }
    }
}

@Composable
private fun LinkButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, style = DesignType.ButtonLabel, color = DesignPalette.Accent)
    }
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = DesignType.Micro,
        color = DesignPalette.Expense,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Пароль — с кнопкой «показать»: на телефоне опечатку в скрытом поле не увидеть. */
@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "••••••••",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(
                    if (visible) "Скрыть" else "Показать",
                    style = DesignType.Micro,
                    color = DesignPalette.Accent,
                )
            }
        },
    )
}

private fun stepTitle(step: AccountStep) = when (step) {
    AccountStep.Login -> "Вход по почте"
    AccountStep.Register -> "Новый аккаунт"
    AccountStep.Verify -> "Подтверждение почты"
    AccountStep.Forgot -> "Сброс пароля"
    AccountStep.Reset -> "Новый пароль"
}

private fun submitLabel(step: AccountStep) = when (step) {
    AccountStep.Login -> "Войти"
    AccountStep.Register -> "Создать аккаунт"
    AccountStep.Verify -> "Подтвердить"
    AccountStep.Forgot -> "Прислать код"
    AccountStep.Reset -> "Сменить пароль и войти"
}

/**
 * Удаление аккаунта — с паролем. Без него удалить аккаунт мог бы любой, кому достался
 * разблокированный телефон, а вернуть удалённое нечем.
 */
@Composable
internal fun DeleteAccountDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text("Удалить аккаунт?", style = DesignType.SheetTitle, color = DesignPalette.Expense)
        },
        text = {
            Column {
                Text(
                    text = "Аккаунт и почта будут удалены с сервера насовсем — и для " +
                        "«Моего хозяйства» тоже: аккаунт у приложений общий. Профиль, " +
                        "инкубаторы и закладки на телефоне останутся.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FieldLabel("Пароль", required = true)
                PasswordField(value = password, onValueChange = { password = it })
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = DesignType.Micro, color = DesignPalette.Expense)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty() && !busy) {
                Text(
                    if (busy) "Удаляем…" else "Удалить",
                    style = DesignType.ButtonLabel,
                    color = if (password.isNotEmpty() && !busy) DesignPalette.Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
            }
        },
    )
}

