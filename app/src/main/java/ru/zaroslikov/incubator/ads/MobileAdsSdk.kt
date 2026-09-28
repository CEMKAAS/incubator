package ru.zaroslikov.incubator.ads

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.yandex.mobile.ads.common.AdTheme
import com.yandex.mobile.ads.common.YandexAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.design.theme.ThemeMode

/**
 * Единственная дверь в Yandex Mobile Ads из приложения.
 *
 * Пакет `ads/` устроен так же, как `analytics/`: всё, что импортирует
 * `com.yandex.mobile.ads`, лежит здесь, и `grep -rn "com.yandex.mobile.ads" app/src/main`
 * должен находить только этот пакет. Экранам от рекламы нужно ровно два предмета —
 * баннер-карточка ([AdBanner]) и молчание при первом запуске ([AppOpenAdController]),
 * и ни тому ни другому незачем знать, чей это SDK.
 *
 * Инициализация — один раз на процесс и по первому требованию, а не в
 * `Application.onCreate`: тот случается и в процессе, который WorkManager поднимает ради
 * напоминания, а там ни баннеров, ни экрана нет, и поднимать ради него рекламный SDK с
 * его сетью — значит тратить батарею на показ, которого не будет. Первым требует либо
 * выход приложения на передний план (реклама при запуске), либо первый баннер на экране.
 *
 * Все вызовы — с главного потока: и `YandexAds.initialize`, и его ответ приходят на нём,
 * а списку ожидающих замок был бы нужен только для ошибки, которой [whenReady] не даёт
 * совершить.
 *
 * Согласие на персонализацию (`YandexAds.setUserConsent`) не спрашивается и не
 * выставляется: приложение распространяется в России, где отдельного диалога согласия
 * рекламная сеть не требует, а собственного экрана согласия у приложения нет. Появится
 * распространение в ЕЭЗ — понадобится и диалог, и вызов до `initialize`.
 */
object MobileAdsSdk {

    /**
     * Идентификаторы рекламных блоков — те же, что стояли в прежней версии приложения.
     * Другие завели бы новую статистику в кабинете и оборвали бы старую.
     */
    const val BANNER_UNIT_ID = "R-M-12856457-1"
    const val APP_OPEN_UNIT_ID = "R-M-12856457-2"

    private val readyState = MutableStateFlow(false)
    private var started = false
    private val waiting = mutableListOf<() -> Unit>()

    /** Закончил ли SDK инициализацию — для Compose, который ждёт его потоком. */
    val ready: StateFlow<Boolean> get() = readyState

    /**
     * Запускает инициализацию, если она ещё не начиналась. Повторные вызовы ничего не
     * делают: за то, что SDK поднимается один раз, отвечает этот объект, а не тот, кто
     * зовёт.
     */
    fun start(context: Context) {
        // Не с главного потока — перенести туда, а не падать: реклама не та подсистема,
        // ради которой стоит ронять приложение.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { start(context) }
            return
        }
        if (started) return
        started = true
        // То же решение, что у AppMetrica (`withLocationTracking(false)`): приложение
        // офлайновое и о месте не спрашивает, рекламе координаты тоже не нужны.
        // Разрешений на геолокацию в манифесте нет, так что это не запрет, а закреплённое
        // намерение — чтобы одно добавленное когда-нибудь разрешение не открыло его молча.
        // Ставится до `initialize`: после него SDK читает флаги уже на свой лад.
        YandexAds.setLocationTracking(false)
        // Подробный лог SDK — только в отладочной сборке: без него «рекламы нет» и
        // «рекламы нет из-за отсутствия заполнения» выглядят одинаково.
        YandexAds.enableLogging(BuildConfig.DEBUG)
        YandexAds.initialize(context.applicationContext) {
            readyState.value = true
            val actions = waiting.toList()
            waiting.clear()
            actions.forEach { it() }
        }
    }

    /**
     * Выполняет [action] сразу, если SDK готов, иначе — как только он будет готов.
     * Запускает инициализацию, если её ещё не было.
     */
    fun whenReady(context: Context, action: () -> Unit) {
        start(context)
        if (readyState.value) action() else waiting += action
    }

    /**
     * Тема объявления под тему приложения.
     *
     * SDK умеет рисовать объявление светлым или тёмным, и белая карточка рекламы
     * посреди тёмного экрана была бы единственным, что не переключилось вместе с
     * «Оформлением». «Системная» читается из конфигурации приложения: с Android 12
     * `AppSettings.syncNightMode` держит её в согласии с выбором человека, а ниже
     * системная тема и есть тема телефона.
     */
    fun adThemeFor(context: Context, mode: ThemeMode): AdTheme = when (mode) {
        ThemeMode.LIGHT -> AdTheme.LIGHT
        ThemeMode.DARK -> AdTheme.DARK
        ThemeMode.SYSTEM -> {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            if (night == Configuration.UI_MODE_NIGHT_YES) AdTheme.DARK else AdTheme.LIGHT
        }
    }
}
