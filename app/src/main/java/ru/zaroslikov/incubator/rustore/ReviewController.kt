package ru.zaroslikov.incubator.rustore

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.rustore.sdk.review.RuStoreReviewManager
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import ru.rustore.sdk.core.util.toSuspendResult
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.settings.AppSettings

private const val TAG = "Review"

/** Страница приложения в RuStore — куда ведёт кнопка, если встроенную оценку показать нечем. */
private const val RUSTORE_APP_URL = "https://apps.rustore.ru/app/${BuildConfig.APPLICATION_ID}"

/**
 * Просьба оценить приложение — встроенным окном RuStore, без ухода из приложения.
 *
 * **Когда просить.** Ровно в одном месте: сразу после того, как закладка доведена до
 * срока и птенцы посчитаны ([offerAfterHatch]). Это единственная минута, в которую у
 * человека в этом приложении есть чему радоваться, и единственная, про которую заранее
 * известно, что он не занят чем-то срочным. Просьба «на пятом запуске», как её обычно
 * делают, пришлась бы в том числе на того, кто открыл приложение выяснить, почему не
 * пришло напоминание, — и получила бы честную оценку этого выяснения.
 *
 * **Сколько раз просить.** Не чаще раза на версию — [AppSettings.reviewAskedVersion].
 * У RuStore есть и свой предел, он вернёт `RuStoreRequestLimitReached`, но опираться
 * только на него нельзя: свои правила он может менять, а второе окно после второго
 * вывода за неделю — это уже не просьба. Отметка ставится **до** показа, а не после
 * удачи: если окно не появилось, значит RuStore нечего показать, и повторять попытку
 * через день смысла не больше, чем сегодня.
 *
 * **Отказы тихие — кроме нажатой руками кнопки.** Всё, чем встроенная оценка может не
 * состояться (RuStore не установлен, человек уже оценивал, предел просьб), — не событие
 * для того, кто только что закрыл закладку. А вот нажавшему «Оценить приложение» в
 * «О приложении» нужно, чтобы что-нибудь произошло: для него [open] в этом случае
 * открывает страницу приложения в RuStore, где оценку можно поставить обычным способом.
 */
class ReviewController(
    private val application: Application,
    private val settings: AppSettings,
) {

    /** Своя область — просьба переживает экран, с которого пришла. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val manager: RuStoreReviewManager? by lazy {
        runCatching { RuStoreReviewManagerFactory.create(application) }
            .onFailure { Log.d(TAG, "RuStore недоступен: ${it.message}") }
            .getOrNull()
    }

    /**
     * Закладка доведена до срока — предложить оценить, если в этой версии ещё не
     * предлагали.
     *
     * @param versionCode код текущей сборки. Параметром, а не чтением `BuildConfig`
     *        внутри: так правило «раз на версию» видно на месте вызова и проверяется
     *        без запуска приложения.
     */
    fun offerAfterHatch(versionCode: Int) {
        if (settings.reviewAskedVersion == versionCode) return
        settings.reviewAskedVersion = versionCode
        launchFlow(event = Events.REVIEW_OFFERED, fallbackToStore = false)
    }

    /** «Оценить приложение» из «О приложении» — по нажатию, без всяких условий. */
    fun open() {
        launchFlow(event = Events.REVIEW_OPENED, fallbackToStore = true)
    }

    private fun launchFlow(event: String, fallbackToStore: Boolean) {
        val manager = manager ?: run {
            if (fallbackToStore) openStorePage()
            return
        }
        Analytics.report(event)

        scope.launch {
            val info = manager.requestReviewFlow().toSuspendResult().getOrElse { error ->
                // Сюда приходит и «уже оценивал», и «слишком часто просите», и
                // «RuStore не установлен» — все они означают одно: окна не будет.
                Log.d(TAG, "Окно оценки недоступно: ${error.message}")
                if (fallbackToStore) openStorePage()
                return@launch
            }
            manager.launchReviewFlow(info).toSuspendResult().onFailure { error ->
                Log.d(TAG, "Окно оценки не открылось: ${error.message}")
                if (fallbackToStore) openStorePage()
            }
        }
    }

    /**
     * Страница приложения в RuStore обычной ссылкой.
     *
     * `NEW_TASK` обязателен: намерение уходит из контекста приложения, а не активности,
     * — контроллер живёт в контейнере и об активностях ничего не знает.
     */
    private fun openStorePage() {
        val intent = Intent(Intent.ACTION_VIEW, RUSTORE_APP_URL.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { application.startActivity(intent) }
            .onFailure { Log.d(TAG, "Страницу в RuStore открыть нечем: ${it.message}") }
    }
}
