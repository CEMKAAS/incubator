package ru.zaroslikov.incubator.airing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Запасной будильник на срок таймера — на случай, если служба не доживёт до него.
 *
 * Основной путь — [AiringTimerService]: она ждёт срока под wakelock и сама включает
 * мелодию. Но foreground service — это процесс, а процессы на некоторых прошивках
 * гасят вместе с их службами, и `START_STICKY` — обещание системы, а не гарантия. Без
 * подстраховки такая смерть означала бы тишину при открытом инкубаторе — ровно то, ради
 * чего таймер ставили. Будильник неточный (`setAndAllowWhileIdle`, никакого
 * `SCHEDULE_EXACT_ALARM` — см. службу, почему точный не годится), то есть может прийти
 * на минуты позже, но приходит и в Doze, и в мёртвый процесс: приёмник
 * ([AiringTimerActionReceiver.ACTION_TIME_UP]) переводит таймер в «время вышло» и звенит
 * уведомлением с `FLAG_INSISTENT`, раз службы, которая играла бы сама, нет.
 *
 * Пока служба жива, будильник лишний и безвредный: к его приходу состояние уже `Done`,
 * и [AiringTimerController.timeUp] на нём ничего не делает.
 */
object AiringTimerAlarm {
    private const val TAG = "AiringTimerAlarm"

    /** Небольшой отступ после срока: служба, если жива, должна успеть первой. */
    private const val MARGIN_MILLIS = 1_500L

    fun schedule(context: Context, endAt: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                endAt + MARGIN_MILLIS,
                pendingIntent(context),
            )
        }.onFailure { Log.w(TAG, "Будильник не поставлен", it) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AiringTimerActionReceiver::class.java)
            .setAction(AiringTimerActionReceiver.ACTION_TIME_UP)
        return PendingIntent.getBroadcast(
            context,
            AiringTimerActionReceiver.ACTION_TIME_UP.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
