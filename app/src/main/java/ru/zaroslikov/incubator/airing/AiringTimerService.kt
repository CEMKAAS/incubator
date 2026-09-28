package ru.zaroslikov.incubator.airing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.InventoryApplication

/**
 * Служба, которая доводит таймер проветривания до конца, когда приложения на экране нет.
 *
 * Foreground service, а не точный будильник, и это выбор, а не случайность. `setAlarmClock`
 * на Android 12+ требует `SCHEDULE_EXACT_ALARM`, которое с 14-й версии выключено по
 * умолчанию и включается человеком в системных настройках, — то есть первый же таймер
 * уводил бы его из формы в настройки. Служба стартует из открытого приложения без
 * вопросов, держит частичный wakelock на время таймера, показывает счётчик, а по сроку
 * зовёт [AiringTimerController.timeUp] и играет мелодию. Тип `specialUse` — единственный
 * из типов Android 14, под который подходит таймер (`shortService` кончается через три
 * минуты). До глубокого Doze, где wakelock перестают работать, телефон доходит через
 * полчаса неподвижности с погашенным экраном, — дольше инкубатор открытым не держат
 * ([MAX_MINUTES] — потолок, а не норма).
 *
 * Служба ничего не решает сама: она подписана на [AiringTimerController.state] и делает
 * то, что говорит состояние, — ждёт, звенит, уходит. Кнопки уведомления и «Готово» в
 * форме меняют состояние через контроллер, и служба узнаёт об этом тем же путём. Поэтому
 * же она переживает смерть процесса: `START_STICKY` поднимает её заново, контроллер
 * читает состояние с диска, и служба продолжает с того же места.
 *
 * Мелодия — системный будильник ([RingtoneManager.TYPE_ALARM]) с `USAGE_ALARM`: играет на
 * громкости будильника и пробивает «не беспокоить» так, как пробивает будильник, — а
 * закрыть инкубатор вовремя важнее любого другого уведомления этого приложения. Играет
 * сама служба, а не канал уведомления, и это не мелочь: без разрешения на уведомления
 * канал молчит, а инкубатор всё равно открыт. Цикл — до «Готово», но не дольше
 * [RING_TIMEOUT_MILLIS].
 */
class AiringTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: AiringTimerController

    private var waitJob: Job? = null
    private var waitingFor: Long = 0L
    private var ringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /**
     * Мелодия смолкла сама, по сроку, — никто не нажал «Готово» и не закрыл инкубатор,
     * насколько известно приложению; после ухода службы об этом остаётся обычное
     * уведомление. Погашенная кнопкой мелодия такого следа не оставляет: человек уже ответил.
     */
    private var ringTimedOut = false

    override fun onCreate() {
        super.onCreate()
        controller = (application as InventoryApplication).container.airingTimer
        ensureAiringChannels(this)
        scope.launch { controller.state.collect { apply(it) } }
    }

    /**
     * Первое, что делает поднятая служба, — выходит на передний план: у неё на это
     * несколько секунд, и не сделать этого значит упасть. Дальше всё решает состояние —
     * см. [apply]; здесь только гарантия, что `startForeground` был.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = controller.state.value
        promote(state)
        // Поднята, а делать нечего — отменили, пока стартовала, или система подняла
        // службу заново после того, как всё кончилось: выйти, не оставив уведомления.
        if (state is AiringTimerState.Idle || (state is AiringTimerState.Done && !state.ringing)) leave()
        return START_STICKY
    }

    private fun leave() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSound()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun apply(state: AiringTimerState) {
        // На передний план — только с тем, что стоит показывать: бегущий счётчик и
        // «время вышло», пока звенит. Досрочное «Завершить» — единственный случай, когда
        // ничто не должно всплыть, и без этого условия оно на кадр показывало бы баннер.
        if (state is AiringTimerState.Running || (state is AiringTimerState.Done && state.ringing)) {
            promote(state)
        }
        when (state) {
            is AiringTimerState.Running -> {
                ringJob?.cancel()
                stopSound()
                if (waitJob?.isActive != true || waitingFor != state.endAt) {
                    waitJob?.cancel()
                    waitingFor = state.endAt
                    waitJob = scope.launch { await(state) }
                }
            }
            is AiringTimerState.Done -> {
                waitJob?.cancel()
                if (state.ringing) {
                    if (ringJob?.isActive != true) {
                        startSound()
                        ringJob = scope.launch { ring() }
                    }
                } else {
                    ringJob?.cancel()
                    stopSound()
                    releaseWakeLock()
                    // Уведомление службы уходит вместе с ней; о том, что было, остаётся
                    // обычное — с переходом в форму, но без кнопок: службы, которая
                    // выполнила бы их, уже нет.
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (ringTimedOut) {
                        postAiringNotification(
                            this,
                            AIRING_DONE_NOTIFICATION_ID,
                            doneNotification(this, state, ringing = false),
                        )
                    }
                    stopSelf()
                }
            }
            AiringTimerState.Idle -> {
                waitJob?.cancel()
                ringJob?.cancel()
                stopSound()
                releaseWakeLock()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                NotificationManagerCompat.from(this).cancel(AIRING_DONE_NOTIFICATION_ID)
                stopSelf()
            }
        }
    }

    /** Ждёт срока под wakelock и отдаёт конец контроллеру — тот включит мелодию. */
    private suspend fun await(state: AiringTimerState.Running) {
        val remaining = state.remainingMillis(System.currentTimeMillis())
        acquireWakeLock(remaining + WAKE_LOCK_MARGIN_MILLIS)
        delay(remaining)
        controller.timeUp()
    }

    /**
     * Держит мелодию до срока или до «Готово». На Android 8 и 9 у [Ringtone] нет
     * зацикливания, там мелодия перезапускается вручную, когда доиграла.
     */
    private suspend fun ring() {
        acquireWakeLock(RING_TIMEOUT_MILLIS + WAKE_LOCK_MARGIN_MILLIS)
        val until = System.currentTimeMillis() + RING_TIMEOUT_MILLIS
        while (scope.isActive && System.currentTimeMillis() < until) {
            delay(RING_POLL_MILLIS)
            // Без мелодии (нет ни будильника, ни звука уведомления) срок выдерживает
            // вибрация — прервать цикл здесь значило бы прервать и её.
            val tone = ringtone
            if (tone != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !tone.isPlaying) {
                runCatching { tone.play() }
            }
        }
        ringTimedOut = true
        controller.stopRinging()
    }

    /**
     * Выводит службу на передний план с уведомлением под состояние. Повторный вызов
     * обновляет уведомление; Android 14 требует тип, и он один — `specialUse`.
     */
    private fun promote(state: AiringTimerState) {
        val notification = when (state) {
            is AiringTimerState.Running -> countdownNotification(this, state)
            is AiringTimerState.Done -> doneNotification(this, state, ringing = state.ringing)
            // Служба поднята, а таймера уже нет (отменили, пока она стартовала): на
            // передний план всё равно выйти надо, иначе система сочтёт запуск обманом.
            AiringTimerState.Idle -> countdownNotification(
                this,
                AiringTimerState.Running(0L, AiringTimerTarget(0L), "", 0L, 0L, 0),
            )
        }
        // `ServiceCompat` сам отбрасывает тип на версиях, которые его не знают.
        runCatching {
            ServiceCompat.startForeground(
                this,
                AIRING_TIMER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure {
            // Не вышла на передний план — значит, и держать её нельзя: система убьёт
            // процесс за службу, которая обещала уведомление и не показала его.
            Log.w(TAG, "Не удалось выйти на передний план", it)
            stopSelf()
        }
    }

    private fun startSound() {
        if (ringtone == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val tone = uri?.let { RingtoneManager.getRingtone(this, it) }
            if (tone != null) {
                tone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
                runCatching { tone.play() }.onFailure { Log.w(TAG, "Мелодия не заиграла", it) }
                ringtone = tone
            }
        }
        if (vibrator == null) {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v != null && v.hasVibrator()) {
                runCatching {
                    // Как будильник, а не как уведомление: иначе в «Не беспокоить» вибрацию
                    // глушат, а она — запасной сигнал, когда мелодии нет.
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    @Suppress("DEPRECATION")
                    v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), attributes)
                }
                vibrator = v
            }
        }
    }

    private fun stopSound() {
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
    }

    private fun acquireWakeLock(timeoutMillis: Long) {
        releaseWakeLock()
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(timeoutMillis)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    companion object {
        private const val TAG = "AiringTimerService"
        private const val WAKE_LOCK_TAG = "Incubator:AiringTimer"
        private const val WAKE_LOCK_MARGIN_MILLIS = 30_000L
        private const val RING_POLL_MILLIS = 1_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 300, 500, 1_200)

        /** Поднимает службу; уже поднятая просто получает `onStartCommand` ещё раз. */
        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AiringTimerService::class.java),
            )
        }.onFailure {
            // Из фона foreground service Android 12+ не пускает: так бывает, когда
            // будильник будит мёртвый процесс. Тогда звенит уведомление, см. приёмник.
            Log.w(TAG, "Служба не поднята", it)
        }.isSuccess
    }
}
