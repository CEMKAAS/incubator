package ru.zaroslikov.incubator

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.zaroslikov.incubator.ads.AdLoadingNotice
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.ads.shouldMuteAppOpenAd
import ru.zaroslikov.incubator.rustore.AppUpdateNotice
import ru.zaroslikov.incubator.rustore.IS_RUSTORE_BUILD
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.batch.AiringTimerFab
import ru.zaroslikov.incubator.design.theme.IncubatorTheme
import ru.zaroslikov.incubator.design.theme.ThemeMode
import ru.zaroslikov.incubator.design.theme.backgroundDark
import ru.zaroslikov.incubator.design.theme.backgroundLight
import ru.zaroslikov.incubator.design.theme.isDark
import ru.zaroslikov.incubator.qr.QrLink
import ru.zaroslikov.incubator.airing.EXTRA_AIRING_TIMER
import ru.zaroslikov.incubator.work.EXTRA_BATCH_ID
import ru.zaroslikov.incubator.work.EXTRA_INCUBATOR_ID


class MainActivity : ComponentActivity() {

    companion object {
        private var TAG = "MainActivity"
        const val REQUEST_CODE_NOTIFICATION_PERMISSIONS = 228
    }

    /**
     * Куда приложение открыто — к закладке по напоминанию или к инкубатору по QR-коду —
     * и порядковый номер этого прихода.
     *
     * Состояние Compose, а не просто поле: намерение может прийти и в работающее
     * приложение — камера телефона открывает ссылку из QR-кода через [onNewIntent], —
     * и композиция должна об этом узнать. Номер растёт только там: `onCreate` нового
     * экземпляра активности (поворот) начинает с нуля, а граф навигации помнит, какой
     * номер уже обработан, в `rememberSaveable`, — так поворот не повторяет переход, а
     * новый код в работающем приложении его получает. См. `InventoryNavHost`.
     */
    private var launchTarget by mutableStateOf<LaunchTarget?>(null)
    private var launchSerial by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Расписание напоминаний собирается заново по базе при каждом открытии
        // приложения — так уведомления переживают перенос базы между телефонами.
        // Почему именно здесь, а не в Application.onCreate, — см. ReminderSync.
        (application as? InventoryApplication)?.container?.reminderSync?.syncInBackground()

        // Атрибуты профиля AppMetrica — то, чем режутся когорты и удержание. Считаются
        // по базе, а не копятся событиями, и поэтому пересчитываются при каждом
        // открытии; почему именно здесь, а не в Application.onCreate, — см.
        // AnalyticsProfile: там же, где и напоминания, процесс поднимает и WorkManager.
        (application as? InventoryApplication)?.container?.analyticsProfile?.syncInBackground()

        // Таймер проветривания сверяется с часами: срок мог выйти, пока процесс был
        // мёртв, а бегущему таймеру без службы служба возвращается. См. AiringTimerController.
        (application as? InventoryApplication)?.container?.airingTimer?.refresh()

        val settings = (application as InventoryApplication).container.appSettings

        // Уведомление ведёт не «в приложение», а в закладку, о которой напомнили.
        // Намерение приходит с FLAG_ACTIVITY_CLEAR_TASK, то есть всегда через onCreate:
        // задача очищается, и активность создаётся заново — в onNewIntent оно не бывает.
        // Ссылка из QR-кода приходит тем же путём, намерением VIEW, — и ведёт не к закладке,
        // а к «Замерам за сегодня» инкубатора; см. [LaunchTarget].
        val target = LaunchTarget.from(intent)
        launchTarget = target

        // Инструкцию видит каждый, кто её ещё не закрыл, — и поставивший приложение
        // впервые, и обновившийся (`guidePending` поднят по умолчанию). Разница между
        // ними ровно одна и наступает после инструкции: новому сразу открывается форма
        // инкубатора, потому что без устройства работать не с чем, а у обновившегося
        // инкубаторы уже есть, и он попадает прямо на главный экран. Настоящий первый
        // запуск отличает `isFirstLaunch`, и его короткого флага для этого мало — он
        // гаснет при первом же чтении, а инструкция стоит между ним и формой, поэтому
        // намерение переезжает в `addIncubatorPending`, живущий до конца инструкции.
        //
        // Пришедшего по уведомлению инструкция не встречает: он нажал на конкретную
        // закладку, и экран поверх неё был бы задержкой ни о чём. Флаг остаётся поднятым
        // — инструкция дождётся обычного запуска.
        if (isFirstLaunch(this)) settings.addIncubatorPending = true
        val showGuide = settings.guidePending && target == null
        // Форма инкубатора — продолжение инструкции, а не самостоятельное событие: без
        // неё она не показывается, и флаг тут же гаснет, чтобы не ждать инструкции,
        // которой уже не будет.
        val openAddIncubator = showGuide && settings.addIncubatorPending
        if (!showGuide) settings.addIncubatorPending = false

        // Реклама при запуске молчит на первом запуске и на первом запуске после
        // обновления — и пока на экране инструкция, которая и есть тот первый запуск.
        // Решение принимается здесь, до `onStart`, потому что контроллер показывает
        // рекламу по выходу процесса на передний план, а он наступает следом за этим
        // методом. Молчание — на весь процесс, см. `AppOpenAdController.mute`. Код
        // версии записывается сразу: следующий запуск этой версии — уже не первый.
        val ads = (application as InventoryApplication).container.appOpenAds
        val mute = shouldMuteAppOpenAd(
            seenVersion = settings.adsVersionCode,
            currentVersion = BuildConfig.VERSION_CODE,
            guideShowing = showGuide,
        )
        if (mute) ads.mute()
        settings.adsVersionCode = BuildConfig.VERSION_CODE

        // Обновление через RuStore спрашивается один раз за процесс — этот метод
        // случается и на каждом повороте экрана, а проверка ходит в магазин (флаг
        // внутри контроллера). И не спрашивается вовсе на первом запуске: человеку,
        // который только что поставил приложение и читает инструкцию, предлагать его
        // обновить — значит сказать, что он поставил не то. Проверка ничего не
        // показывает, пока RuStore не ответит, и молчит совсем, если его на телефоне
        // нет; карточка живёт ниже, в корне композиции.
        // И не спрашивается вовсе в сборке не для RuStore: обновление приедет оттуда,
        // откуда приложение взяли, а карточка внизу экрана повела бы в магазин.
        val appUpdate = (application as InventoryApplication).container.appUpdate
        if (!showGuide && IS_RUSTORE_BUILD) appUpdate.check()

        // Разрешение на уведомления спрашивается после инструкции, в `onGuideFinished`,
        // а не поверх первого кадра: системный вопрос до того, как человек увидел хоть
        // что-то, читается как вопрос ни о чём.

        // Тема — выбор из «Настроек», и приложение перекрашивается в момент выбора, а
        // не при следующем запуске: подписка на поток вместо однократного чтения.
        // Начальное значение читается синхронно, чтобы первый же кадр был нужного цвета
        // — иначе тёмная тема вспыхивала бы светлым при каждом старте.
        paintWindowForTheme(settings.themeMode)

        setContent {
            // Поток запоминается: пересобранный на каждой рекомпозиции, он заново
            // подписывался бы на настройки при каждом кадре корня.
            val themeFlow = remember { settings.themeModeFlow() }
            val themeMode by themeFlow.collectAsState(initial = settings.themeMode)
            // Единицы измерения — тем же путём, что и тема: выбор в «Настройках»
            // перерисовывает градусы и знак валюты сразу, а не при следующем запуске.
            val unitsFlow = remember { settings.unitsFlow() }
            val units by unitsFlow.collectAsState(initial = settings.units)
            IncubatorTheme(darkTheme = themeMode.isDark()) {
                // Заставка «сейчас будет реклама» лежит поверх всего приложения и на
                // время ожидания держит экран — см. AdLoadingNotice. В боксе, а не
                // просто следом: два узла подряд в теме опирались бы на то, как их
                // сложит корневая раскладка, а здесь порядок важен.
                val waitingForAd by ads.waitingForAd.collectAsState()
                val updateState by appUpdate.state.collectAsState()
                CompositionLocalProvider(LocalUnits provides units) {
                    Box(Modifier.fillMaxSize()) {
                        InventoryApp(
                            showGuide = showGuide,
                            openAddIncubator = openAddIncubator,
                            launchTarget = launchTarget,
                            launchSerial = launchSerial,
                            onGuideFinished = {
                                settings.guideFinished()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    getNotificationPermissions()
                                }
                            },
                        )
                        // Идущий таймер проветривания — кольцом в углу поверх всех экранов,
                        // кроме формы, где он поставлен. Нажатие ведёт в ту форму тем же
                        // путём, что и уведомление таймера, — целью запуска с новым номером.
                        AiringTimerFab(
                            onOpen = { target ->
                                launchTarget = TimerTarget(target.incubatorId, target.batchId)
                                launchSerial += 1
                            },
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                        // Карточка обновления лежит над приложением, но под заставкой
                        // рекламы: та держит экран целиком, и предлагать что-либо поверх
                        // неё было бы предложением, которого не видно.
                        // В сборке не для RuStore проверка не запускается вовсе, так что
                        // состояние осталось бы пустым и так; условие стоит рядом с самой
                        // карточкой, чтобы её нельзя было оживить, забыв про эту сборку.
                        if (IS_RUSTORE_BUILD) {
                            AppUpdateNotice(
                                state = updateState,
                                onDownload = appUpdate::download,
                                onInstall = appUpdate::install,
                                onDismiss = appUpdate::dismiss,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                        AdLoadingNotice(visible = waitingForAd)
                    }
                }
            }
        }
    }


    /**
     * Фон окна под выбранную тему — до `setContent`, для Android младше 12.
     *
     * Там у приложения нет своего ночного режима (см. `AppSettings.syncNightMode`), и
     * окно красится по `values` / `values-night` — то есть по теме телефона. Человек,
     * выбравший «Тёмная» на дневном телефоне, видел бы кремовое окно между стартовым
     * кадром и первым кадром Compose. На Android 12 и новее это делает система сама,
     * и здесь ничего не трогаем: фон из ресурсов уже правильный.
     *
     * Только явный выбор: при «Системная» ресурсы и так согласны с телефоном.
     */
    private fun paintWindowForTheme(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val color = when (mode) {
            ThemeMode.SYSTEM -> return
            ThemeMode.LIGHT -> backgroundLight
            ThemeMode.DARK -> backgroundDark
        }
        window.setBackgroundDrawable(ColorDrawable(color.toArgb()))
    }

    /**
     * Спрашивает разрешение на уведомления — один раз, и только `POST_NOTIFICATIONS`.
     *
     * Раньше вместе с ним запрашивался `ACCESS_NOTIFICATION_POLICY`, а отказ в ответе
     * вызывал запрос заново. Первое — не runtime-разрешение вовсе (его выдаёт только
     * системный экран «Не беспокоить»), так что через `requestPermissions` оно не
     * выдавалось никогда; второе превращало это в бесконечную петлю «спросил — отказали
     * — спросил». Теперь ответ только записывается в лог: «нет» — это ответ, и настройки
     * экран «Настройки» покажет, где его поменять.
     */
    private fun getNotificationPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.d(TAG, "Notification Permissions : previously granted successfully")
                return
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSIONS
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Намерение в уже работающее приложение — ссылка из QR-кода, открытая камерой
     * телефона, пока приложение стояло в фоне (`launchMode="singleTop"` в манифесте).
     *
     * `setIntent` обязателен: поворот экрана пересоздаёт активность с `getIntent()`, и
     * без него она вернулась бы к намерению первого запуска. Намерение без цели — обычный
     * возврат на передний план — ничего не меняет: иначе оно сбрасывало бы цель, к
     * которой граф, возможно, ещё не перешёл.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = LaunchTarget.from(intent) ?: return
        launchTarget = target
        launchSerial += 1
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_NOTIFICATION_PERMISSIONS) return
        // Прерванный диалог (поворот, касание мимо) приходит с пустыми массивами —
        // индекс без проверки здесь падал.
        if (grantResults.isEmpty()) return
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Notification Permissions : " + if (granted) "granted" else "denied")
        Analytics.report(Events.NOTIFICATIONS_PERMISSION, mapOf("Разрешено" to granted))
    }

}


/**
 * Куда приложение открыто снаружи — по нажатию на напоминание или по QR-коду инкубатора.
 *
 * Разбирается один раз, в [MainActivity.onCreate] (и в [MainActivity.onNewIntent], когда
 * ссылка приходит в работающее приложение), и дальше едет обычным параметром: `Intent`
 * живёт у активности, а навигация — у Compose, и таскать первый по второму значило бы
 * объяснять каждому экрану, что бывают намерения. Два варианта, потому что два входа
 * ведут в разные места: напоминание — в шторку закладки, код — в «Замеры за сегодня»
 * инкубатора.
 */
sealed interface LaunchTarget {
    val incubatorId: Long

    companion object {
        /**
         * Таймер проверяется первым: его намерение несёт те же extras, что и напоминание,
         * плюс свою отметку. Напоминание — вторым: у его намерения нет `data`, у ссылки —
         * нет extras.
         */
        fun from(intent: Intent?): LaunchTarget? =
            TimerTarget.from(intent) ?: ReminderTarget.from(intent) ?: QrTarget.from(intent)
    }
}

/**
 * Куда ведёт уведомление таймера проветривания: в форму, из которой его поставили.
 *
 * [batchId] отличен от нуля — это шторка закладки, тот же маршрут, что у напоминания;
 * ноль — «Замеры за сегодня» инкубатора, тот же маршрут, что у QR-кода. Отдельный
 * вариант, а не [ReminderTarget] с флагом, потому что и ведёт он по-другому (стек
 * срезается до инкубатора, как по коду: приложение, скорее всего, уже открыто), и
 * считается отдельно — «Переход по уведомлению» остаётся откликом на напоминания.
 */
data class TimerTarget(override val incubatorId: Long, val batchId: Long) : LaunchTarget {
    companion object {
        fun from(intent: Intent?): TimerTarget? {
            if (intent?.getBooleanExtra(EXTRA_AIRING_TIMER, false) != true) return null
            val incubatorId = intent.getLongExtra(EXTRA_INCUBATOR_ID, 0L)
            val batchId = intent.getLongExtra(EXTRA_BATCH_ID, 0L)
            return if (incubatorId != 0L) TimerTarget(incubatorId, batchId) else null
        }
    }
}

/**
 * Куда ведёт нажатие по напоминанию: инкубатор и закладка внутри него.
 *
 * Оба числа нужны сразу, и это не избыточность. Шторка закладки открывается **поверх**
 * экрана инкубатора — она и есть его часть, отдельного маршрута у неё нет, — поэтому
 * дойти до закладки, не зная её инкубатора, некуда.
 */
data class ReminderTarget(override val incubatorId: Long, val batchId: Long) : LaunchTarget {
    companion object {
        fun from(intent: Intent?): ReminderTarget? {
            val incubatorId = intent?.getLongExtra(EXTRA_INCUBATOR_ID, 0L) ?: 0L
            val batchId = intent?.getLongExtra(EXTRA_BATCH_ID, 0L) ?: 0L
            // Инкубатора нет — вести некуда; закладка без него не адресуется.
            return if (incubatorId != 0L) ReminderTarget(incubatorId, batchId) else null
        }
    }
}

/**
 * Куда ведёт QR-код инкубатора: к его «Замерам за сегодня».
 *
 * Приходит намерением `VIEW` со ссылкой `incubator://measure/<id>` — от камеры телефона,
 * от браузера, от любого сканера, который умеет открывать ссылки. Разбор самой ссылки —
 * [QrLink.parse], тот же, что у сканера внутри приложения: два разбора одной ссылки
 * разошлись бы на первом же особом случае. Есть ли такой инкубатор, здесь не проверяется
 * — это узнает экран инкубатора, которому и отвечать «не найден».
 */
data class QrTarget(override val incubatorId: Long) : LaunchTarget {
    companion object {
        fun from(intent: Intent?): QrTarget? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            val id = QrLink.parse(intent.dataString) ?: return null
            return QrTarget(id)
        }
    }
}

// Функция для проверки первого запуска приложения
fun isFirstLaunch(context: Context): Boolean {
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val isFirstLaunch = sharedPreferences.getBoolean("is_first_launch", true)

    if (isFirstLaunch) sharedPreferences.edit().putBoolean("is_first_launch", false).apply()

    return isFirstLaunch
}
