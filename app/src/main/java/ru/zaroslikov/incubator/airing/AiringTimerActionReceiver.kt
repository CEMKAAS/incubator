package ru.zaroslikov.incubator.airing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.zaroslikov.incubator.InventoryApplication

/**
 * Кнопки уведомлений таймера — «Завершить», «Отменить», «Закрыл инкубатор».
 *
 * Приёмник, а не `PendingIntent.getService`: нажатие на кнопку уведомления приходит из
 * фона, и `startService` на службу, которая к этой секунде могла уже остановиться,
 * Android 8+ не пускает — а приёмнику всё равно, жива ли она. Сам приёмник ничего не показывает и не
 * гасит — он меняет состояние через [AiringTimerController], а служба и форма узнают
 * об этом из потока, как обо всём остальном.
 */
class AiringTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = (context.applicationContext as? InventoryApplication)
            ?.container?.airingTimer ?: return
        when (intent.action) {
            ACTION_CANCEL -> controller.cancel()
            ACTION_FINISH -> controller.finishNow()
            ACTION_DISMISS -> controller.stopRinging()
            ACTION_TIME_UP -> timeUp(context, controller)
        }
    }

    /**
     * Запасной будильник ([AiringTimerAlarm]) пришёл. Живая служба к этому моменту уже
     * всё сделала сама, и [AiringTimerController.timeUp] на `Done` не делает ничего.
     * Мёртвый процесс будильник поднимает заново — тогда службу из фона Android 12+
     * не пускает, и звенит уведомление: `FLAG_INSISTENT` повторяет звук канала, пока
     * его не снимут, а `setTimeoutAfter` снимает его по сроку мелодии сам.
     */
    private fun timeUp(context: Context, controller: AiringTimerController) {
        val running = controller.state.value as? AiringTimerState.Running ?: return
        if (System.currentTimeMillis() < running.endAt) return
        controller.timeUp()
        val done = controller.state.value as? AiringTimerState.Done ?: return
        if (!AiringTimerService.start(context)) {
            postAiringNotification(
                context,
                AIRING_DONE_NOTIFICATION_ID,
                insistentDoneNotification(context, done),
            )
        }
    }

    companion object {
        const val ACTION_CANCEL = "ru.zaroslikov.incubator.airing.CANCEL"
        const val ACTION_FINISH = "ru.zaroslikov.incubator.airing.FINISH"
        const val ACTION_DISMISS = "ru.zaroslikov.incubator.airing.DISMISS"
        const val ACTION_TIME_UP = "ru.zaroslikov.incubator.airing.TIME_UP"
    }
}
