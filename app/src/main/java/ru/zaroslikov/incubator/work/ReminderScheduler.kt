package ru.zaroslikov.incubator.work

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Единственная работа-напоминание и всё, что с ней делают.
 *
 * **Работа одна на приложение, и ставится она на ближайший момент.** Раньше их было по
 * одной на каждую пару «закладка × время» — двадцать идущих закладок по четыре
 * напоминания давали восемьдесят периодических работ при потолке планировщика в
 * пятьдесят, и лишние молча не доезжали до системы. Теперь [ReminderWorker] просыпается,
 * показывает всё, что пришлось на его момент, и ставит следующую работу сам; сколько бы
 * закладок ни завели, работа остаётся одна.
 *
 * **И она одноразовая, а не периодическая.** У периодической точен только первый запуск,
 * заданный `setInitialDelay`: `flex` по умолчанию равен периоду, то есть суткам, и
 * дальше система вправе выполнить работу в любой момент следующих суток. Выравнивалось
 * это лишь тем, что человек открывал приложение. Одноразовая работа задаёт задержку
 * заново перед каждым срабатыванием — от текущего времени и в текущем часовом поясе, —
 * так что ни дрейфа, ни перелёта через пояс она не накапливает.
 */
class ReminderScheduler(private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Приводит расписание к [moment]: `null` — напоминать нечего, работу снять.
     *
     * **Это сверка, а не пересборка, и разница здесь дороже, чем кажется.** Сначала было
     * «снять всё и поставить заново», и в этом пряталась потеря сегодняшнего напоминания.
     * Работу, которой пришёл срок, система выполняет не в ту же секунду: в Doze она
     * откладывается на десятки минут. Открытое в это время приложение снимало ещё не
     * выполненную работу, а следующий момент по построению лежит **строго в будущем** —
     * то есть уже завтра. Сегодняшнее напоминание исчезало молча, и у того, кто открывает
     * приложение по утрам, это повторялось бы каждый день.
     *
     * Поэтому просроченную работу трогать нельзя: она ещё сработает — и, сработав, сама
     * пересчитает следующий момент по свежей базе. Сверка оставляет её как есть,
     * переставляет только ту, чей срок разошёлся с планом, и ставит недостающую.
     */
    fun schedule(moment: FireMoment?) {
        val existing = liveReminder()

        if (moment == null) {
            if (existing != null) {
                Log.i(TAG, "Напоминать нечего, работа снята")
                workManager.cancelUniqueWork(REMINDER_WORK_NAME)
            }
            return
        }

        val next = existing?.nextScheduleTimeMillis
        val now = System.currentTimeMillis()
        when {
            existing == null -> {
                Log.i(TAG, "Работы не было, ставим на ${stamp(moment.at)}")
                enqueue(moment)
            }
            // Срок уже прошёл, а работа жива: она просрочена и вот-вот выполнится. Это и
            // есть тот случай, ради которого написана сверка, — не трогаем.
            next == null || next <= now -> Log.i(TAG, "Работа просрочена, ждём её срабатывания")
            // Плановый момент сместился — переставить. Допуск нужен потому, что WorkManager
            // хранит срок с точностью до своей записи, и сравнение «до миллисекунды»
            // переставляло бы работу на каждой сверке.
            abs(next - moment.at) > DRIFT_TOLERANCE_MILLIS -> {
                Log.i(TAG, "Момент сместился: ${stamp(next)} -> ${stamp(moment.at)}")
                enqueue(moment)
            }
            else -> Log.i(TAG, "Работа уже стоит на ${stamp(next)}")
        }
    }

    /** Живая работа-напоминание; `null` — её нет, снята или уже выполнена. */
    private fun liveReminder(): WorkInfo? =
        workManager.getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()
            .firstOrNull { !it.state.isFinished }

    /** Ставит работу на момент [moment], заменяя ту, что стояла. */
    fun enqueue(moment: FireMoment) {
        val delay = moment.at - System.currentTimeMillis()
        Log.i(
            TAG,
            "Напоминание на ${stamp(moment.at)}, через ${delay / 60_000} мин, " +
                "закладок: ${moment.jobs.size}",
        )

        // Работа несёт свой момент и ничего больше: что именно на него пришлось, она
        // считает при срабатывании по базе — за сутки ожидания закладку могли завершить,
        // переименовать или убрать у неё это время.
        val data = Data.Builder()
            .putLong(KEY_FIRE_AT, moment.at)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(REMINDER_TAG)
            .build()

        workManager.enqueueUniqueWork(
            REMINDER_WORK_NAME,
            // REPLACE, а не KEEP: сюда приходят ровно тогда, когда момент изменился, и
            // старая работа с прежним сроком здесь именно то, от чего избавляются.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Снимает вообще всю работу приложения — разовая уборка после обновления.
     *
     * Напоминания прежних версий помечены названием закладки, а версии после них — по
     * работе на каждое её время; ни нынешнее уникальное имя, ни метка их всех не находят,
     * а перебрать названия закладок недостаточно — закладку могли переименовать между
     * постановкой и обновлением. Единственный способ достать их все — снять всё;
     * расписание тут же собирается заново по базе ([ReminderSync]), так что терять нечего.
     * Вызывается один раз, см. `AppSettings.remindersMigrated`.
     *
     * **Дожидается конца, а не бросает вызов на полпути.** `cancelAllWork` возвращает
     * `Operation` и выполняется в своём исполнителе; флаг «прибрано» поднимается сразу
     * следующей строкой, и без ожидания смерть процесса между ними оставила бы старую
     * работу живой навсегда — доставать её после этого нечем, второй уборки уже не будет.
     * Блокирующее ожидание допустимо: вызов идёт с `Dispatchers.IO`.
     */
    fun purgeLegacyWork() {
        workManager.cancelAllWork().result.get()
    }

    /** Момент в местном времени — только для журнала. */
    private fun stamp(millis: Long): String =
        java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(java.util.Date(millis))

    private companion object {
        const val TAG = "ReminderScheduler"

        /** Насколько срок работы может разойтись с планом, прежде чем её переставят. */
        const val DRIFT_TOLERANCE_MILLIS = 60_000L
    }
}
