package ru.zaroslikov.incubator.airing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.zaroslikov.incubator.MainActivity
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.work.EXTRA_BATCH_ID
import ru.zaroslikov.incubator.work.EXTRA_INCUBATOR_ID
import ru.zaroslikov.incubator.ui.clockText
import java.util.Date

/**
 * Уведомления таймера проветривания: счётчик, пока идёт, и «время вышло», когда кончился.
 *
 * Каналов два, потому что у них разные важности. Счётчик — `IMPORTANCE_LOW`: он висит
 * всё проветривание, обновляется системой сам ([NotificationCompat.Builder.setChronometerCountDown])
 * и пищать не должен ни разу. «Время вышло» — `IMPORTANCE_HIGH`, чтобы выехать поверх
 * того, чем человек занят. Мелодию в обычном пути играет сама [AiringTimerService] —
 * в цикле, до «Готово», и независимо от того, разрешены ли уведомления, — поэтому её
 * уведомление идёт с `setSilent`, а звук канала (тот же будильник) нужен одному
 * [insistentDoneNotification], запасному пути на случай гибели процесса.
 */
const val AIRING_COUNTDOWN_CHANNEL_ID = "Проветривание"
const val AIRING_DONE_CHANNEL_ID = "Проветривание окончено"

/** Номера вне диапазона напоминаний (`reminderNotificationId` не доходит до 1,5 млрд). */
const val AIRING_TIMER_NOTIFICATION_ID = 2_000_000_000
const val AIRING_DONE_NOTIFICATION_ID = 2_000_000_001

/** Отметка в намерении: нажали на уведомление таймера, а не напоминания. См. `TimerTarget`. */
const val EXTRA_AIRING_TIMER = "ru.zaroslikov.incubator.extra.AIRING_TIMER"

fun ensureAiringChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        ?: return
    if (manager.getNotificationChannel(AIRING_COUNTDOWN_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                AIRING_COUNTDOWN_CHANNEL_ID,
                "Таймер проветривания",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Счётчик, пока инкубатор открыт на проветривание"
                setShowBadge(false)
            },
        )
    }
    if (manager.getNotificationChannel(AIRING_DONE_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                AIRING_DONE_CHANNEL_ID,
                "Проветривание окончено",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Время проветривания вышло — пора закрыть инкубатор"
                // Звук канала — тот же будильник, что играет служба, но нужен он только
                // запасному уведомлению ([insistentDoneNotification]): служба своё шлёт с
                // `setSilent`, чтобы мелодия не звучала дважды.
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            },
        )
    }
}

/** Уведомление службы, пока таймер идёт: обратный отсчёт рисует система. */
fun countdownNotification(context: Context, state: AiringTimerState.Running) =
    NotificationCompat.Builder(context, AIRING_COUNTDOWN_CHANNEL_ID)
        .setSmallIcon(R.drawable.baseline_egg_24)
        .setContentTitle(titleFor(state.label, "Проветривание"))
        .setContentText("Закройте инкубатор в ${clockText(Date(state.endAt))} · ${state.minutes} мин")
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(state.endAt)
        .setShowWhen(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(timerContentIntent(context, state.target))
        .addAction(0, "Завершить", actionIntent(context, AiringTimerActionReceiver.ACTION_FINISH))
        .addAction(0, "Отменить", actionIntent(context, AiringTimerActionReceiver.ACTION_CANCEL))
        .build()

/**
 * «Время вышло»: пока играет мелодия — с кнопкой, которая её гасит; после — просто
 * запись о том, что случилось, с переходом в форму.
 */
fun doneNotification(context: Context, state: AiringTimerState.Done, ringing: Boolean) =
    NotificationCompat.Builder(context, AIRING_DONE_CHANNEL_ID)
        .setSmallIcon(R.drawable.baseline_egg_24)
        .setContentTitle("Время вышло — закройте инкубатор!")
        .setContentText(doneText(state))
        .setStyle(NotificationCompat.BigTextStyle().bigText(doneText(state)))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setOngoing(ringing)
        .setAutoCancel(!ringing)
        .setSilent(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(timerContentIntent(context, state.target))
        .apply {
            if (ringing) {
                addAction(
                    0,
                    "Закрыл инкубатор",
                    actionIntent(context, AiringTimerActionReceiver.ACTION_DISMISS),
                )
            }
        }
        .build()

/**
 * «Время вышло», когда играть мелодию некому: будильник разбудил мёртвый процесс, а
 * службу из фона поднять нельзя. Звук — канала, `FLAG_INSISTENT` повторяет его, пока
 * уведомление не снимут, `setTimeoutAfter` снимает его по сроку мелодии; кнопка
 * «Закрыл инкубатор» гасит через приёмник, как обычно.
 */
fun insistentDoneNotification(context: Context, state: AiringTimerState.Done): android.app.Notification =
    NotificationCompat.Builder(context, AIRING_DONE_CHANNEL_ID)
        .setSmallIcon(R.drawable.baseline_egg_24)
        .setContentTitle("Время вышло — закройте инкубатор!")
        .setContentText(doneText(state))
        .setStyle(NotificationCompat.BigTextStyle().bigText(doneText(state)))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        .setTimeoutAfter(RING_TIMEOUT_MILLIS)
        .setAutoCancel(true)
        .setContentIntent(timerContentIntent(context, state.target))
        .addAction(0, "Закрыл инкубатор", actionIntent(context, AiringTimerActionReceiver.ACTION_DISMISS))
        .build()
        .apply { flags = flags or android.app.Notification.FLAG_INSISTENT }

private fun doneText(state: AiringTimerState.Done): String =
    "${titleFor(state.label, "Проветривание")} · ${state.minutes} мин. " +
        "Минуты подставлены в форму замера — запишите его."

private fun titleFor(label: String, prefix: String) =
    if (label.isBlank()) prefix else "$prefix · $label"

/** Показывает уведомление, если разрешено; иначе молча — мелодия всё равно играет. */
fun postAiringNotification(context: Context, id: Int, notification: android.app.Notification): Boolean {
    val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    if (canNotify) NotificationManagerCompat.from(context).notify(id, notification)
    return canNotify
}

/**
 * Куда ведёт нажатие: в форму, из которой таймер поставили — шторку закладки или «Замеры
 * за сегодня» инкубатора. Разбирает [ru.zaroslikov.incubator.TimerTarget].
 *
 * Без `CLEAR_TASK`, в отличие от напоминания: таймер ставят из работающего приложения,
 * и через минуту оно, скорее всего, ещё открыто на той же шторке — `singleTop` отдаст
 * намерение в `onNewIntent`, а граф сам срежет стек до инкубатора, как по QR-коду.
 */
fun timerContentIntent(context: Context, target: AiringTimerTarget): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_AIRING_TIMER, true)
        putExtra(EXTRA_INCUBATOR_ID, target.incubatorId)
        putExtra(EXTRA_BATCH_ID, target.batchId)
    }
    return PendingIntent.getActivity(
        context,
        AIRING_TIMER_NOTIFICATION_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * Кнопки уведомления идут через приёмник, а не в службу: к моменту нажатия служба
 * могла уже остановиться (мелодия смолкла по сроку), а `startService` из фона на
 * остановленную службу Android 8+ не пускает. Приёмнику это всё равно.
 */
private fun actionIntent(context: Context, action: String): PendingIntent {
    val intent = Intent(context, AiringTimerActionReceiver::class.java).setAction(action)
    return PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
