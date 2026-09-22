package ru.zaroslikov.incubator.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.MainActivity
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.di.AppContainer
import ru.zaroslikov.incubator.settings.AppSettings

/**
 * Единственная работа-напоминание: показывает всё, что пришлось на её момент, и ставит
 * следующую.
 *
 * Работа просыпается и **перечитывает расписание из базы**, а не верит тому, с чем её
 * поставили: за сутки ожидания закладку могли завершить, удалить, переименовать или
 * убрать у неё это время. Это и есть ответ на «после завершения уведомления приходить не
 * должны» — база отвечает на него сама, без надежды на то, что кто-то не забыл снять
 * работу. С собой работа несёт только свой момент ([KEY_FIRE_AT]).
 *
 * Момент нужен именно как якорь: система выполняет работу не в ту же секунду, в Doze её
 * откладывают на десятки минут, и «что должно звенеть сейчас» дало бы уже завтрашний
 * список. [jobsDueAt] считает от заданного момента, а не от текущего времени.
 *
 * **Следующая работа ставится здесь же, и это единственное, что продолжает расписание.**
 * Периодической работы больше нет — у неё точен только первый запуск, — так что порвать
 * цепочку нельзя: показ уведомлений обёрнут так, чтобы дойти до постановки в любом
 * случае, а неудачное чтение базы возвращает [Result.retry], а не «успех без плана».
 */
class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? InventoryApplication)?.container
        val now = System.currentTimeMillis()
        // Ноль — работа поставлена без момента (такого быть не должно): считаем, что
        // сработала вовремя, и берём текущее время.
        val fireAt = inputData.getLong(KEY_FIRE_AT, 0L).takeIf { it > 0L } ?: now

        val plan = readPlan(container)
        if (plan == null) {
            // Базу прочитать не удалось — её мог подменять импорт, — и это не повод ни
            // показывать что-то наугад, ни обрывать расписание. Повтор с отступом:
            // работа не выполнена, и WorkManager вернётся к ней сам.
            Log.w(TAG, "Расписание не прочитано, повтор")
            return Result.retry()
        }

        val due = jobsDueAt(plan, fireAt)

        // Общий выключатель с экрана настроек. Проверяется здесь, а не снятием работы:
        // расписание принадлежит закладкам, и снятая работа не воскресла бы сама.
        // Выключенные напоминания просыпаются, молчат и ставят следующую — включив их,
        // человек получает работающее расписание, а не тишину до перезапуска приложения.
        val settings = container?.appSettings ?: AppSettings(applicationContext)
        if (settings.remindersEnabled) {
            due.forEach { job -> runCatching { notify(job, plan) }.onFailure { logShowFailure(it) } }
        }

        // Следующий момент считается от «сейчас», но не раньше отработанного: сработав с
        // опозданием, работа не должна поставить себя на тот же момент второй раз.
        scheduleNext(plan, maxOf(now, fireAt))
        return Result.success()
    }

    /** Показывает одно напоминание и отчитывается о нём. */
    private fun notify(job: ReminderJob, plan: List<ReminderJob>) {
        val shown = showReminder(
            context = applicationContext,
            title = job.title.ifBlank { NOTIFICATION_TITLE.toString() },
            text = job.note.ifBlank { NOTIFICATION_TITLE.toString() },
            notificationId = reminderNotificationId(job.batchId, job.time),
            incubatorId = job.incubatorId,
            batchId = job.batchId,
        )

        // Пара к «Переход по уведомлению»: их отношение и есть отклик на напоминания —
        // у офлайнового приложения единственный рычаг удержания, и без знаменателя
        // судить о нём нельзя. «Показано» отправляется и тогда, когда уведомление не
        // прошло (нет разрешения): это разные исходы одного и того же срабатывания, и
        // разделить их должен параметр, а не пропущенное событие — пропущенное молча
        // завысило бы отклик ровно на тех, кто напоминаний не видел.
        Analytics.report(
            Events.REMINDER_SHOWN,
            mapOf(
                "Время" to job.time,
                "Показано" to shown,
                // Свой текст напоминания против стандартного: заполняют ли поле в форме.
                "Свой текст" to job.note.isNotBlank(),
                // Сколько напоминаний совпало по времени: одна работа показывает их
                // разом, и «два уведомления в восемь утра» — это про расписание, а не
                // про два срабатывания.
                "Разом" to plan.count { it.time == job.time },
            ),
        )
    }

    /**
     * Ставит следующее срабатывание. Плана нет — расписание кончилось, и работы не будет
     * до ближайшей сверки (её делает каждый запуск приложения).
     *
     * Постановка идёт **после** показа уведомлений, и порядок этот вынужденный: работа
     * ставится под тем же уникальным именем, под которым выполняется сама, а такая
     * постановка отменяет текущую. Отменённой к этому моменту уже нечего делать —
     * уведомления показаны, — а новая работа в очереди стоит.
     */
    private fun scheduleNext(plan: List<ReminderJob>, from: Long) {
        val next = nextFire(plan, from)
        if (next == null) {
            Log.i(TAG, "Напоминать больше нечего")
            return
        }
        ReminderScheduler(applicationContext).enqueue(next)
    }

    /**
     * Всё расписание из базы, или `null` — прочитать не удалось.
     *
     * Отмену корутины ловить **нельзя**, и это не мелочь: работу останавливают штатно —
     * снятием, — и проглоченная `CancellationException` выглядела бы отсюда как «в базе
     * ничего нет». Раньше она такой и выглядела, а по этому признаку снималось всё
     * расписание закладки.
     *
     * Срок нужен потому, что `try/catch` ловит исключение, но не зависание, а зависнуть
     * тут есть на чём: работу будят в том числе в те секунды, когда базу подменяет
     * импорт и соединение с ней закрыто. Без срока работа стояла бы, удерживая wakelock,
     * до десятиминутного предела WorkManager.
     */
    private suspend fun readPlan(container: AppContainer?): List<ReminderJob>? {
        if (container == null) return null
        return try {
            withTimeoutOrNull(DB_READ_TIMEOUT_MILLIS) {
                val batches = container.itemsRepository.getAllBatches().first()
                    .filter { it.wantsReminders() }
                val times = batches.associate { it.id to container.itemsRepository.getTimeList(it.id) }
                plannedReminders(batches, times)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "Чтение расписания не удалось", error)
            null
        }
    }

    private fun logShowFailure(error: Throwable) {
        Log.w(TAG, "Уведомление не показано", error)
    }

    private companion object {
        const val TAG = "ReminderWorker"

        /** Сколько ждать базу, прежде чем считать, что прочитать её не удалось. */
        const val DB_READ_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Показывает напоминание.
 *
 * [notificationId] свой у каждой пары «закладка + время»: с общей константой, как было
 * раньше, второе уведомление затирало первое, и из трёх идущих закладок в шторке
 * оставалась одна.
 *
 * Возвращает, показали ли уведомление на самом деле: без разрешения на уведомления
 * работа доходит до конца и молча ничего не показывает, и знать эту разницу нужно как
 * минимум аналитике — иначе отклик на напоминания считался бы от числа, в которое
 * входят те, кто их и не видел.
 */
fun showReminder(
    context: Context,
    title: String,
    text: String,
    notificationId: Int,
    incubatorId: Long,
    batchId: Long,
): Boolean {
    ensureChannel(context)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.baseline_egg_24)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVibrate(LongArray(0))
        .setContentIntent(reminderIntent(context, notificationId, incubatorId, batchId))
        .setAutoCancel(true)

    val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    if (canNotify) {
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    return canNotify
}

/**
 * Заводит канал уведомлений, если его ещё нет.
 *
 * Проверка не ради экономии вызова: канал, однажды созданный, принадлежит человеку —
 * он мог приглушить его в системных настройках, и повторное создание с
 * `IMPORTANCE_HIGH` система всё равно проигнорирует. Пересоздавая канал при каждом
 * показе, код обещал важность, которой не мог обеспечить.
 */
private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return

    val channel = NotificationChannel(
        CHANNEL_ID,
        VERBOSE_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH,
    ).apply { description = VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION }
    manager.createNotificationChannel(channel)
}

/**
 * Куда ведёт нажатие по уведомлению: инкубатор закладки, а поверх него — её шторка.
 *
 * Раньше намерение было одно на все уведомления и без данных: открывался главный экран,
 * и до закладки, о которой напомнили, надо было ещё дойти. Идентификаторы едут в
 * `extras`, а разбирает их [MainActivity].
 *
 * Код запроса — тот же номер, что и у уведомления, и это важнее, чем кажется:
 * `PendingIntent` различаются по нему, и с общим нулём, как было раньше, второе
 * уведомление молча переписывало бы намерение первого (`FLAG_UPDATE_CURRENT`), уводя
 * оба на одну и ту же закладку.
 */
private fun reminderIntent(
    context: Context,
    requestCode: Int,
    incubatorId: Long,
    batchId: Long,
): PendingIntent {
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        flags = flags or PendingIntent.FLAG_IMMUTABLE
    }

    return PendingIntent.getActivity(
        context,
        requestCode,
        reminderContentIntent(context, incubatorId, batchId),
        flags,
    )
}

/**
 * Само намерение, которое уносит из уведомления в закладку.
 *
 * Отдельно от [PendingIntent] ради проверяемости: `PendingIntent` наружу своего
 * содержимого не показывает, поэтому «уведомление ведёт куда надо» иначе никак не
 * проверить — а разбирает его [ru.zaroslikov.incubator.ReminderTarget] на той стороне,
 * и эти двое обязаны понимать друг друга.
 *
 * Задача очищается (`CLEAR_TASK`), потому что закладка должна оказаться на своём месте
 * — внутри своего инкубатора, с возвратом к списку инкубаторов, — а не поверх того,
 * что человек делал в приложении в прошлый раз.
 */
fun reminderContentIntent(context: Context, incubatorId: Long, batchId: Long): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(EXTRA_INCUBATOR_ID, incubatorId)
        putExtra(EXTRA_BATCH_ID, batchId)
    }
