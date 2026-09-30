package ru.zaroslikov.incubator.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.yandex.mobile.ads.appopenad.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.settings.AppSettings

/**
 * Реклама при запуске приложения — полноэкранная, по выходу на передний план.
 *
 * Живёт в контейнере (`AppDataContainer.appOpenAds`), а не в `MainActivity`, потому
 * что следит за **процессом**, а не за активностью: `ProcessLifecycleOwner` даёт
 * `ON_START` при холодном старте и при каждом возвращении из фона — и не даёт его на
 * поворот экрана, где `Activity.onStart` случается заново и показал бы рекламу поверх
 * только что повёрнутого экрана. Активность здесь нужна ровно на одно —
 * [AppOpenAd.show] требует её, — и отслеживается через `ActivityLifecycleCallbacks`,
 * причём только [hostActivity]: экран самой рекламы — тоже активность этого процесса,
 * и без фильтра рекламу показывали бы поверх рекламы.
 *
 * Два входа и один выключатель:
 *
 * - **Холодный старт** — рекламы ещё нет; на время загрузки поднимается заставка
 *   ([waitingForAd], [AdLoadingNotice]), и объявление показывается, как только пришло.
 *   Заставка тут не украшение, а сама граница «при запуске»: пока она висит, человек
 *   предупреждён и ждёт, а как только она ушла по сроку ([NOTICE_TIMEOUT_MS]), реклама
 *   на этом запуске уже не покажется — опоздавшая остаётся лежать до следующего выхода
 *   на передний план. Она же держит экран, поэтому отдельная проверка «а не начал ли
 *   человек работать» не нужна: работать под заставкой не с чем.
 * - **Возврат из фона** — показывается, если уже загружена и не протухла
 *   ([AD_TTL_MS]: полноэкранное объявление живёт часы, и показ вчерашнего упёрся бы в
 *   отказ — слот сгорел, человек не увидел ничего); иначе только заказывается на
 *   следующий раз. Заставки здесь нет намеренно: возврат из фона случается по десятку
 *   раз на дню, и ждать перед ней каждый раз человек не подписывался.
 * - **[mute]** — молчание на весь процесс: первый запуск и первый запуск после
 *   обновления (см. [shouldMuteAppOpenAd]). На весь процесс, а не на один показ, потому
 *   что «первый запуск» для человека — это пока он не закрыл приложение, а не пока не
 *   свернул его на секунду ответить на сообщение. Замолчавший контроллер и не грузит
 *   ничего: SDK ради показа, которого не будет, поднимать незачем.
 *
 * **Заказ на показ живёт ровно до ухода в фон.** [showPending] поднимают `ON_START` и
 * приход объявления, а гасит — помимо самого показа — `ON_STOP`: заказ, переживший
 * сворачивание, срабатывал бы в произвольный момент (первая версия показывала второе
 * объявление сразу вслед за первым: «Домой» поверх открытой рекламы поднимал заказ,
 * закрытие рекламы грузило следующую, и та выходила без всякого выхода на передний
 * план). Пока объявление на экране ([showing]), `ON_START` тоже ничего не заказывает —
 * возврат к рекламе не есть возврат в приложение.
 *
 * После показа — и после неудачного тоже — реклама заказывается заново, чтобы к
 * следующему возврату уже лежала готовой. Все вызовы с главного потока: и жизненный
 * цикл, и ответы SDK приходят на нём.
 */
class AppOpenAdController(
    private val application: Application,
    private val settings: AppSettings,
    private val hostActivity: Class<out Activity>,
) : DefaultLifecycleObserver {

    private var ad: AppOpenAd? = null
    private var loadedAt = 0L
    private var loader: AppOpenAdLoader? = null
    private var loading = false
    private var muted = false
    private var coldStart = true
    /** Показать сразу по загрузке — только на холодном старте и только пока висит заставка. */
    private var showWhenLoaded = false

    private val _waitingForAd = MutableStateFlow(false)

    /**
     * Показывать ли заставку «сейчас будет реклама» ([AdLoadingNotice]).
     *
     * Поднимается на холодном старте, когда объявление заказано, и гаснет, как только
     * ответ получен — показом, отказом или истечением [NOTICE_TIMEOUT_MS].
     */
    val waitingForAd: StateFlow<Boolean> get() = _waitingForAd

    private val handler = Handler(Looper.getMainLooper())
    /** Реклама ждёт активности: `ON_START` процесса мог прийти раньше, чем она встала. */
    private var showPending = false
    /** Объявление сейчас на экране — от `show()` до закрытия или отказа. */
    private var showing = false
    private var host: Activity? = null

    init {
        application.registerActivityLifecycleCallbacks(HostTracker())
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // Premium, пришедший посреди процесса (ответ сервера на старте, оплата): заставка
        // «загружаем рекламу» уходит сразу, заказ и лежащее объявление сбрасываются. Уже
        // открытое объявление не закрываем — оно доиграет само.
        // `Main`, а не `Main.immediate`: первый сбор — уже после конструктора, когда
        // `noticeTimeout` ниже по тексту класса инициализирован.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            AdFree.active.collect { adFree ->
                if (!adFree) return@collect
                log("Premium — реклама при запуске отключена")
                showWhenLoaded = false
                showPending = false
                stopWaiting()
                clearAd()
            }
        }
    }

    /** Больше в этом процессе рекламу при запуске не показывать и не грузить. */
    fun mute() {
        muted = true
        showWhenLoaded = false
        showPending = false
        stopWaiting()
        log("приглушено на весь процесс: первый запуск или первый после обновления")
    }

    override fun onStart(owner: LifecycleOwner) {
        val cold = coldStart
        coldStart = false
        log("на передний план, холодный старт=$cold, приглушено=$muted, есть объявление=${ad != null}")
        if (muted || showing || AdFree.active.value) return
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt > AD_TTL_MS) clearAd()
        if (ad != null) {
            showPending = true
            showIfPossible()
            return
        }
        if (cold) {
            showWhenLoaded = true
            startWaiting()
        }
        load()
    }

    override fun onStop(owner: LifecycleOwner) {
        showPending = false
        showWhenLoaded = false
        stopWaiting()
    }

    /**
     * Поднимает заставку и заводит будильник на случай, если ответа так и не будет.
     *
     * Без будильника медленная сеть держала бы человека перед заставкой сколько угодно:
     * у загрузки объявления своего срока нет, а «нет ответа» снаружи неотличимо от
     * «ещё чуть-чуть».
     */
    private fun startWaiting() {
        _waitingForAd.value = true
        handler.removeCallbacks(noticeTimeout)
        handler.postDelayed(noticeTimeout, NOTICE_TIMEOUT_MS)
    }

    private fun stopWaiting() {
        handler.removeCallbacks(noticeTimeout)
        _waitingForAd.value = false
    }

    /**
     * Объявление не успело: заставка уходит, и **на этом запуске реклама уже не
     * покажется**. Приехавшее следом объявление остаётся лежать до следующего выхода на
     * передний план — падать поверх приложения, которым человек уже занялся, ему незачем.
     */
    private val noticeTimeout = Runnable {
        log("не успело за $NOTICE_TIMEOUT_MS мс — заставку убираем, показ до следующего раза")
        showWhenLoaded = false
        _waitingForAd.value = false
    }

    private fun load() {
        if (muted || loading || ad != null || AdFree.active.value) return
        // Защёлка ставится уже внутри: если инициализация SDK так и не ответит, снаружи
        // она осталась бы поднятой навсегда, и следующий `onStart` не заказал бы ничего.
        MobileAdsSdk.whenReady(application) {
            if (muted || loading || ad != null || AdFree.active.value) return@whenReady
            loading = true
            val loader = loader ?: AppOpenAdLoader(application).also { loader = it }
            val request = AdRequest.Builder(MobileAdsSdk.APP_OPEN_UNIT_ID)
                .setPreferredTheme(MobileAdsSdk.adThemeFor(application, settings.themeMode))
                .build()
            loader.loadAd(request, loadListener)
        }
    }

    private val loadListener = object : AppOpenAdLoadListener {
        override fun onAdLoaded(appOpenAd: AppOpenAd) {
            loading = false
            ad = appOpenAd
            loadedAt = SystemClock.elapsedRealtime()
            log("загружено, ждали показа=$showWhenLoaded")
            if (showWhenLoaded) showPending = true
            showWhenLoaded = false
            showIfPossible()
        }

        override fun onAdFailedToLoad(error: AdRequestError) {
            loading = false
            log("не загрузилось: ${error.code} ${error.description}")
            showWhenLoaded = false
            // Заставка уходит сразу, не досиживая срок: ответ уже получен, и держать
            // человека перед ней ещё пять секунд не за чем.
            stopWaiting()
        }
    }

    /**
     * Показывает, если всё сошлось: показ заказан, реклама есть, активность стоит и
     * процесс всё ещё на переднем плане — загрузка могла кончиться уже после того, как
     * приложение свернули, и реклама поверх чужого экрана никому не нужна.
     */
    private fun showIfPossible() {
        if (!showPending || muted || showing || AdFree.active.value) return
        val ready = ad ?: return
        val activity = host ?: return
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (!foreground) return
        showPending = false
        showing = true
        // Заставка своё отработала — дальше экран занимает само объявление.
        stopWaiting()
        log("показываем")
        ready.setAdEventListener(eventListener)
        ready.show(activity)
    }

    /**
     * След решений контроллера — только в отладочной сборке.
     *
     * Реклама при запуске молчит по доброму десятку причин (приглушена, не загрузилась,
     * приехала поздно, экран уже трогали), и снаружи все они выглядят одинаково: рекламы
     * нет. Один тег в логе отвечает, которая из них сработала.
     */
    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private val eventListener = object : AppOpenAdEventListener {
        override fun onAdShown() {}
        override fun onAdFailedToShow(adError: AdError) {
            showing = false
            clearAd()
            load()
        }

        override fun onAdDismissed() {
            showing = false
            clearAd()
            load()
        }

        override fun onAdClicked() {}
        override fun onAdImpression(impressionData: ImpressionData?) {}
    }

    private fun clearAd() {
        ad?.setAdEventListener(null)
        ad = null
    }

    /** Следит только за [hostActivity]; экран рекламы и прочие чужие активности — мимо. */
    private inner class HostTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (!hostActivity.isInstance(activity)) return
            host = activity
            showIfPossible()
        }

        override fun onActivityStopped(activity: Activity) {
            if (host === activity) host = null
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (host === activity) host = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }

    private companion object {
        const val TAG = "AppOpenAd"

        /**
         * Сколько заставка ждёт объявление, прежде чем пустить человека в приложение.
         *
         * Это единственный срок, ограничивающий показ «при запуске»: пока заставка висит,
         * реклама уместна, после неё — уже нет. Семь секунд — компромисс, померенный по
         * живому запуску: инициализация SDK занимает около пяти секунд, объявление
         * приехало на 5,9-й; срок короче отсекал бы рекламу почти всегда, длиннее —
         * превращал бы запуск в ожидание. Отказ в загрузке снимает заставку сразу и срока
         * не досиживает.
         *
         * Первая версия отмеряла окно **от выхода процесса на передний план** и была
         * равна пяти секундам — и **реклама не показывалась ни разу**: в это окно
         * попадала инициализация SDK, так что объявление всегда опаздывало.
         */
        const val NOTICE_TIMEOUT_MS = 7_000L

        /** Сколько загруженное объявление считается годным к показу. */
        const val AD_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
