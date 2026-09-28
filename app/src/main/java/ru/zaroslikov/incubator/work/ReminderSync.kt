package ru.zaroslikov.incubator.work

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.settings.AppSettings

/**
 * Расписание напоминаний, выведенное из базы.
 *
 * Существует ради двух вещей сразу — переноса данных и любой правки закладок.
 *
 * Уведомления живут не в базе, а в собственном хранилище WorkManager, и экспорт/импорт их
 * не касается: файл базы уезжает на другой телефон целиком — вместе с закладками и
 * строками `Batch_time`, — а поставленная работа остаётся на старом. На новом телефоне
 * после импорта в базе есть напоминания, а будить некому; на старом, если базу заменили
 * чужой, наоборот остаётся работа от закладок, которых больше нет. Ни то, ни другое
 * нельзя починить в момент импорта: подмена базы заканчивается смертью процесса (см.
 * `DataTransferController`). Поэтому расписание не хранится, а **выводится**: при каждом
 * открытии приложения оно собирается заново из того, что сейчас в базе.
 *
 * И по той же причине это единственный ответ на «закладку изменили» ([refreshReminders]):
 * завершение, удаление, возврат в работу, правка времён в форме — всё это записи в базу,
 * после которых расписание надо просто пересчитать. Прежде каждый такой случай сам решал,
 * снять напоминания или поставить, и один из них решал неверно — сохранение формы
 * завершённой закладки возвращало ей будильники.
 *
 * Вызывается из `MainActivity`, а не из `Application.onCreate`: процесс поднимает и сам
 * WorkManager, когда приходит время сработать напоминанию, и пересчёт в этот момент снял
 * бы работу, которая прямо сейчас выполняется.
 */
class ReminderSync(
    private val itemsRepository: ItemsRepository,
    private val scheduler: ReminderScheduler,
    private val appSettings: AppSettings,
) : WorkRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Два пересчёта разом сняли бы работу, которую ставит соседний. */
    private val lock = Mutex()

    override fun refreshReminders() {
        syncInBackground()
    }

    /** Пересобирает расписание в фоне; вызывающему ждать нечего. */
    fun syncInBackground() {
        scope.launch {
            runCatching { sync() }.onFailure { Log.w(TAG, "Пересчёт расписания не удался", it) }
        }
    }

    /**
     * Считает план по базе и приводит к нему работу.
     *
     * **База читается до того, как что-либо трогается в планировщике.** Упавшее чтение
     * оставляет прежнее расписание живым, а не человека без напоминаний; раньше здесь
     * стояло безусловное снятие, и наполовину выполненная пересборка означала тишину до
     * следующего открытия приложения.
     *
     * Чтение ограничено сроком по той же причине, по какой оно ограничено в
     * [ReminderWorker]: `try/catch` ловит исключение, но не зависание, а база бывает
     * занята — её подменяет импорт, соединение закрыто. Без срока пересчёт молча висел
     * бы в фоне, удерживая замок, и следующий не начался бы никогда.
     *
     * Остаётся одна гонка, и она безобидна. Закладку могут завершить между чтением базы и
     * постановкой: план тогда останется с её напоминанием, потому что в прочитанном виде
     * закладка ещё числилась идущей. Разбирается это само — работа проснётся, перечитает
     * базу, увидит архив и просто ничего не покажет.
     */
    suspend fun sync() = lock.withLock {
        // Разовая уборка за прежними версиями: их работы помечены названием закладки либо
        // стоят по одной на каждое время закладки, и нынешнее уникальное имя до них не
        // дотягивается. Флаг поднимается только после того, как уборка действительно
        // закончилась, — иначе смерть процесса посередине оставила бы старую работу
        // навсегда, доставать её было бы нечем.
        if (!appSettings.remindersMigrated) {
            runCatching { scheduler.purgeLegacyWork() }
                .onSuccess { appSettings.remindersMigrated = true }
                .onFailure { Log.w(TAG, "Уборка старых работ не удалась", it) }
        }

        val jobs = readPlan()
        if (jobs == null) {
            Log.w(TAG, "База не прочитана, расписание оставлено как есть")
            return@withLock
        }

        scheduler.schedule(nextFire(jobs, System.currentTimeMillis()))
    }

    /**
     * Всё, что должно звенеть, по нынешней базе; `null` — прочитать не удалось.
     *
     * Времена читаются одним обходом идущих закладок. Запрос на закладку здесь всё ещё
     * один — `Batch_time.idPT` с четырнадцатой версии схемы под индексом, так что это
     * поиск, а не полный просмотр таблицы на каждую закладку.
     */
    private suspend fun readPlan(): List<ReminderJob>? = try {
        withTimeoutOrNull(DB_READ_TIMEOUT_MILLIS) {
            val batches = itemsRepository.getAllBatches().first().filter { it.wantsReminders() }
            val timesByBatch = batches.associate { it.id to itemsRepository.getTimeList(it.id) }
            plannedReminders(batches, timesByBatch)
        }
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        // Отмену пробрасываем: проглоченная, она выглядела бы как «в базе ничего нет».
        throw cancellation
    } catch (error: Throwable) {
        Log.w(TAG, "Чтение расписания не удалось", error)
        null
    }

    private companion object {
        const val TAG = "ReminderSync"

        /** Сколько ждать базу, прежде чем считать, что прочитать её не удалось. */
        const val DB_READ_TIMEOUT_MILLIS = 10_000L
    }
}
