package ru.zaroslikov.incubator.rustore

import android.app.Activity
import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallState
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.rustore.sdk.core.util.toSuspendResult
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events

private const val TAG = "AppUpdate"

/**
 * Что приложение сейчас говорит человеку про обновление. Ровно то, что рисует
 * [AppUpdateNotice], и ничего сверх того.
 *
 * Состояний четыре, а не «есть обновление / нет»: отложенное обновление
 * ([AppUpdateType.FLEXIBLE]) — это не одно нажатие, а три разнесённых во времени
 * события, и каждое из них человек должен увидеть. Согласился — качается; скачалось —
 * ждёт разрешения встать; и всё это время приложением можно пользоваться.
 */
sealed interface AppUpdateState {

    /** Говорить нечего: обновления нет, оно отклонено или RuStore на телефоне нет. */
    data object Hidden : AppUpdateState

    /**
     * Новая версия есть, и её предлагают скачать.
     *
     * @param versionName как версия называется в RuStore — «1.2.0», а не код сборки:
     *        код человеку ни о чём не говорит, а имя он видел на экране «О приложении».
     * @param sizeBytes размер файла. Показывается потому, что качать будут с телефона,
     *        и «14 МБ» — это ответ на вопрос, который человек задаёт себе сам.
     */
    data class Offered(val versionName: String, val sizeBytes: Long) : AppUpdateState

    /**
     * Идёт загрузка.
     *
     * @param percent доля скачанного, `null` — пока RuStore не сообщил размер. Разница
     *        не косметическая: полоса без числа честно говорит «идёт», а полоса,
     *        замершая на нуле, читается как «зависло».
     */
    data class Downloading(val percent: Int?) : AppUpdateState

    /** Файл скачан, дело за согласием установить. */
    data object ReadyToInstall : AppUpdateState
}

/** Чем кончилась проверка, запрошенная руками из «О приложении». */
enum class UpdateCheckOutcome {
    /** Обновление есть — карточка уже на экране. */
    Offered,

    /** Стоит последняя версия. */
    UpToDate,

    /** Спросить не у кого: RuStore не установлен, устарел или не ответил. */
    Unavailable,
}

/**
 * Обновление приложения через RuStore — отложенное, с согласия и в фоне.
 *
 * **Почему отложенное ([AppUpdateType.FLEXIBLE]), а не немедленное.** У RuStore есть
 * режим, в котором поверх приложения встаёт его собственный экран с прогрессом и до
 * конца установки делать ничего нельзя. Для этого приложения он неуместен: его
 * открывают, потому что пришло напоминание проверить инкубатор, и экран обновления
 * между уведомлением и закладкой — это ровно то место, где человек закроет приложение и
 * пойдёт к инкубатору без него. Отложенный режим качает в фоне и спрашивает про
 * установку тогда, когда файл уже готов; всё это время приложение работает.
 *
 * **Почему контроллер, а не вызов в экране.** Загрузка переживает и экран, и поворот, и
 * уход в фон: она идёт в RuStore, а не у нас. Состояние поэтому живёт в контейнере
 * приложения — там же, где реклама при запуске и по той же причине (см.
 * `AppContainer.appOpenAds`), — а `MainActivity` только подписана на него.
 *
 * **Все отказы тихие.** RuStore может быть не установлен, устареть, не ответить или
 * ответить ошибкой — и ни один из этих случаев не является событием для человека,
 * который открыл приложение посмотреть закладку. Все они дают [AppUpdateState.Hidden],
 * и приложение ведёт себя так, будто обновлений не бывает. Единственное исключение —
 * проверка, запрошенная руками: тот, кто нажал «Проверить обновление», ответа ждёт, и
 * ему [UpdateCheckOutcome] отвечает.
 */
class AppUpdateController(application: Application) {

    /**
     * Своя область, а не `viewModelScope`: загрузка не принадлежит ни одному экрану.
     * `Main.immediate` — потому что всё, что здесь происходит, кончается записью в
     * [state], который читает композиция; уходить ради этого на другой поток незачем.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * `null`, если менеджер не собрался вовсе. Создание лезет в систему за провайдером
     * RuStore, и на телефоне без него бросает — а это не ошибка, а обычное положение
     * дел для приложения, поставленного из другого магазина.
     */
    private val manager: RuStoreAppUpdateManager? by lazy {
        runCatching { RuStoreAppUpdateManagerFactory.create(application) }
            .onFailure { Log.d(TAG, "RuStore недоступен: ${it.message}") }
            .getOrNull()
    }

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Hidden)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Ответ последней проверки — им же начинается загрузка. */
    private var info: AppUpdateInfo? = null

    /**
     * Карточку закрыли своей рукой. До конца процесса больше не показываем: человек уже
     * сказал «не сейчас», и повторить это на каждом переходе между экранами значило бы
     * не услышать. Проверка, запрошенная руками, флаг снимает — она и есть «а теперь
     * покажи».
     */
    private var dismissed = false

    /**
     * Автоматическая проверка уже была в этом процессе.
     *
     * `MainActivity.onCreate` случается и на каждом повороте экрана, а проверка — это
     * запрос в RuStore; без этого флага поворот телефона трижды подряд стоил бы трёх
     * обращений к магазину и ничего не сообщил бы нового. Проверка, запрошенная руками,
     * флагу не подчиняется: её для того и нажали.
     */
    private var checkedInProcess = false

    private var listening = false

    private val listener = object : InstallStateUpdateListener {
        override fun onStateUpdated(state: InstallState) {
            when (state.installStatus) {
                InstallStatus.DOWNLOADING -> {
                    val total = state.totalBytesToDownload
                    val percent = if (total > 0) {
                        (state.bytesDownloaded * 100 / total).toInt().coerceIn(0, 100)
                    } else {
                        null
                    }
                    _state.value = AppUpdateState.Downloading(percent)
                }

                InstallStatus.DOWNLOADED -> _state.value = AppUpdateState.ReadyToInstall

                InstallStatus.FAILED, InstallStatus.DOWNLOAD_INTERRUPTED -> {
                    Log.d(TAG, "Загрузка не удалась, код ${state.installErrorCode}")
                    _state.value = AppUpdateState.Hidden
                }

                // PENDING и INSTALLING рисовать нечем и незачем: первое — та же
                // загрузка, второе длится секунды и кончается перезапуском.
                else -> Unit
            }
        }
    }

    /**
     * Спрашивает RuStore, есть ли версия новее.
     *
     * @param manual проверку запросили руками. Тогда она идёт даже после закрытой
     *        карточки, а результат уезжает в [onOutcome] — нажавшему нужно услышать
     *        ответ, в том числе «у вас и так последняя».
     */
    fun check(manual: Boolean = false, onOutcome: ((UpdateCheckOutcome) -> Unit)? = null) {
        val manager = manager ?: run {
            onOutcome?.invoke(UpdateCheckOutcome.Unavailable)
            return
        }
        if (manual) {
            dismissed = false
        } else {
            if (dismissed || checkedInProcess) return
            checkedInProcess = true
        }
        // Загрузка уже идёт — спрашивать нечего, ответ на экране.
        if (_state.value is AppUpdateState.Downloading) {
            onOutcome?.invoke(UpdateCheckOutcome.Offered)
            return
        }

        scope.launch {
            val info = manager.getAppUpdateInfo().toSuspendResult().getOrElse { error ->
                Log.d(TAG, "Проверка обновления не удалась: ${error.message}")
                onOutcome?.invoke(UpdateCheckOutcome.Unavailable)
                return@launch
            }
            this@AppUpdateController.info = info

            when {
                // Файл уже лежит скачанным — с прошлого запуска, где согласились
                // качать, но не дошли до установки. Предлагаем сразу поставить.
                info.installStatus == InstallStatus.DOWNLOADED -> {
                    listen(manager)
                    _state.value = AppUpdateState.ReadyToInstall
                    onOutcome?.invoke(UpdateCheckOutcome.Offered)
                }

                // Загрузка идёт прямо сейчас, начатая в прошлый раз.
                info.updateAvailability ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    listen(manager)
                    _state.value = AppUpdateState.Downloading(percent = null)
                    onOutcome?.invoke(UpdateCheckOutcome.Offered)
                }

                info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE -> {
                    _state.value = AppUpdateState.Offered(
                        versionName = info.availableVersionName,
                        sizeBytes = info.fileSize,
                    )
                    Analytics.report(
                        Events.APP_UPDATE_OFFERED,
                        mapOf(
                            "Версия" to info.availableVersionName,
                            "Вручную" to manual,
                        ),
                    )
                    onOutcome?.invoke(UpdateCheckOutcome.Offered)
                }

                else -> {
                    _state.value = AppUpdateState.Hidden
                    onOutcome?.invoke(UpdateCheckOutcome.UpToDate)
                }
            }
        }
    }

    /** «Обновить»: RuStore спрашивает согласие и качает файл в фоне. */
    fun download() {
        val manager = manager ?: return
        val info = info ?: return
        listen(manager)
        _state.value = AppUpdateState.Downloading(percent = null)
        Analytics.report(
            Events.APP_UPDATE_STARTED,
            mapOf("Версия" to info.availableVersionName),
        )

        scope.launch {
            val options = AppUpdateOptions.Builder()
                .appUpdateType(AppUpdateType.FLEXIBLE)
                .build()
            manager.startUpdateFlow(info, options).toSuspendResult()
                .onSuccess { code ->
                    // RESULT_OK здесь значит «согласился качать», а не «обновился»:
                    // дальше говорит слушатель. Отказ гасим и больше не предлагаем —
                    // человек уже ответил.
                    if (code != Activity.RESULT_OK) {
                        dismissed = true
                        _state.value = AppUpdateState.Hidden
                    }
                }
                .onFailure { error ->
                    Log.d(TAG, "Обновление не началось: ${error.message}")
                    _state.value = AppUpdateState.Hidden
                }
        }
    }

    /** «Установить»: RuStore ставит скачанное и перезапускает приложение. */
    fun install() {
        val manager = manager ?: return
        Analytics.report(Events.APP_UPDATE_INSTALL)
        scope.launch {
            val options = AppUpdateOptions.Builder()
                .appUpdateType(AppUpdateType.FLEXIBLE)
                .build()
            manager.completeUpdate(options).toSuspendResult().onFailure { error ->
                Log.d(TAG, "Установка не началась: ${error.message}")
                _state.value = AppUpdateState.Hidden
            }
        }
    }

    /** «Позже». Загрузку, если она идёт, не трогает — она в RuStore и дойдёт сама. */
    fun dismiss() {
        dismissed = true
        _state.value = AppUpdateState.Hidden
    }

    /**
     * Подписка на ход загрузки — один раз за процесс.
     *
     * Отписки нет намеренно: слушатель принадлежит контроллеру, контроллер — процессу,
     * а повторная подписка на каждое нажатие дала бы по копии слушателя на нажатие.
     */
    private fun listen(manager: RuStoreAppUpdateManager) {
        if (listening) return
        manager.registerListener(listener)
        listening = true
    }
}
