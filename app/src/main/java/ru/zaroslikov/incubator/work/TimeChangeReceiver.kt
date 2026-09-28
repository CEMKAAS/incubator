package ru.zaroslikov.incubator.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.zaroslikov.incubator.InventoryApplication

/**
 * Пересчитывает расписание, когда сдвинулось само время.
 *
 * «08:00» — это восемь утра там, где человек сейчас, а поставленная работа знает лишь
 * абсолютный момент, вычисленный в прежнем поясе. Без этого приёмника перелёт из Москвы
 * во Владивосток оставлял напоминание на московские восемь — то есть на час дня по
 * местным часам, — и исправлялось это лишь тогда, когда человек сам открывал приложение.
 * Для офлайнового приложения, которое как раз по напоминанию и открывают, это значит
 * «исправится, когда уже не нужно».
 *
 * Ловятся оба события: смена пояса и ручной перевод часов. Перевод стрелок на летнее
 * время системой приходит тем же `ACTION_TIME_CHANGED`.
 *
 * Приёмник объявлен в манифесте: приложение в этот момент не запущено — в том и дело.
 * Работа делается не здесь, а в [ReminderSync], который сам уходит в фон; приёмнику
 * достаточно поднять контейнер. Своего `goAsync` он не берёт нарочно: удерживать
 * приёмник ради задачи, которая всё равно живёт в области видимости приложения, значит
 * держать процесс дольше, чем нужно, — а сам пересчёт занимает доли секунды и переживёт
 * возврат из `onReceive`.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIMEZONE_CHANGED && action != Intent.ACTION_TIME_CHANGED) return

        val container = (context.applicationContext as? InventoryApplication)?.container
        if (container == null) {
            Log.w(TAG, "Время сменилось, но контейнер не готов")
            return
        }
        Log.i(TAG, "Время сменилось ($action), пересчитываем расписание")
        container.reminderSync.syncInBackground()
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
    }
}
