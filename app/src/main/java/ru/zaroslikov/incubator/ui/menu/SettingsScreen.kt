package ru.zaroslikov.incubator.ui.menu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.rememberBannerAdHost
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.settings.TransferState
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.CardHeader
import ru.zaroslikov.incubator.ui.incubator.EmptyNote
import ru.zaroslikov.incubator.ui.incubator.TabCard
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.species.CustomSpeciesSheet
import ru.zaroslikov.incubator.ui.species.speciesSummary
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.design.theme.ThemeMode
import ru.zaroslikov.incubator.design.components.ChoiceChip
import ru.zaroslikov.incubator.ui.components.DashedAddButton
import ru.zaroslikov.incubator.design.components.HintIcon
import ru.zaroslikov.incubator.design.components.SheetDropdownField
import ru.zaroslikov.incubator.ui.components.ShowAllToggle
import ru.zaroslikov.incubator.design.components.SlidingTab
import ru.zaroslikov.incubator.design.components.SlidingTabSwitcher
import ru.zaroslikov.incubator.design.components.rememberSheetDraft
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

object SettingsDestination : NavigationDestination {
    override val route = "Settings"
}

/** Шторка своего вида закрыта. Ноль — создание, больше нуля — правка вида с этим id. */
private const val NO_SPECIES_SHEET = -1L

/** Сколько своих видов показывает свёрнутая карточка «Свои виды птицы». */
private const val VisibleSpecies = 2

/**
 * «Настройки»: уведомления, оформление, единицы, свои виды птицы и перенос базы.
 *
 * Свои виды — общие для хозяйства, поэтому список, правка и удаление собраны здесь, хотя заводят их
 * чаще из формы закладки. Раздел уведомлений короткий: времена напоминаний принадлежат закладке и
 * задаются в её форме; здесь — общий выключатель и путь в системные настройки.
 *
 * Перенос базы — файлом через системный выбор места; чтение и запись живут в `:data`, сюда приходит
 * готовый исход.
 */
@Composable
fun SettingsScreen(
    navigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    // Отдельным значением только потому, что состояние переноса читают четыре места, и
    // одно из них — ключ LaunchedEffect ниже.
    val transfer = state.transfer
    var speciesSheet by rememberSaveable { mutableStateOf(NO_SPECIES_SHEET) }
    // Черновик конструктора вида живёт в композиции экрана, а не шторки: свёрнутую
    // шторку открывают тем же вводом, а уход с настроек его отменяет. Один на всё
    // место вызова — создание и правка идут через одну ViewModel.
    val speciesDraft = rememberSheetDraft()
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    var chooseExport by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }

    // Разрешение перечитывается на каждом возврате к экрану, и это не перестраховка.
    // Выдают его в системных настройках — то есть в другом приложении, — и вернуться
    // оттуда можно только сюда. Никакого состояния при этом не меняется, поэтому сам по
    // себе экран не перерисуется: прочитанное однажды значение так и висело бы «не
    // выдано» над только что выданным разрешением, и человек решил бы, что не помогло.
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = hasNotificationPermission(context)
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        // Тип MIME произвольный: у файла SQLite своего типа нет, и «application/octet-stream»
        // — единственное, на что согласны все провайдеры документов.
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? -> uri?.let { send(SettingsIntent.Export(it)) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { send(SettingsIntent.Import(it)) } }

    // Отправку запускает экран, а не контроллер: системное окно «Поделиться» — это
    // намерение, а запускать намерения может только тот, у кого есть активность.
    // Контроллер лишь готовит файл и говорит «готово», после чего состояние сбрасывается
    // сразу: копия уже у системы, и оставленное состояние открывало бы то же окно
    // второй раз при каждом возврате на экран.
    LaunchedEffect(transfer) {
        val ready = transfer as? TransferState.ReadyToShare ?: return@LaunchedEffect
        shareCopy(context, ready.uri)
        send(SettingsIntent.ClearTransfer)
    }

    MenuScreen(
        title = "Настройки",
        subtitle = "Оформление, единицы, уведомления и данные",
        navigateBack = navigateBack,
        contentPadding = contentPadding,
    ) {
        NotificationsCard(
            enabled = state.remindersEnabled,
            permissionGranted = notificationsAllowed,
            onToggle = { send(SettingsIntent.SetReminders(it)) },
            onOpenSystemSettings = { openNotificationSettings(context) },
        )

        Spacer(Modifier.height(16.dp))

        AppearanceCard(
            mode = state.themeMode,
            onSelect = { send(SettingsIntent.SetThemeMode(it)) },
        )

        Spacer(Modifier.height(16.dp))

        TemperatureUnitCard(
            unit = state.units.temperature,
            onSelect = { send(SettingsIntent.SetTemperatureUnit(it)) },
        )

        Spacer(Modifier.height(16.dp))

        CurrencyCard(
            currency = state.units.currency,
            onSelect = { send(SettingsIntent.SetCurrency(it)) },
        )

        Spacer(Modifier.height(16.dp))

        CustomSpeciesCard(
            species = state.customSpecies,
            onAdd = { speciesSheet = 0L },
            onEdit = { speciesSheet = it.id },
            onDelete = { send(SettingsIntent.RequestDeleteSpecies(it)) },
        )

        Spacer(Modifier.height(16.dp))

        DataCard(
            busy = transfer is TransferState.Working,
            onExport = { chooseExport = true },
            onImport = { confirmImport = true },
        )

        Spacer(Modifier.height(16.dp))

        DangerCard(
            busy = transfer is TransferState.Working,
            onWipe = { confirmWipe = true },
        )

        Spacer(Modifier.height(16.dp))

        PrivacyNote()

        // Реклама — последняя карточка, под примечанием: она не настройка и стоять
        // между ними не должна.
        AdBanner(rememberBannerAdHost(), Modifier.padding(top = 16.dp))
    }

    if (speciesSheet != NO_SPECIES_SHEET) {
        CustomSpeciesSheet(
            speciesId = speciesSheet,
            draft = speciesDraft,
            onDismiss = { speciesSheet = NO_SPECIES_SHEET },
            // Список под шторкой на потоке и перечитается сам; закрыть — всё, что нужно.
            onSaved = { speciesSheet = NO_SPECIES_SHEET },
        )
    }

    state.deleteRequest?.let { request ->
        DeleteSpeciesDialog(
            request = request,
            onDismiss = { send(SettingsIntent.CancelDeleteSpecies) },
            onConfirm = {
                Analytics.report(Events.CUSTOM_SPECIES_DELETED)
                send(SettingsIntent.ConfirmDeleteSpecies)
            },
        )
    }

    if (chooseExport) {
        ExportChoiceDialog(
            onDismiss = { chooseExport = false },
            onSaveToDevice = {
                chooseExport = false
                Analytics.report(Events.DB_EXPORT)
                exportLauncher.launch(SettingsViewModel.defaultExportName())
            },
            onShare = {
                chooseExport = false
                Analytics.report(Events.DB_SHARE)
                send(SettingsIntent.ShareCopy)
            },
        )
    }

    if (confirmWipe) {
        ConfirmWipeDialog(
            onDismiss = { confirmWipe = false },
            onConfirm = {
                confirmWipe = false
                Analytics.report(Events.DB_WIPE)
                send(SettingsIntent.Wipe)
            },
        )
    }

    if (confirmImport) {
        ConfirmImportDialog(
            onDismiss = { confirmImport = false },
            onConfirm = {
                confirmImport = false
                Analytics.report(Events.DB_IMPORT)
                importLauncher.launch(arrayOf("*/*"))
            },
        )
    }

    TransferDialog(
        state = transfer,
        onDismiss = { send(SettingsIntent.ClearTransfer) },
    )
}

/**
 * Уведомления: выключатель и состояние системного разрешения.
 *
 * Разрешение показано отдельной строкой, а не спрятано в выключатель, потому что это
 * два разных «нет». Выключенный тумблер — решение пользователя, снимаемое обратно тут
 * же; отозванное разрешение — запрет системы, и включённый над ним тумблер честно
 * обещал бы уведомления, которых не будет. Поэтому при отсутствии разрешения строка
 * ведёт в системные настройки: вернуть его изнутри приложения уже нельзя.
 */
@Composable
private fun NotificationsCard(
    enabled: Boolean,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    TabCard {
        CardHeader(
            title = "Уведомления",
            subtitle = "Напоминания о проверке инкубатора",
        )
        Spacer(Modifier.height(16.dp))

        MenuRow(
            title = "Напоминания",
            description = reminderDescription(enabled, permissionGranted),
            trailing = { MenuSwitch(checked = enabled, onCheckedChange = onToggle) },
        )

        MenuRowDivider()

        MenuRow(
            title = "Системные настройки",
            description = if (permissionGranted) {
                "Звук, важность и показ на экране блокировки"
            } else {
                "Разрешение на уведомления не выдано — его выдают здесь"
            },
            onClick = onOpenSystemSettings,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Время напоминаний и их текст задаются в каждой закладке отдельно.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun reminderDescription(enabled: Boolean, permissionGranted: Boolean): String = when {
    !enabled -> "Выключены — расписание закладок сохранено"
    !permissionGranted -> "Включены, но система их не покажет"
    else -> "Приходят в заданное закладкой время"
}

/** Вкладки переключателя темы — в порядке [ThemeMode.entries]. */
private val ThemeTabs = listOf(
    SlidingTab("Системная", R.drawable.ic_theme_auto_design),
    SlidingTab("Светлая", R.drawable.ic_theme_light_design),
    SlidingTab("Тёмная", R.drawable.ic_theme_dark_design),
)

/**
 * Оформление: светлая тема, тёмная или как у системы. Сегментированный переключатель, как у вкладок
 * инкубатора; своей страницы у него нет, поэтому положение «таблетки» анимируется здесь
 * (`animateFloatAsState`). «Системная» первая: она по умолчанию и верна для большинства.
 */
@Composable
private fun AppearanceCard(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val position by animateFloatAsState(
        targetValue = mode.ordinal.toFloat(),
        animationSpec = tween(durationMillis = 220),
        label = "themePill",
    )
    TabCard {
        CardHeader(
            title = "Оформление",
            subtitle = "Светлая, тёмная или как на телефоне",
        )
        Spacer(Modifier.height(16.dp))
        SlidingTabSwitcher(
            tabs = ThemeTabs,
            position = { position },
            onSelect = { onSelect(ThemeMode.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = themeDescription(mode),
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun themeDescription(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Приложение переключается вслед за темой телефона: включили " +
        "ночной режим — стало тёмным, выключили — светлым."
    ThemeMode.LIGHT -> "Всегда светлая, независимо от настроек телефона."
    ThemeMode.DARK -> "Всегда тёмная, независимо от настроек телефона."
}

/**
 * Температура: Цельсий или Фаренгейт.
 *
 * Чипы, а не «таблетка» темы: у той каждой вкладке положен значок, а нарисовать
 * различимые значки для °C и °F — значит нарисовать буквы; здесь буквы и есть выбор.
 * Валюта ниже — выпадающий список: шести пунктам ни чипы, ни «таблетка» не подходят.
 *
 * Подпись под чипами говорит главное: пересчитывается показ, а не записи. В базе
 * температура всегда в Цельсиях, и переключение туда-обратно ничего не портит — см.
 * [ru.zaroslikov.incubator.settings.TemperatureUnit].
 */
@Composable
private fun TemperatureUnitCard(unit: TemperatureUnit, onSelect: (TemperatureUnit) -> Unit) {
    TabCard {
        CardHeader(
            title = "Температура",
            subtitle = "В каких градусах показывать режим и замеры",
        )
        Spacer(Modifier.height(16.dp))
        ChoiceRow {
            TemperatureUnit.entries.forEach { option ->
                ChoiceChip(
                    text = "${option.symbol} · ${option.title}",
                    selected = option == unit,
                    onClick = { onSelect(option) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Все температуры — план, замеры, отклонения, аналитика — переводятся " +
                "на лету, ввод тоже в этих градусах. Записанное не меняется: переключили " +
                "обратно — цифры те же.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Валюта сумм — знак после числа.
 *
 * Только знак: суммы хранятся числом без валюты, и курса у приложения нет. Выбрать
 * тенге после года в рублях значит подписать те же числа иначе, а не пересчитать их —
 * подпись под полем говорит это прямо, чтобы никто не ждал конвертации.
 *
 * Выпадающий список, а не чипы, как у температуры: валют шесть, и рядом чипов на три
 * строки они занимали полкарточки ради выбора, который делают один раз; список закрытым
 * не занимает ничего, а открытым показывает все шесть разом.
 */
@Composable
private fun CurrencyCard(currency: Currency, onSelect: (Currency) -> Unit) {
    TabCard {
        CardHeader(
            title = "Валюта",
            subtitle = "Знак у стоимости яиц, птенцов и инкубатора",
        )
        Spacer(Modifier.height(16.dp))
        SheetDropdownField(
            options = Currency.entries,
            selected = currency,
            label = { "${it.symbol} · ${it.title}" },
            onSelect = onSelect,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Меняется только знак. Суммы по курсу не пересчитываются: сколько " +
                "записали, столько и останется.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Чипы выбора с переносом на новую строку — как ряд фильтров над закладками. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Свои виды птицы: список с правкой по нажатию, корзиной справа и пунктирной кнопкой «Добавить
 * вид» под ним.
 *
 * **Кнопка, а не строка списка**: строкой она выглядела бы ещё одним видом. Пунктирная кнопка —
 * то, чем в приложении говорят «добавить ещё один».
 *
 * Корзина стоит в строке, а не в меню. Удалить вид, по которому идёт закладка, нельзя — это
 * объясняет диалог. **Корзина нейтрально-серая, а красный ждёт в диалоге**: красные иконки по одной
 * на вид были бы ярче всего на экране, красный здесь значит «отсюда не вернуться».
 *
 * Подпись «21 день · 3 овоскопирования» говорит, чем виды отличаются, не открывая их.
 *
 * **Свёрнутый список — два вида, остальные за «Показать все (N)»** ([ShowAllToggle], с числом):
 * десяток строк отодвигал бы «Оформление» и перенос базы за край экрана. Раскрытие в
 * `rememberSaveable`.
 */
@Composable
private fun CustomSpeciesCard(
    species: List<CustomSpecies>,
    onAdd: () -> Unit,
    onEdit: (CustomSpecies) -> Unit,
    onDelete: (CustomSpecies) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TabCard {
        CardHeader(
            title = "Свои виды птицы",
            subtitle = "Режимы инкубации, которых нет среди встроенных",
        )
        if (species.isEmpty()) {
            EmptyNote(
                "Пока ни одного своего вида. Добавьте его здесь или прямо в форме " +
                    "закладки — плиткой «Свой вид» рядом с остальными птицами."
            )
        } else {
            Spacer(Modifier.height(16.dp))
            val shown = if (expanded) species else species.take(VisibleSpecies)
            Column(Modifier.animateContentSize()) {
                shown.forEachIndexed { index, item ->
                    if (index > 0) MenuRowDivider()
                    MenuRow(
                        title = item.name,
                        description = customSpeciesSummary(item),
                        onClick = { onEdit(item) },
                        trailing = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_delete_24),
                                    contentDescription = "Удалить вид «${item.name}»",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            if (species.size > VisibleSpecies) {
                Spacer(Modifier.height(12.dp))
                ShowAllToggle(
                    expanded = expanded,
                    total = species.size,
                    onClick = { expanded = !expanded },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DashedAddButton(text = "Добавить вид", onClick = onAdd)

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Вид общий для всех инкубаторов. Правка его дней не меняет расписание " +
                "уже созданных закладок — оно записано в них самих.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Подпись под именем вида — та же, что показывает конструктор под своей таблицей. */
private fun customSpeciesSummary(species: CustomSpecies): String =
    speciesSummary(days = species.length, candlings = species.candlingDays.size)

/**
 * Удаление своего вида — вопрос либо объяснение, почему нельзя.
 *
 * Одно окно на оба исхода, потому что нажатие одно и то же, а ответ зависит от того,
 * что нашлось в базе. С идущими закладками кнопка одна — «Понятно»: предлагать
 * «Удалить» рядом с текстом «нельзя» значило бы обещать то, чего не будет.
 *
 * Завершённые закладки удалению не мешают: их план записан в них самих, а срок и
 * овоскопирования им больше не нужны. Это диалог и говорит — иначе вопрос «а что
 * будет с моими прошлыми закладками» останется без ответа до нажатия.
 */
@Composable
private fun DeleteSpeciesDialog(
    request: DeleteSpeciesRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = request.species.name
    val blocked = request.activeBatches > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = if (blocked) "Вид используется" else "Удалить вид «$name»?",
                style = DesignType.SheetTitle,
                color = if (blocked) MaterialTheme.colorScheme.onSurface else DesignPalette.Expense,
            )
        },
        text = {
            Text(
                text = if (blocked) {
                    val n = request.activeBatches
                    val verb = if (n % 10 == 1 && n % 100 != 11) "идёт" else "идут"
                    "По виду «$name» сейчас $verb " +
                        "${plural(n, "закладка", "закладки", "закладок")}. Без вида они " +
                        "остались бы без срока и овоскопирований. Завершите их или " +
                        "дождитесь вывода — потом вид можно будет удалить."
                } else {
                    "Завершённые закладки по этому виду останутся: их расписание записано " +
                        "в них самих. Новую закладку по виду «$name» создать будет нельзя, " +
                        "пока его не опишут заново."
                },
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            if (blocked) {
                TextButton(onClick = onDismiss) {
                    Text("Понятно", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
                }
            } else {
                TextButton(onClick = onConfirm) {
                    Text("Удалить", style = DesignType.ButtonLabel, color = DesignPalette.Expense)
                }
            }
        },
        dismissButton = if (blocked) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text("Отмена", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
                }
            }
        },
    )
}

/**
 * Перенос базы. Экспорт назван «копией»: файл — вся база, и он же переносит хозяйство на новый
 * телефон. Импорт — заменой: слияния приложение не умеет. Кнопка копии спрашивает куда — см.
 * [ExportChoiceDialog].
 *
 * **Кнопка импорта не красная**: красный значит «отсюда не вернуться», а нажатие лишь открывает
 * [ConfirmImportDialog], который и предупреждает; красный остаётся за «Удалить все данные».
 *
 * **Пояснения к «Экспорту» и «Импорту» — за «i» рядом с заголовком**: их читают один раз, а места
 * они занимали всегда.
 */
@Composable
private fun DataCard(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    TabCard {
        Text(
            text = "Управление данными",
            style = DesignType.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        TitleWithHint(title = "Экспорт", hint = ExportHint)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Accent,
                contentColor = DesignPalette.OnAccent,
            ),
        ) {
            Text(text = if (busy) "Подождите…" else "Сохранить копию", style = DesignType.ButtonLabel)
        }

        MenuRowDivider()

        TitleWithHint(title = "Импорт", hint = ImportHint)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.IncomeSurface,
                contentColor = DesignPalette.Accent,
            ),
        ) {
            Text(text = "Восстановить из копии", style = DesignType.ButtonLabel)
        }
    }
}

/** Заголовок строки карточки со значком «i» сразу за ним. */
@Composable
private fun TitleWithHint(title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = DesignType.ListItemTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HintIcon(hint = hint)
    }
}

private const val ExportHint =
    "Сохранить инкубаторы, закладки, замеры и овоскопирования в файл на устройстве " +
        "или отправить его в другое приложение. Открыть копию сможет только это приложение."

private const val ImportHint =
    "Перенести хозяйство с другого телефона или вернуть его из сохранённой копии. " +
        "Текущие данные будут заменены."

/**
 * Удаление всех данных — отдельной карточкой, последней и красной.
 *
 * Отдельно от «Управления данными» намеренно. Там всё обратимо: экспорт ничего не
 * трогает, импорт заменяет одну базу другой, и у человека на руках остаётся файл.
 * Здесь не остаётся ничего, и соседство с кнопками, которые «просто работают с
 * файлом», притупляло бы разницу. Поэтому карточка своя, стоит после всего и говорит о
 * необратимости до нажатия, а не только в диалоге: заголовок, одна фраза и кнопка.
 */
@Composable
private fun DangerCard(busy: Boolean, onWipe: () -> Unit) {
    TabCard {
        Text(
            text = "Удаление данных",
            style = DesignType.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Будут удалены все данные приложения без возможности вернуть их.",
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onWipe,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Expense,
                contentColor = Color.White,
            ),
        ) {
            Text(text = "Удалить все данные", style = DesignType.ButtonLabel)
        }
    }
}

/**
 * Строка внизу экрана: данные никуда не уходят — стоит здесь, потому что вопрос «где всё это
 * лежит» возникает после «экспорта» и «удалить всё».
 *
 * **Речь о данных, а не о приложении в целом**: AppMetrica шлёт события (открытые экраны,
 * сохранения), так что «приложение ничего не отправляет» было бы неправдой. Содержимое базы в
 * аналитику не попадает, и вынести его может только человек.
 */
@Composable
private fun PrivacyNote() {
    Text(
        text = "Все данные хранятся только на вашем устройстве — копия покидает телефон, " +
            "только если вы отправите её сами. Реклама в приложении — от Яндекса; ей " +
            "не передаётся ничего о вашем хозяйстве.",
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/**
 * Куда девать копию: файлом на устройство или сразу в другое приложение.
 *
 * Два разных ответа на два разных вопроса, и потому не одна кнопка с догадкой. «На
 * устройстве» — это перенос на новый телефон и хранение про запас: файл ложится туда,
 * куда человек укажет в системном выборе, и остаётся там. «Отправить» — это почта себе,
 * мессенджер, облако: файл уходит наружу, и решение об этом должно быть нажатием, а не
 * побочным следствием сохранения.
 *
 * Кнопки в теле диалога, а не в его штатных слотах: слоты кладут кнопки в строку, и два
 * длинных названия в ней либо ужимаются до нечитаемого, либо переносятся вразнобой.
 */
@Composable
private fun ExportChoiceDialog(
    onDismiss: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = "Куда сохранить копию?",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = "Копия — это вся база целиком, одним файлом. Открыть её " +
                        "сможет только это приложение.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSaveToDevice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.Accent,
                        contentColor = DesignPalette.OnAccent,
                    ),
                ) {
                    Text("Сохранить на устройстве", style = DesignType.ButtonLabel)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.IncomeSurface,
                        contentColor = DesignPalette.Accent,
                    ),
                ) {
                    // Подпись длиннее кнопки и переносится на две строки; без явного
                    // выравнивания вторая строка прижимается влево и кнопка выглядит
                    // сломанной.
                    Text(
                        text = "Отправить в другое приложение",
                        style = DesignType.ButtonLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {},
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

/**
 * Предупреждение перед удалением всего. Перечисляет, что исчезнет (инкубаторы, закладки, замеры,
 * овоскопирования): «все данные» читается как «настройки и кэш». Тут же выход — сохранить копию.
 *
 * Кнопки неравноправны: красное «Удалить всё» в слоте подтверждения, а «Отмена» акцентным зелёным —
 * промах стоит всего хозяйства.
 */
@Composable
private fun ConfirmWipeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = "Удалить все данные?",
                style = DesignType.SheetTitle,
                color = DesignPalette.Expense,
            )
        },
        text = {
            Text(
                text = "Будут удалены все инкубаторы, закладки, замеры, овоскопирования " +
                    "и напоминания — и те, что в архиве, тоже. Приложение станет таким " +
                    "же, как после установки.\n\nВернуть это будет нечем: копии в " +
                    "интернете нет, данные хранятся только на этом устройстве. Если они " +
                    "ещё могут понадобиться — закройте окно и сперва сохраните копию.\n\n" +
                    "После удаления приложение перезапустится.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить всё", style = DesignType.ButtonLabel, color = DesignPalette.Expense)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
            }
        },
    )
}

/**
 * Спрашивает перед импортом, а не после выбора файла.
 *
 * Порядок важен: предупреждение о потере данных должно стоять до того, как человек
 * пошёл искать файл, — согласие, взятое после долгого выбора, берётся у того, кто уже
 * настроился и читать перестал.
 */
@Composable
private fun ConfirmImportDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = "Заменить все данные?",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "Инкубаторы, закладки, замеры и овоскопирования, которые есть " +
                    "сейчас, будут удалены и заменены содержимым файла. Отменить это " +
                    "будет нечем — если текущие данные нужны, сперва сохраните копию.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Выбрать файл", style = DesignType.ButtonLabel, color = DesignPalette.Expense)
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

/**
 * Итог переноса.
 *
 * Два исхода из четырёх заканчиваются перезапуском, и он **не предлагается, а
 * сообщается**. Кнопка «останусь здесь» была бы обещанием, которого приложение не может
 * выполнить: соединение с базой уже закрыто, и первый же экран за диалогом упал бы.
 * Перезапуск делает [ru.zaroslikov.incubator.settings.DataTransferController] сам,
 * через пару секунд после того, как выставит состояние; диалог существует ровно затем,
 * чтобы человек успел прочитать, почему приложение сейчас закроется, и не принял это
 * за поломку.
 */
@Composable
private fun TransferDialog(
    state: TransferState,
    onDismiss: () -> Unit,
) {
    val text = when (state) {
        is TransferState.Exported -> "Копия сохранена."
        is TransferState.Imported ->
            "Данные загружены. Приложение сейчас перезапустится, чтобы открыть новую базу."

        is TransferState.Wiped ->
            "Все данные удалены. Приложение сейчас перезапустится и откроется пустым."

        is TransferState.Failed -> state.message
        is TransferState.FailedNeedsRestart -> state.message
        else -> return
    }
    // Перезапуск идёт у обоих исходов, в которых база уже была закрыта: и у удавшегося,
    // и у сорвавшегося с откатом. Во втором данные на диске целы, но соединение с ними
    // закрыто, и отпускать человека к экранам, которые тут же упадут, нельзя.
    val restarting = state is TransferState.Imported ||
        state is TransferState.Wiped ||
        state is TransferState.FailedNeedsRestart
    val failed = state is TransferState.Failed || state is TransferState.FailedNeedsRestart

    AlertDialog(
        onDismissRequest = { if (!restarting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = if (failed) "Не получилось" else "Готово",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
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
            // У ждущего перезапуска исхода кнопки нет вовсе: нажимать не на что, всё
            // решено. Пустой слот честнее неактивной кнопки — та обещает действие.
            if (!restarting) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Понятно",
                        style = DesignType.ButtonLabel,
                        color = DesignPalette.Accent,
                    )
                }
            }
        },
    )
}

/**
 * Отдаёт готовую копию системному окну «Поделиться».
 *
 * `ACTION_SEND` через `createChooser`, а не прямой запуск: файл базы не открывается
 * ничем, кроме этого приложения, так что «приложения по умолчанию» для него нет и быть
 * не может — выбирать адресата человек будет каждый раз заново.
 *
 * `FLAG_GRANT_READ_URI_PERMISSION` обязателен. Без него выбранное приложение получит
 * ссылку, которую не имеет права прочитать: `content://`-адрес от FileProvider сам по
 * себе никаких прав не даёт, их выдаёт именно намерение и ровно на время своей жизни.
 */
private fun shareCopy(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Отправить копию базы"))
    } catch (e: Exception) {
        // Отправлять нечем — на устройстве нет ни одного приложения, принимающего файлы.
        // Сказать об этом здесь нечем, а падать из-за этого незачем: копия на устройство
        // остаётся рядом, второй кнопкой того же диалога.
    }
}

/**
 * Выдано ли разрешение на уведомления.
 *
 * До Android 13 разрешения не существовало вовсе, и уведомления показывались всегда, —
 * там ответ всегда «да». Проверять на более старых системах нечего: `POST_NOTIFICATIONS`
 * им неизвестен, и запрос вернул бы отказ на ровном месте.
 */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Открывает системный экран уведомлений приложения.
 *
 * `ACTION_APP_NOTIFICATION_SETTINGS` без запасного пути: он появился в Android 8, а
 * `minSdk` приложения — 26, то есть та же восьмёрка. `try` вокруг всё же нужен —
 * намерение обслуживает системный лаунчер настроек, а он на части прошивок вырезан.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Настроек нет — сказать об этом всё равно нечем, а падать из-за них незачем.
    }
}

