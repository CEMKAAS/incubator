package ru.zaroslikov.incubator.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.rememberBannerAdHost
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.rustore.IS_RUSTORE_BUILD
import ru.zaroslikov.incubator.rustore.UpdateCheckOutcome
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.CardHeader
import ru.zaroslikov.incubator.ui.incubator.TabCard
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

object AboutDestination : NavigationDestination {
    override val route = "About"
}

/** Куда писать и куда заходить — единственные четыре адреса, которые знает приложение. */
private const val SUPPORT_EMAIL = "s.zaroslikov@yandex.ru"
// Группа и Telegram — `internal`: их же дают приветствие стартового экрана и последняя
// страница «Как пользоваться» (`ui/components/SocialLinks.kt`).
internal const val VK_GROUP_URL = "https://vk.com/myfermaapp"
private const val VK_CHANNEL_URL = "https://vk.ru/im/channels/-239980765"
internal const val TELEGRAM_URL = "https://t.me/my_ferma_app"

/** Как группа пишется в тексте: без схемы, как её произносят. */
internal const val VK_GROUP_LABEL = "vk.com/myfermaapp"

/** Telegram-канал, в отличие от ВК-канала, адрес имеет произносимый — его и показываем. */
internal const val TELEGRAM_LABEL = "t.me/my_ferma_app"

/**
 * У канала адреса, который можно произнести, нет: «vk.ru/im/channels/-239980765» никто
 * не перепишет с экрана и не запомнит. Поэтому в строке стоит не он, а то, что за ним.
 */
private const val VK_CHANNEL_LABEL = "Новости и обновления приложения"

/**
 * «Моё хозяйство» — трекинговые ссылки AppMetrica, а не адреса страниц в магазинах: переход
 * засчитывается в кабинете «Моего хозяйства», и видно, из какого магазина он пришёл.
 *
 * Обе ссылки показываются в любой сборке: магазин, из которого поставили «Инкубатор», не
 * обязан быть тем, которым человек пользуется, — пусть выбирает сам. Прямой адрес RuStore
 * (`FarmApp.STORE_URL`) остаётся за кнопкой «Обновить» — там речь о приложении, которое
 * уже стоит.
 */
private const val FARM_APP_URL_RUSTORE =
    "https://4625329.redirect.appmetrica.yandex.com?appmetrica_tracking_id=1110909310214390884&referrer=reattribution%3D1"
private const val FARM_APP_URL_GOOGLE_PLAY =
    "https://4625329.redirect.appmetrica.yandex.com?appmetrica_tracking_id=102102982392332880&referrer=reattribution%3D1"

private const val DEVELOPER = "Заросликов Семён Николаевич"

/**
 * «О приложении»: что это за версия, кто её сделал и как до него достучаться.
 *
 * Иконка спрашивается у системы, а не рисуется своей копией: смысл строки «текущая
 * иконка приложения» ровно в том, чтобы человек узнал ту, по которой он сюда и нажал,
 * а копия рано или поздно от неё отстала бы. Подробности — в [AppIcon], там же причина,
 * по которой `painterResource` для этого не годится.
 *
 * Версия и дата выпуска приходят из `BuildConfig`, а не из строк в коде. Версию туда
 * кладёт Gradle сам, дату — `buildConfigField` рядом с `versionName`; обе живут в одном
 * месте, и обновить одну, забыв другую, теперь стоит усилий.
 *
 * Просьба писать об идеях и ошибках стоит выше контактов намеренно: контакты без
 * просьбы — справочные сведения, которыми никто не пользуется, а просьба без контактов
 * — вежливость без адреса.
 */
@Composable
fun AboutScreen(
    navigateBack: () -> Unit,
    navigateToGuide: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: AboutViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    MenuScreen(
        title = "О приложении",
        subtitle = "Версия, разработчик и обратная связь",
        navigateBack = navigateBack,
        contentPadding = contentPadding,
    ) {
        AppCard()

        Spacer(Modifier.height(16.dp))
        GuideCard(onOpen = navigateToGuide)

        // Карточки магазина нет в сборке не для RuStore: обе её строки ведут в магазин,
        // из которого это приложение не ставили. См. IS_RUSTORE_BUILD.
        if (IS_RUSTORE_BUILD) {
            Spacer(Modifier.height(16.dp))
            StoreCard(
                checking = state.checkingUpdate,
                outcome = state.updateOutcome,
                onRate = { viewModel.onIntent(AboutIntent.Rate) },
                onCheckUpdate = { viewModel.onIntent(AboutIntent.CheckUpdate) },
            )
        }

        Spacer(Modifier.height(16.dp))
        FeedbackCard(
            onWrite = {
                Analytics.report(Events.MAIL_TO_DEVELOPER)
                sendEmail(context)
            },
            onJoin = {
                Analytics.report(Events.OPEN_VK_GROUP)
                openUrl(context, VK_GROUP_URL)
            },
        )

        Spacer(Modifier.height(16.dp))
        ContactsCard(
            onEmail = { sendEmail(context) },
            onVk = { openUrl(context, VK_GROUP_URL) },
            onChannel = {
                Analytics.report(Events.OPEN_TG_CHANNEL)
                openUrl(context, VK_CHANNEL_URL)
            },
            onTelegram = {
                Analytics.report(Events.OPEN_TELEGRAM)
                openUrl(context, TELEGRAM_URL)
            },
        )

        Spacer(Modifier.height(16.dp))
        OtherAppsCard(
            onFarm = { store, url ->
                Analytics.report(Events.OPEN_FARM_APP_LINK, mapOf("Магазин" to store))
                openUrl(context, url)
            },
        )

        // Реклама — последняя карточка, после рекомендуемых приложений.
        AdBanner(rememberBannerAdHost(), Modifier.padding(top = 16.dp))
    }
}

/** Иконка, название, версия, дата выпуска и разработчик — паспорт сборки. */
@Composable
private fun AppCard() {
    TabCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DesignPalette.Surface),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon()
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = "Инкубатор",
                    style = DesignType.CardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Учёт инкубации птицы",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        MenuFactRow("Версия", BuildConfig.VERSION_NAME)
        Spacer(Modifier.height(12.dp))
        MenuFactRow("Дата выпуска", BuildConfig.RELEASE_DATE)
        Spacer(Modifier.height(12.dp))
        MenuFactRow("Разработчик", DEVELOPER)
    }
}

/**
 * Иконка приложения — та самая, что стоит на рабочем столе.
 *
 * Берётся у системы через `getApplicationIcon`, а **не** через
 * `painterResource(R.mipmap.ic_launcher)`, и это не вкусовщина: при `minSdk = 26` этот
 * идентификатор всегда разрешается в `mipmap-anydpi-v26/ic_launcher.xml`, а там лежит
 * `<adaptive-icon>`. Compose разбирает всякий `.xml` как векторный ресурс и на первом же
 * теге, который не `<vector>`, бросает `IllegalArgumentException` — то есть экран падал
 * бы при открытии, и на каждом устройстве одинаково.
 *
 * У системы же спрашивается ровно то, что нарисовано на рабочем столе: она сама сложит
 * фон с передним планом и обрежет по маске прошивки. Это заодно буквальнее исполняет
 * «текущая иконка приложения» — узнаётся именно та, по которой человек сюда нажал.
 */
@Composable
private fun AppIcon() {
    val context = LocalContext.current
    // Ключ — имя пакета: в пределах установки иконка не меняется, и пересобирать
    // растр на каждой перерисовке незачем.
    val icon = remember(context.packageName) {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(width = IconPx, height = IconPx)
            .asImageBitmap()
    }
    Image(
        bitmap = icon,
        contentDescription = "Иконка приложения",
        modifier = Modifier.size(64.dp),
    )
}

/**
 * Сторона растра иконки в пикселях.
 *
 * Число, а не 64 dp, пересчитанные по плотности: `toBitmap` принимает пиксели, и
 * 192 — стандартный xxxhdpi-размер лаунчер-иконки, которого хватает и на самых плотных
 * экранах. Меньше — иконка была бы мыльной, больше — впустую.
 */
private const val IconPx = 192

/**
 * Дверь обратно в инструкцию первого запуска.
 *
 * Инструкция показывается один раз, и это правильно — но «а как тут листать» спрашивают
 * и через месяц, и обновившиеся с прежних версий её вовсе не видели. Одна строка, а не
 * карточка с текстом: пересказ инструкции рядом с кнопкой в неё был бы второй, худшей
 * инструкцией. Стоит сразу под паспортом приложения, до просьбы написать: сначала
 * человек узнаёт, как приложением пользоваться, потом — куда писать, если не вышло.
 */
@Composable
private fun GuideCard(onOpen: () -> Unit) {
    TabCard {
        MenuRow(
            title = "Как пользоваться",
            description = "Инкубатор, закладка, замеры — и какие экраны листаются",
            onClick = onOpen,
        )
    }
}

/**
 * Две вещи, которые приложение умеет через RuStore: оценка и проверка обновления.
 *
 * Стоят вместе и сразу под инструкцией не случайно. Обе — разговор не с разработчиком,
 * а с магазином, и обе отвечают на вопрос «а дальше что»: одна даёт сказать, как оно,
 * вторая — узнать, нет ли версии новее. Просьба написать об ошибке ниже: она про
 * конкретную беду, а эти две — про приложение целиком.
 *
 * **Проверка отвечает строкой под собой, а не всплывающим сообщением.** Ответов у неё
 * три, и два из них — «у вас последняя версия» и «RuStore на телефоне нет» — это то, что
 * человек захочет перечитать: всплывающее сообщение он в половине случаев не успеет
 * дочитать и нажмёт ещё раз. Найденное обновление говорит о себе само — карточкой внизу
 * экрана, той же, что и при запуске, — поэтому здесь про него сказано коротко.
 */
@Composable
private fun StoreCard(
    checking: Boolean,
    outcome: UpdateCheckOutcome?,
    onRate: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    TabCard {
        MenuRow(
            title = "Оценить приложение",
            description = "Окно оценки RuStore — не выходя из приложения",
            onClick = onRate,
        )

        MenuRowDivider()

        MenuRow(
            title = "Проверить обновление",
            description = when {
                checking -> "Спрашиваем RuStore…"
                outcome == UpdateCheckOutcome.Offered -> "Новая версия есть — карточка внизу экрана"
                outcome == UpdateCheckOutcome.UpToDate -> "У вас последняя версия"
                outcome == UpdateCheckOutcome.Unavailable -> "RuStore не отвечает — проверьте вручную в магазине"
                else -> "Спросить RuStore, нет ли версии новее"
            },
            onClick = onCheckUpdate,
        )
    }
}

/**
 * Другое приложение того же автора — «Моё хозяйство», учёт фермы целиком.
 *
 * Отдельная карточка, а не строка в «Контактах»: там адреса, по которым пишут
 * разработчику, а это приложение, которое ставят. Птенцы из поздравления с выводом
 * уходят именно туда, так что тому, кто его ещё не поставил, нужно знать, где его взять.
 */
@Composable
private fun OtherAppsCard(onFarm: (store: String, url: String) -> Unit) {
    TabCard {
        CardHeader(
            title = "Рекомендуемые приложения",
            subtitle = "Моё хозяйство — учёт животных, кормов и доходов фермы",
        )
        Spacer(Modifier.height(16.dp))

        MenuRow(
            title = "Скачать в RuStore",
            onClick = { onFarm("RuStore", FARM_APP_URL_RUSTORE) },
            trailing = { MenuIcon(R.drawable.ic_rustore) },
        )

        MenuRowDivider()

        MenuRow(
            title = "Скачать в Google Play",
            onClick = { onFarm("Google Play", FARM_APP_URL_GOOGLE_PLAY) },
            trailing = { MenuIcon(R.drawable.ic_google) },
        )
    }
}

/**
 * Просьба рассказывать об идеях и ошибках — с двумя кнопками, куда именно.
 *
 * Кнопки идут по убыванию разговора: письмо — один на один с разработчиком, группа —
 * птицеводы между собой. Кнопки канала здесь нет: канал — объявления в одну сторону,
 * он ничего не спрашивает и ответа не ждёт, а карточка просит рассказать. Адрес канала
 * остался строкой в «Контактах» ниже — вместе с событием «Переход в канал».
 */
@Composable
private fun FeedbackCard(onWrite: () -> Unit, onJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.InsightSurface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Расскажите, чего не хватает",
                style = DesignType.SectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Приложение растёт из того, что просят птицеводы. Если чего-то " +
                    "не хватает, что-то считается не так или приложение просто ошиблось — " +
                    "напишите. Каждое письмо читается, и почти каждая версия сделана " +
                    "по чьей-то просьбе.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onWrite,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = DesignPalette.OnAccent,
                ),
            ) {
                Text(text = "Написать об идее или ошибке", style = DesignType.ButtonLabel)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Surface,
                    contentColor = DesignPalette.Accent,
                ),
            ) {
                Text(text = "Группа ВКонтакте", style = DesignType.ButtonLabel)
            }
        }
    }
}

/**
 * Все четыре адреса строками.
 *
 * Почта и группа дублируют кнопки выше не по недосмотру: кнопка ведёт в почтовое
 * приложение, а адрес нужен и тем, кто пишет с другого устройства, — увидеть его
 * глазами больше негде. Каналы кнопок не имеют, и эти строки — единственный путь к ним.
 */
@Composable
private fun ContactsCard(
    onEmail: () -> Unit,
    onVk: () -> Unit,
    onChannel: () -> Unit,
    onTelegram: () -> Unit,
) {
    TabCard {
        CardHeader(
            title = "Контакты",
            subtitle = "Почта, группа и каналы — все ведут к разработчику",
        )
        Spacer(Modifier.height(16.dp))

        MenuRow(
            title = "Почта",
            description = SUPPORT_EMAIL,
            onClick = onEmail,
            trailing = { MenuIcon(R.drawable.baseline_edit_note_24) },
        )

        MenuRowDivider()

        MenuRow(
            title = "Группа ВКонтакте",
            description = VK_GROUP_LABEL,
            onClick = onVk,
            trailing = { MenuIcon(R.drawable.baseline_cottage_24) },
        )

        MenuRowDivider()

        MenuRow(
            title = "Канал ВКонтакте",
            description = VK_CHANNEL_LABEL,
            onClick = onChannel,
            trailing = { MenuIcon(R.drawable.baseline_campaign_24) },
        )

        MenuRowDivider()

        MenuRow(
            title = "Канал в Telegram",
            description = TELEGRAM_LABEL,
            onClick = onTelegram,
            trailing = { MenuIcon(R.drawable.baseline_send_24) },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            // Обещание сужено до того, за что можно ручаться. «Приложение работает без
            // интернета: ничего никуда не отправляется» — так здесь стояло, и это
            // неправда: AppMetrica инициализируется в `InventoryApplication` и шлёт
            // события. Разработчик действительно не видит инкубаторов и закладок, и
            // именно это карточке контактов и нужно сказать.
            text = "Инкубаторы и закладки остаются на телефоне: разработчик их не " +
                "видит, пока вы сами не пришлёте.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Открывает почтовое приложение с уже заполненными адресом и темой.
 *
 * Тема несёт версию: письмо «не работает» без неё стоит ещё одного письма с вопросом,
 * какая версия. `ACTION_SENDTO` с `mailto:`, а не `ACTION_SEND`: первый видят только
 * почтовые приложения, второй — ещё и мессенджеры, и выбор из десятка приложений на
 * кнопке «написать письмо» сбивает с толку.
 */
private fun sendEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$SUPPORT_EMAIL".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "Инкубатор ${BuildConfig.VERSION_NAME} — идея или ошибка")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Почтового приложения нет — адрес всё равно виден строкой выше.
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        // Открыть нечем — на экране остаётся сам адрес.
    }
}
