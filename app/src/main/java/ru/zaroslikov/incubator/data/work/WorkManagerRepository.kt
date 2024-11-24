package ru.zaroslikov.incubator.data.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ru.zaroslikov.incubator.data.ferma.Time
import java.util.Calendar
import java.util.concurrent.TimeUnit


class WorkManagerRepository(private var context: Context) : WorkRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun scheduleReminder(list: MutableList<Time>, name: String) {

        list.forEach {
                val sd = it.time.split(":")

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, sd[0].toInt())
                    set(Calendar.MINUTE, sd[1].toInt())
                    set(Calendar.SECOND, 0)
                }

                val data = Data.Builder()
                    .putString("name", name)
                    .putString("CHANNEL_ID", CHANNEL_ID)
                    .putString("NOTIFICATION_TITLE", NOTIFICATION_TITLE.toString())
                    .putInt("NOTIFICATION_ID", NOTIFICATION_ID)
                    .build()


                val workRequest =
                    PeriodicWorkRequestBuilder<ReminderWorker>(
                        1,
                        TimeUnit.DAYS
                    ).setInitialDelay(
                        calendar.timeInMillis - System.currentTimeMillis(),
                        TimeUnit.MILLISECONDS
                    ).setInputData(data).addTag(name).build()

                workManager.enqueueUniquePeriodicWork(
                    it.time,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    workRequest
                )
        }
    }

    override fun cancelAllNotifications(name: String) {
        workManager.cancelAllWorkByTag(name)
    }
}

