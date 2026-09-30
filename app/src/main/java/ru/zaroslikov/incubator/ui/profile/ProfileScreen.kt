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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetTextField
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
 * «Профиль» — необязательная регистрация и всё, что к ней относится.
 *
 * У экрана два лица. **Гость** видит, что профиль не нужен ни для чего из работы с
 * инкубаторами, и два пути его завести: VK ID (если сборка настроена, см. `VkId`) и
 * «Заполнить вручную». **Владелец профиля** видит аватар, имя, хозяйство и город, правит
 * их, привязывает или отвязывает VK и может удалить профиль — хозяйство при этом
 * остаётся.
 *
 * Ни один путь не уводит человека с экрана навсегда: вход через VK открывает окно VK и
 * возвращает сюда же, отказ в нём — обычный исход, а не ошибка.
 */
@Composable
fun ProfileScreen(
    navigateBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
    accountViewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    // Окно правки — `rememberSaveable`: поворот экрана посреди набора не должен его
    // закрывать. Черновик полей живёт внутри окна, см. ProfileEditDialog.
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    // Шторка аккаунта и его удаление. Сама форма — в AccountViewModel: пароль не должен
    // попадать в сохранённое состояние, а ViewModel переживает поворот и без него.
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var confirmAccountDelete by rememberSaveable { mutableStateOf(false) }
    val accountForm by accountViewModel.state.collectAsStateWithLifecycle()

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
        }
    }

    CollectEffects(accountViewModel) { effect ->
        when (effect) {
            // Только что созданный аккаунт у гостя — самое время завести и профиль: имя
            // аккаунт не спрашивает, а «вы вошли» без имени читается как половина дела.
            is AccountEffect.SignedIn -> {
                accountSheet = false
                if (effect.newAccount && !state.user.hasProfile) editing = true
            }
            AccountEffect.Deleted -> confirmAccountDelete = false
        }
    }

    val openAccount: (AccountStep) -> Unit = { step ->
        accountViewModel.onIntent(AccountIntent.Open(step))
        accountSheet = true
    }
    val accountCard: @Composable () -> Unit = {
        if (state.account is AccountState.SignedIn || state.account == AccountState.SignedOut) {
            Spacer(Modifier.height(16.dp))
            AccountCard(
                state = state.account,
                onLogin = { openAccount(AccountStep.Login) },
                onRegister = { openAccount(AccountStep.Register) },
                onLogout = { accountViewModel.onIntent(AccountIntent.Logout) },
                onDelete = {
                    accountViewModel.onIntent(AccountIntent.StartDelete)
                    confirmAccountDelete = true
                },
            )
        }
    }

    val user = state.user
    MenuScreen(
        title = "Профиль",
        subtitle = when {
            state.loading -> null
            user.hasProfile -> user.name.ifBlank { "Профиль VK" }
            else -> "Необязательно — приложение работает и без него"
        },
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

        if (!user.hasProfile) {
            GuestCard(
                vkAvailable = state.vkAvailable,
                busy = state.busy,
                onVk = { send(ProfileIntent.LoginVk) },
                onManual = { editing = true },
            )
            accountCard()
        } else {
            ProfileCard(
                user = user,
                onEdit = { editing = true },
                onPickAvatar = launchPicker,
                onRemoveAvatar = { send(ProfileIntent.RemoveAvatar) },
            )

            if (state.vkAvailable || user.isVkLinked) {
                Spacer(Modifier.height(16.dp))
                VkCard(
                    linked = user.isVkLinked,
                    available = state.vkAvailable,
                    busy = state.busy,
                    onLink = { send(ProfileIntent.LoginVk) },
                    onUnlink = { confirmUnlink = true },
                )
            }
            accountCard()

            Spacer(Modifier.height(16.dp))
            DeleteProfileCard(busy = state.busy, onDelete = { confirmDelete = true })
        }

        Spacer(Modifier.height(16.dp))
        ProfilePrivacyNote(
            vkAvailable = state.vkAvailable || user.isVkLinked,
            accountAvailable = state.account != AccountState.Unavailable,
        )
    }

    if (accountSheet) {
        AccountSheet(viewModel = accountViewModel, onDismiss = { accountSheet = false })
    }

    // Сессию могли отозвать, пока диалог открыт (refresh ответил «токен недействителен»):
    // удалять тогда нечем, и диалог закрывается вместе с карточкой.
    if (confirmAccountDelete && state.account is AccountState.SignedIn) {
        DeleteAccountDialog(
            busy = accountForm.busy,
            error = accountForm.deleteError,
            onDismiss = { confirmAccountDelete = false },
            onConfirm = { accountViewModel.onIntent(AccountIntent.DeleteAccount(it)) },
        )
    }

    if (editing) {
        ProfileEditDialog(
            initial = user,
            creating = !user.hasProfile,
            onDismiss = { editing = false },
            onSave = { name, farm, city -> send(ProfileIntent.Save(name, farm, city)) },
        )
    }

    if (confirmUnlink) {
        ConfirmDialog(
            title = "Выйти из VK?",
            text = "Профиль останется на телефоне — с тем же именем и фото. Приложение " +
                "только забудет, что он связан с VK. Привязать его снова можно в любой момент.",
            confirm = "Выйти",
            danger = false,
            onDismiss = { confirmUnlink = false },
            onConfirm = {
                confirmUnlink = false
                send(ProfileIntent.UnlinkVk)
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить профиль?",
            text = "Имя, фото, хозяйство и город будут стёрты" +
                (if (user.isVkLinked) ", привязка к VK — снята" else "") +
                ".\n\nИнкубаторы, закладки и замеры останутся — профиль к ним не " +
                "привязан, и пользоваться приложением можно и дальше.",
            confirm = "Удалить",
            danger = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                send(ProfileIntent.Delete)
            },
        )
    }
}

/**
 * Гость: профиль не заведён.
 *
 * Первая строка говорит, что регистрация необязательна, — раньше, чем кнопки: человек,
 * открывший «Профиль» из любопытства, не должен уйти с ощущением, что без аккаунта он
 * пользуется урезанным приложением.
 */
@Composable
private fun GuestCard(
    vkAvailable: Boolean,
    busy: Boolean,
    onVk: () -> Unit,
    onManual: () -> Unit,
) {
    TabCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(user = User(), size = 72)
        }
        Spacer(Modifier.height(16.dp))
        CardHeader(
            title = "Вы пользуетесь приложением без профиля",
            subtitle = "Все функции доступны и так — регистрация необязательна",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Профиль — это ваше имя, фото, хозяйство и город. Он хранится на " +
                "телефоне вместе с данными и переезжает с копией базы на новый телефон.",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (vkAvailable) {
            VkButton(text = "Войти через VK ID", busy = busy, onClick = onVk)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onManual,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (vkAvailable) DesignPalette.IncomeSurface else DesignPalette.Accent,
                contentColor = if (vkAvailable) DesignPalette.Accent else DesignPalette.OnAccent,
            ),
        ) {
            Text("Заполнить вручную", style = DesignType.ButtonLabel)
        }
    }
}

/** Профиль: аватар, имя, хозяйство и город, и путь к правке. */
@Composable
private fun ProfileCard(
    user: User,
    onEdit: () -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    TabCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                user = user,
                size = 72,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Сменить фото", onClick = onPickAvatar),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name.ifBlank { "Без имени" },
                    style = DesignType.SectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                if (user.isVkLinked) {
                    Spacer(Modifier.height(6.dp))
                    VkBadge()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPickAvatar) {
                Text(
                    text = if (user.avatar == null) "Добавить фото" else "Сменить фото",
                    style = DesignType.ButtonLabel,
                    color = DesignPalette.Accent,
                )
            }
            if (user.avatar != null) {
                TextButton(onClick = onRemoveAvatar) {
                    Text(
                        text = "Убрать фото",
                        style = DesignType.ButtonLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Accent,
                contentColor = DesignPalette.OnAccent,
            ),
        ) {
            Text("Изменить профиль", style = DesignType.ButtonLabel)
        }
    }
}

/**
 * VK ID: привязать или выйти.
 *
 * Показывается и в сборке без ключей VK, если профиль уже привязан — например, приехал
 * в копии базы с телефона, где VK был: выйти должно быть можно всегда, а войти — только
 * там, где есть чем.
 */
@Composable
private fun VkCard(
    linked: Boolean,
    available: Boolean,
    busy: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    TabCard {
        CardHeader(
            title = "VK ID",
            subtitle = if (linked) "Профиль связан с VK" else "Профиль не связан с VK",
        )
        Spacer(Modifier.height(16.dp))
        if (linked) {
            MenuRow(
                title = "Выйти из VK",
                description = "Профиль, имя и фото останутся на телефоне",
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUnlink,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.IncomeSurface,
                    contentColor = DesignPalette.Accent,
                ),
            ) {
                Text("Выйти из VK", style = DesignType.ButtonLabel)
            }
        } else if (available) {
            MenuRow(
                title = "Привязать VK ID",
                description = "Пустые имя и фото заполнятся из VK, заполненные останутся как есть",
            )
            Spacer(Modifier.height(12.dp))
            VkButton(text = "Привязать VK ID", busy = busy, onClick = onLink)
        }
    }
}

/**
 * Удаление профиля — отдельной карточкой, последней и красной, как «Удалить все данные»
 * в настройках. Но слова другие, и это главное: удаляется профиль, а не хозяйство, и
 * карточка говорит это до нажатия.
 */
@Composable
private fun DeleteProfileCard(busy: Boolean, onDelete: () -> Unit) {
    TabCard {
        MenuRow(
            title = "Удалить профиль",
            description = "Инкубаторы, закладки и замеры останутся",
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDelete,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Expense,
                contentColor = Color.White,
            ),
        ) {
            Text("Удалить профиль", style = DesignType.ButtonLabel)
        }
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
 * Аватар: фото из базы или инициалы на зелёном; у гостя — пустой кружок с «?».
 *
 * Картинка декодируется один раз на содержимое (`remember(contentHashCode)`): Room на
 * каждый ответ отдаёт новый массив, и ключ по ссылке декодировал бы JPEG заново после
 * любой записи. Большая картинка — из чужой базы, не из этого приложения, которое
 * пишет не больше 256 точек, — читается с уменьшением, а нечитаемая считается
 * отсутствующей.
 */
@Composable
private fun Avatar(user: User, size: Int, modifier: Modifier = Modifier) {
    val bytes = user.avatar
    val image: ImageBitmap? = remember(bytes?.contentHashCode(), bytes?.size) {
        bytes?.let(::decodeAvatar)
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (user.hasProfile) DesignPalette.Accent else DesignPalette.IncomeSurface),
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
                color = if (user.hasProfile) DesignPalette.OnAccent else DesignPalette.Accent,
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
 * Про VK — только когда он есть в сборке или в профиле: говорить об обмене с сервисом,
 * которым здесь нельзя воспользоваться, значит заставлять читать лишнее.
 *
 * Про сервер — по той же мерке: в сборке с сервером аккаунтов фраза «сервера у
 * приложения нет» стала бы неправдой, а обещание о приватности, которое можно поймать на
 * противоречии, хуже отсутствующего. Там сказано точнее: что уходит на сервер — почта и
 * пароль — и чего туда не уходит.
 */
@Composable
private fun ProfilePrivacyNote(vkAvailable: Boolean, accountAvailable: Boolean) {
    Text(
        text = (
            if (accountAvailable) {
                "Профиль и хозяйство хранятся только на этом телефоне. На сервер " +
                    "аккаунтов уходят лишь почта и пароль, если вы создадите аккаунт."
            } else {
                "Профиль хранится только на этом телефоне — сервера у приложения нет."
            }
            ) +
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
 * Правка профиля — и ручная регистрация, когда [creating].
 *
 * Черновик полей — `rememberSaveable` внутри окна: окно создаётся заново при каждом
 * открытии, так что черновик начинается с сохранённого профиля, а поворот экрана
 * посреди набора его не теряет. Имя обязательно всегда, и с VK тоже: профиль без имени,
 * отвязанный потом от VK, стал бы гостем с невидимыми хозяйством, городом и фото.
 */
@Composable
private fun ProfileEditDialog(
    initial: User,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, farm: String, city: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var farm by rememberSaveable { mutableStateOf(initial.farm) }
    var city by rememberSaveable { mutableStateOf(initial.city) }
    // Заголовок и кнопка — по состоянию на момент открытия: после «Создать» база
    // отвечает раньше, чем окно закрывается, и живое `!hasProfile` успело бы на кадр
    // перекрасить «Новый профиль» в «Профиль».
    val creatingNow by rememberSaveable { mutableStateOf(creating) }
    val canSave = name.isNotBlank()

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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, farm, city) }, enabled = canSave) {
                Text(
                    text = if (creatingNow) "Создать" else "Сохранить",
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
