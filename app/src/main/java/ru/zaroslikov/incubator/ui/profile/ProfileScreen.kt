package ru.zaroslikov.incubator.ui.profile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.account.AccountState
import ru.zaroslikov.incubator.account.AccountSubscription
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.domain.model.User
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.CardHeader
import ru.zaroslikov.incubator.ui.incubator.TabCard
import ru.zaroslikov.incubator.ui.menu.MenuRow
import ru.zaroslikov.incubator.ui.menu.MenuScreen
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ProfileDestination : NavigationDestination {
    override val route = "Profile"
    override val titleRes = R.string.app_name
}

/**
 * Фирменный синий VK ID. Не токен темы: это цвет чужого знака, и в тёмной теме он тот же —
 * кнопка входа через VK везде должна узнаваться как кнопка VK.
 */
private val VkBlue = Color(0xFF0077FF)

/**
 * «Профиль» — он же аккаунт.
 *
 * **Не вошёл** — приглашение войти: через VK ID (если сборка настроена, см. `VkId`), по
 * почте или создать аккаунт, и первой строкой — что всё это необязательно: инкубаторы и
 * закладки работают и без профиля. **Вошёл** — аватар, имя, хозяйство и город, чем выполнен
 * вход, и два действия: выйти (профиль с телефона стирается) и удалить аккаунт.
 *
 * Отдельного «профиля без аккаунта» больше нет: имя хранится на сервере и общее с «Моим
 * хозяйством», а профиль, живущий без аккаунта, был бы вторым ответом на вопрос «как
 * меня зовут».
 */
@Composable
fun ProfileScreen(
    navigateBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
    accountViewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
    // ViewModel экрана, а не шторки: опрос платежа должен пережить уход в браузер и
    // закрытую по ошибке шторку — открытая заново, она вернётся к тому же ожиданию.
    premiumViewModel: PremiumViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    // Окно правки — `rememberSaveable`: поворот экрана посреди набора не должен его
    // закрывать. Черновик полей живёт внутри окна, см. ProfileEditDialog.
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    // Шторка входа и удаление аккаунта. Сама форма — в AccountViewModel: пароль не должен
    // попадать в сохранённое состояние, а ViewModel переживает поворот и без него.
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var confirmAccountDelete by rememberSaveable { mutableStateOf(false) }
    val accountForm by accountViewModel.state.collectAsStateWithLifecycle()
    var premiumSheet by rememberSaveable { mutableStateOf(false) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { send(ProfileIntent.SetAvatar(it)) } }
    val launchPicker = {
        pickAvatar.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    CollectEffects(viewModel) { effect ->
        when (effect) {
            ProfileEffect.Saved -> editing = false
            // Имени нет ни на сервере, ни в VK — сразу спросить: «вы вошли» без имени
            // читается как половина дела.
            is ProfileEffect.SignedInWithVk -> if (effect.needsName) editing = true
        }
    }

    CollectEffects(accountViewModel) { effect ->
        when (effect) {
            is AccountEffect.SignedIn -> {
                accountSheet = false
                if (effect.needsName) editing = true
            }
            AccountEffect.Deleted -> confirmAccountDelete = false
        }
    }

    val openAccount: (AccountStep) -> Unit = { step ->
        accountViewModel.onIntent(AccountIntent.Open(step))
        accountSheet = true
    }

    val user = state.user
    val signedIn = state.account as? AccountState.SignedIn
    // Статус подписки — свежий при каждом открытии экрана и при каждом входе: оплатить
    // могли в «Моём хозяйстве» минуту назад, и вчерашний ответ говорил бы неправду.
    LaunchedEffect(signedIn?.userId) {
        if (signedIn != null) send(ProfileIntent.RefreshSubscription)
    }
    MenuScreen(
        title = "Профиль",
        // Без подзаголовка: имя вошедшего стоит в карточке прямо под шапкой, а невошедшему
        // о необязательности входа говорит сама карточка приглашения.
        subtitle = null,
        navigateBack = navigateBack,
        contentPadding = contentPadding,
    ) {
        if (state.loading) {
            LoadingBox(Modifier.height(240.dp))
            return@MenuScreen
        }

        state.notice?.let { notice ->
            NoticeCard(notice, onDismiss = { send(ProfileIntent.DismissNotice) })
            Spacer(Modifier.height(16.dp))
        }

        when {
            state.account == AccountState.Unavailable -> UnavailableCard()
            signedIn == null -> SignedOutCard(
                vkAvailable = state.vkAvailable,
                busy = state.busy || accountForm.busy,
                onVk = { send(ProfileIntent.LoginVk) },
                onLogin = { openAccount(AccountStep.Login) },
                onRegister = { openAccount(AccountStep.Register) },
            )
            else -> {
                ProfileCard(
                    user = user,
                    account = signedIn,
                    subscription = state.subscription,
                    busy = state.busy,
                    onEdit = { editing = true },
                    onPickAvatar = launchPicker,
                    onRemoveAvatar = { send(ProfileIntent.RemoveAvatar) },
                )
                if (offersPremium(state.subscription)) {
                    Spacer(Modifier.height(16.dp))
                    BuyPremiumButton(onClick = { premiumSheet = true })
                }
                Spacer(Modifier.height(16.dp))
                LogoutButton(onLogout = { confirmLogout = true })
            }
        }

        Spacer(Modifier.height(16.dp))
        ProfilePrivacyNote(vkAvailable = state.vkAvailable)
    }

    if (accountSheet) {
        AccountSheet(viewModel = accountViewModel, onDismiss = { accountSheet = false })
    }

    // Шторка остаётся открытой и после оплаты — на поздравлении, даже когда кнопка
    // «Купить Premium» под ней уже исчезла. Выход из аккаунта закрывает её.
    if (premiumSheet && signedIn != null) {
        PremiumSheet(
            viewModel = premiumViewModel,
            subscription = state.subscription,
            onDismiss = { premiumSheet = false },
        )
    }

    // Сессию могли отозвать, пока диалог открыт (refresh ответил «сессии нет»): удалять
    // тогда нечего, и диалог закрывается вместе с профилем.
    if (confirmAccountDelete && signedIn != null) {
        DeleteAccountDialog(
            viaVk = signedIn.viaVk,
            busy = accountForm.busy,
            error = accountForm.deleteError,
            onDismiss = { confirmAccountDelete = false },
            onConfirmPassword = { accountViewModel.onIntent(AccountIntent.DeleteAccount(it)) },
            onConfirmVk = { accountViewModel.onIntent(AccountIntent.DeleteWithVk) },
        )
    }

    if (editing && signedIn != null) {
        ProfileEditDialog(
            initial = user,
            creating = !user.hasName,
            busy = state.busy,
            error = state.notice,
            onDismiss = { editing = false },
            onSave = { name, farm, city -> send(ProfileIntent.Save(name, farm, city)) },
            // Удаление — из правки, а не с главного экрана: действие редкое и необратимое,
            // и кнопка ему нужна там, куда приходят что-то менять, а не на виду у каждого.
            onDeleteAccount = {
                editing = false
                accountViewModel.onIntent(AccountIntent.StartDelete)
                confirmAccountDelete = true
            },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Выйти из аккаунта?",
            text = "Профиль — имя, фото, хозяйство и город — сотрётся с этого телефона. " +
                "Имя хранится в аккаунте и вернётся при следующем входе; фото, хозяйство и " +
                "город придётся указать заново.\n\nИнкубаторы, закладки и замеры останутся.",
            confirm = "Выйти",
            danger = false,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                send(ProfileIntent.Logout)
            },
        )
    }
}

/**
 * Не вошёл: профиль заводится входом в аккаунт.
 *
 * Первая строка говорит, что это необязательно, — раньше, чем кнопки: человек, открывший
 * «Профиль» из любопытства, не должен уйти с ощущением, что без аккаунта он пользуется
 * урезанным приложением.
 */
@Composable
private fun SignedOutCard(
    vkAvailable: Boolean,
    busy: Boolean,
    onVk: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    TabCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(user = User(), size = 72)
        }
        Spacer(Modifier.height(16.dp))
        CardHeader(
            title = "Войдите, чтобы завести профиль",
            subtitle = "Необязательно — инкубаторы и закладки работают и без него",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Профиль — это ваше имя, фото, хозяйство и город.",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (vkAvailable) {
            VkButton(text = "Войти через VK ID", busy = busy, onClick = onVk)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onLogin,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = accentButtonColors(),
        ) {
            Text("Войти по почте", style = DesignType.ButtonLabel)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRegister,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.IncomeSurface,
                contentColor = DesignPalette.Accent,
            ),
        ) {
            Text("Создать аккаунт", style = DesignType.ButtonLabel)
        }
    }
}

/** Сборка без адреса сервера: аккаунтов нет, а с ними и профиля. */
@Composable
private fun UnavailableCard() {
    TabCard {
        CardHeader(
            title = "Профиль недоступен",
            subtitle = "В этой сборке не настроен сервер аккаунтов",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Инкубаторы, закладки и всё остальное работают как обычно.",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Профиль: аватар, имя со статусом подписки, хозяйство и город, чем выполнен вход, и путь
 * к правке. Отдельных кнопок для фото нет: нажатие на аватар открывает меню —
 * «Загрузить фото» (или «Изменить фото») и, если фото есть, «Удалить фото».
 */
@Composable
private fun ProfileCard(
    user: User,
    account: AccountState.SignedIn,
    subscription: AccountSubscription?,
    busy: Boolean,
    onEdit: () -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    var photoMenu by remember { mutableStateOf(false) }
    TabCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Меню привязано к аватару через обёртку: DropdownMenu раскрывается от своего
            // родителя, и так оно появляется прямо под фото, а не в углу карточки.
            Box {
                Avatar(
                    user = user,
                    size = 72,
                    signedIn = true,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Фото профиля", onClick = { photoMenu = true }),
                )
                DropdownMenu(
                    expanded = photoMenu,
                    onDismissRequest = { photoMenu = false },
                    containerColor = DesignPalette.Surface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (user.avatar == null) "Загрузить фото" else "Изменить фото",
                                style = DesignType.ListItemTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            photoMenu = false
                            onPickAvatar()
                        },
                    )
                    if (user.avatar != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Удалить фото",
                                    style = DesignType.ListItemTitle,
                                    color = DesignPalette.Expense,
                                )
                            },
                            onClick = {
                                photoMenu = false
                                onRemoveAvatar()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // Имя и значок подписки — в одну строку, пока влезают; длинное имя уводит
                // значок на следующую, а не сжимается ради него.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = user.name.ifBlank { "Имя не указано" },
                        style = DesignType.SectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showsPremiumBadge(subscription)) PremiumBadge()
                }
                val place = listOf(user.farm, user.city).filter { it.isNotBlank() }
                if (place.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = place.joinToString(" · "),
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (account.viaVk) {
                    VkBadge()
                } else {
                    Text(
                        text = account.email,
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Срок — отдельной строкой под значком, а не в нём: значок стоит рядом с
                // именем, и «Premium до 30.09.2027» там уводил бы его на новую строку.
                subscription?.let(::premiumUntil)?.let { until ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Premium действует до $until",
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = accentButtonColors(),
        ) {
            Text(if (user.hasName) "Изменить профиль" else "Заполнить профиль", style = DesignType.ButtonLabel)
        }
    }
}

/**
 * Показывать ли значок Premium: только у действующей подписки. «Без подписки» не пишется
 * вовсе — отсутствие значка и есть этот ответ, а место под ним занимает «Купить Premium».
 */
internal fun showsPremiumBadge(subscription: AccountSubscription?): Boolean =
    subscription?.active == true

/**
 * Показывать ли «Купить Premium»: только когда сервер ответил, что подписки нет. Пока
 * ответа нет (нет сети, ещё грузится), кнопки нет — предлагать купить тому, кто, может
 * быть, только что оплатил, было бы неправдой.
 */
internal fun offersPremium(subscription: AccountSubscription?): Boolean =
    subscription != null && !subscription.active

/** Конец действующей подписки как «12.10.2026»; `null` — срока нет или он нечитаем. */
internal fun premiumUntil(subscription: AccountSubscription): String? {
    if (!subscription.active) return null
    return subscription.expiresAt?.let { iso ->
        runCatching {
            Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrNull()
    }
}

/**
 * Выход из аккаунта — кнопкой во всю ширину, без своей карточки: одна кнопка в рамке
 * читалась как отдельный раздел, которым она не является. Удаление живёт в окне правки —
 * см. [ProfileEditDialog].
 */
@Composable
private fun LogoutButton(onLogout: () -> Unit) {
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
}

/** Кнопка VK ID — фирменного синего, с колесом вместо подписи, пока идёт вход. */
@Composable
private fun VkButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = VkBlue,
            contentColor = Color.White,
            disabledContainerColor = VkBlue.copy(alpha = 0.6f),
            disabledContentColor = Color.White,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            VkMark()
            Spacer(Modifier.width(10.dp))
            Text(text, style = DesignType.ButtonLabel)
        }
    }
}

/** Знак VK — белый скруглённый квадрат с буквами, без картинки из чужого SDK. */
@Composable
private fun VkMark() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "VK",
            color = VkBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
        )
    }
}

@Composable
private fun VkBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VkBlue.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "VK ID",
            style = DesignType.Micro,
            color = VkBlue,
        )
    }
}

/**
 * Аватар: фото из базы или инициалы на зелёном; у невошедшего — пустой кружок с «?».
 *
 * Картинка декодируется один раз на содержимое (`remember(contentHashCode)`): Room на
 * каждый ответ отдаёт новый массив, и ключ по ссылке декодировал бы JPEG заново после
 * любой записи. Большая картинка — из чужой базы, не из этого приложения, которое
 * пишет не больше 256 точек, — читается с уменьшением, а нечитаемая считается
 * отсутствующей.
 */
@Composable
private fun Avatar(user: User, size: Int, modifier: Modifier = Modifier, signedIn: Boolean = false) {
    val bytes = user.avatar
    val image: ImageBitmap? = remember(bytes?.contentHashCode(), bytes?.size) {
        bytes?.let(::decodeAvatar)
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (signedIn) DesignPalette.Accent else DesignPalette.IncomeSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Фото профиля",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        } else {
            Text(
                text = initials(user.name).ifEmpty { "?" },
                color = if (signedIn) DesignPalette.OnAccent else DesignPalette.Accent,
                fontSize = (size * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Аватар для показа; всё, что крупнее 512 точек, читается уменьшенным. */
private fun decodeAvatar(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )?.asImageBitmap()
}.getOrNull()

/** «Семён Заросликов» → «СЗ», «Семён» → «С». */
internal fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    TabCard {
        Text(
            text = text,
            style = DesignType.Body,
            color = DesignPalette.Expense,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Понятно", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
        }
    }
}

/**
 * Где лежит профиль и что знает VK — строка внизу, как `PrivacyNote` в настройках.
 *
 * Сказано ровно то, что правда: на сервер уходят почта, пароль и имя (имя общее с «Моим
 * хозяйством»), а хозяйство, город, фото и сами инкубаторы остаются на телефоне. Про VK —
 * только когда он есть в сборке: говорить об обмене с сервисом, которым здесь нельзя
 * воспользоваться, значит заставлять читать лишнее.
 */
@Composable
private fun ProfilePrivacyNote(vkAvailable: Boolean) {
    Text(
        text = "В аккаунте на сервере хранятся почта, пароль и имя. Фото, хозяйство, город " +
            "и все инкубаторы с закладками остаются только на этом телефоне." +
            if (vkAvailable) {
                " При входе через VK приложение получает от VK только имя и фото; " +
                    "о вашем хозяйстве VK ничего не узнаёт."
            } else {
                ""
            },
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/**
 * Правка профиля — и первое заполнение после входа, когда [creating].
 *
 * Черновик полей — `rememberSaveable` внутри окна: окно создаётся заново при каждом
 * открытии, так что черновик начинается с сохранённого профиля, а поворот экрана
 * посреди набора его не теряет. Имя обязательно и уходит на сервер: пока он отвечает,
 * кнопка занята ([busy]), а его отказ ([error]) показывается здесь же, над полями. Внизу, по
 * центру, — «Удалить аккаунт»: действие редкое и необратимое, и держать его на виду на
 * главной карточке незачем.
 */
@Composable
private fun ProfileEditDialog(
    initial: User,
    creating: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, farm: String, city: String) -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var farm by rememberSaveable { mutableStateOf(initial.farm) }
    var city by rememberSaveable { mutableStateOf(initial.city) }
    // Заголовок и кнопка — по состоянию на момент открытия: после «Готово» база
    // отвечает раньше, чем окно закрывается, и живое `!hasName` успело бы на кадр
    // перекрасить «Новый профиль» в «Профиль».
    val creatingNow by rememberSaveable { mutableStateOf(creating) }
    val canSave = name.isNotBlank() && !busy

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = if (creatingNow) "Новый профиль" else "Профиль",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                error?.let {
                    Text(it, style = DesignType.Micro, color = DesignPalette.Expense)
                    Spacer(Modifier.height(12.dp))
                }
                FieldLabel("Имя", required = true)
                SheetTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileViewModel.NAME_MAX_LENGTH) },
                    placeholder = "Иван Иванов",
                    capitalization = KeyboardCapitalization.Words,
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel("Хозяйство")
                SheetTextField(
                    value = farm,
                    onValueChange = { farm = it.take(ProfileViewModel.FARM_MAX_LENGTH) },
                    placeholder = "Ферма «Рассвет»",
                    capitalization = KeyboardCapitalization.Sentences,
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel("Город")
                SheetTextField(
                    value = city,
                    onValueChange = { city = it.take(ProfileViewModel.CITY_MAX_LENGTH) },
                    placeholder = "Тамбов",
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDeleteAccount,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Удалить аккаунт", style = DesignType.ButtonLabel, color = DesignPalette.Expense)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, farm, city) }, enabled = canSave) {
                Text(
                    text = when {
                        busy -> "Сохраняем…"
                        creatingNow -> "Готово"
                        else -> "Сохранить"
                    },
                    style = DesignType.ButtonLabel,
                    color = if (canSave) DesignPalette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Отмена",
                    style = DesignType.ButtonLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = title,
                style = DesignType.SheetTitle,
                color = if (danger) DesignPalette.Expense else MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = text,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirm,
                    style = DesignType.ButtonLabel,
                    color = if (danger) DesignPalette.Expense else DesignPalette.Accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Отмена",
                    style = DesignType.ButtonLabel,
                    color = if (danger) DesignPalette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
