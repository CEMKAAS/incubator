package ru.zaroslikov.incubator.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Time
import java.util.Calendar
import java.util.TimeZone

/**
 * Арифметика напоминаний.
 *
 * Всё, что здесь проверяется, когда-то работало неправильно у людей: уведомление
 * приходило в момент сохранения формы, три закладки показывали одно уведомление на всех,
 * завершённая закладка продолжала будить. Тесты держат именно эти случаи, а не «функция
 * возвращает не null».
 */
class ReminderPlanTest {

    private val moscow: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val vladivostok: TimeZone = TimeZone.getTimeZone("Asia/Vladivostok")

    // --- Разбор времени ------------------------------------------------------------

    @Test
    fun `минута суток считается от полуночи`() {
        assertEquals(0, minuteOfDay("00:00"))
        assertEquals(8 * 60, minuteOfDay("08:00"))
        assertEquals(23 * 60 + 59, minuteOfDay("23:59"))
    }

    @Test
    fun `непонятное время не разбирается`() {
        assertNull(minuteOfDay(""))
        assertNull(minuteOfDay("8"))
        assertNull(minuteOfDay("24:00"))
        assertNull(minuteOfDay("08:60"))
        assertNull(minuteOfDay("утро"))
    }

    // --- Ближайшее срабатывание ----------------------------------------------------

    @Test
    fun `время, которое сегодня ещё не наступило, ставится на сегодня`() {
        val now = moment(2026, Calendar.SEPTEMBER, 10, 6, 0, moscow)
        val expected = moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow)
        assertEquals(expected, nextReminderMillis("08:00", now, moscow))
    }

    @Test
    fun `время, которое сегодня прошло, переносится на завтра`() {
        // Ровно тот случай, из-за которого уведомление приходило сразу же: задержка
        // выходила отрицательной, а WorkManager читает её как «немедленно».
        val now = moment(2026, Calendar.SEPTEMBER, 10, 14, 0, moscow)
        val expected = moment(2026, Calendar.SEPTEMBER, 11, 8, 0, moscow)
        assertEquals(expected, nextReminderMillis("08:00", now, moscow))
    }

    @Test
    fun `совпадение с текущей минутой считается прошедшим`() {
        val now = moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow)
        val expected = moment(2026, Calendar.SEPTEMBER, 11, 8, 0, moscow)
        assertEquals(expected, nextReminderMillis("08:00", now, moscow))
    }

    @Test
    fun `час местный, а не абсолютный`() {
        // Один и тот же «08:00» в двух поясах — разные моменты. Ради этого пояс и
        // берётся заново при каждом счёте: перелёт не должен сдвигать напоминание.
        val now = moment(2026, Calendar.SEPTEMBER, 10, 6, 0, moscow)
        val inMoscow = nextReminderMillis("08:00", now, moscow)!!
        val inVladivostok = nextReminderMillis("08:00", now, vladivostok)!!
        assertNotEquals(inMoscow, inVladivostok)
        assertEquals(8, hourIn(inMoscow, moscow))
        assertEquals(8, hourIn(inVladivostok, vladivostok))
    }

    @Test
    fun `нечитаемое время не ставится вовсе`() {
        assertNull(nextReminderMillis("не время", System.currentTimeMillis(), moscow))
    }

    // --- Диспетчер: ближайший момент по всему расписанию ----------------------------

    @Test
    fun `ближайший момент выбирается из всех напоминаний`() {
        val now = moment(2026, Calendar.SEPTEMBER, 10, 6, 0, moscow)
        val jobs = listOf(job(1, "20:00"), job(2, "08:00"), job(3, "12:00"))

        val fire = nextFire(jobs, now, moscow)!!

        assertEquals(moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow), fire.at)
        assertEquals(listOf(2L), fire.jobs.map { it.batchId })
    }

    @Test
    fun `совпавшие по времени звенят вместе`() {
        // Две закладки на восемь утра — одна работа и два уведомления. Раньше они делили
        // одно уникальное имя, и вторая молча вытесняла первую.
        val now = moment(2026, Calendar.SEPTEMBER, 10, 6, 0, moscow)
        val jobs = listOf(job(1, "08:00"), job(2, "08:00"), job(3, "09:00"))

        val fire = nextFire(jobs, now, moscow)!!

        assertEquals(listOf(1L, 2L), fire.jobs.map { it.batchId })
    }

    @Test
    fun `после последнего на сегодня берётся завтрашнее утро`() {
        val now = moment(2026, Calendar.SEPTEMBER, 10, 21, 0, moscow)
        val jobs = listOf(job(1, "08:00"), job(2, "20:00"))

        val fire = nextFire(jobs, now, moscow)!!

        assertEquals(moment(2026, Calendar.SEPTEMBER, 11, 8, 0, moscow), fire.at)
        assertEquals(listOf(1L), fire.jobs.map { it.batchId })
    }

    @Test
    fun `пустому расписанию момента нет`() {
        assertNull(nextFire(emptyList(), System.currentTimeMillis(), moscow))
    }

    @Test
    fun `нечитаемые времена не мешают остальным`() {
        val now = moment(2026, Calendar.SEPTEMBER, 10, 6, 0, moscow)
        val fire = nextFire(listOf(job(1, "пусто"), job(2, "08:00")), now, moscow)!!
        assertEquals(listOf(2L), fire.jobs.map { it.batchId })
    }

    // --- Что должно было прозвенеть в свой момент -----------------------------------

    @Test
    fun `сработавшая с опозданием работа показывает своё, а не завтрашнее`() {
        // Работу отложили на сорок минут — обычное дело в Doze. Считать «что звенит
        // сейчас» нельзя: восьмичасовое напоминание уже прошло, и ответом было бы
        // завтрашнее. Поэтому считается от момента, на который работа ставилась.
        val at = moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow)
        val jobs = listOf(job(1, "08:00"), job(2, "20:00"))

        assertEquals(listOf(1L), jobsDueAt(jobs, at, moscow).map { it.batchId })
    }

    @Test
    fun `в свой момент звенят все, кто на него назначен`() {
        val at = moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow)
        val jobs = listOf(job(1, "08:00"), job(2, "08:00"), job(3, "20:00"))

        assertEquals(listOf(1L, 2L), jobsDueAt(jobs, at, moscow).map { it.batchId })
    }

    @Test
    fun `закладка, ушедшая из расписания, в свой момент не звенит`() {
        // Между постановкой и срабатыванием закладку завершили: в плане её больше нет,
        // и работа, проснувшись, не найдёт для неё ничего.
        val at = moment(2026, Calendar.SEPTEMBER, 10, 8, 0, moscow)
        assertTrue(jobsDueAt(listOf(job(2, "20:00")), at, moscow).isEmpty())
    }

    // --- Номера уведомлений ---------------------------------------------------------

    @Test
    fun `у каждой пары закладка-время свой номер уведомления`() {
        // С общей константой, как было раньше, третье уведомление затирало второе.
        val ids = listOf(
            reminderNotificationId(1, "08:00"),
            reminderNotificationId(1, "20:00"),
            reminderNotificationId(2, "08:00"),
            reminderNotificationId(2, "20:00"),
        )
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `номер уведомления не переполняет Int`() {
        val id = reminderNotificationId(999_999, "23:59")
        assertTrue("номер вышел за пределы Int: $id", id > 0)
    }

    // --- План по базе ---------------------------------------------------------------

    @Test
    fun `в план попадают только идущие закладки`() {
        val active = batch(1, "0")
        val finished = batch(2, "1")
        val times = mapOf(
            1L to listOf(Time(time = "08:00", idPT = 1)),
            2L to listOf(Time(time = "08:00", idPT = 2)),
        )

        val plan = plannedReminders(listOf(active, finished), times)

        assertEquals(listOf(1L), plan.map { it.batchId })
    }

    @Test
    fun `одинаковые времена внутри закладки схлопываются`() {
        val times = mapOf(
            1L to listOf(
                Time(time = "08:00", idPT = 1),
                Time(time = "08:00", idPT = 1, note = "второе"),
                Time(time = "20:00", idPT = 1),
            )
        )

        val plan = plannedReminders(listOf(batch(1, "0")), times)

        assertEquals(listOf("08:00", "20:00"), plan.map { it.time })
    }

    @Test
    fun `нечитаемое время в план не берётся`() {
        val times = mapOf(1L to listOf(Time(time = "", idPT = 1), Time(time = "08:00", idPT = 1)))
        val plan = plannedReminders(listOf(batch(1, "0")), times)
        assertEquals(listOf("08:00"), plan.map { it.time })
    }

    @Test
    fun `план несёт инкубатор закладки`() {
        // Уведомление ведёт в закладку внутри её инкубатора, своего маршрута у шторки нет.
        val times = mapOf(1L to listOf(Time(time = "08:00", idPT = 1)))
        val plan = plannedReminders(listOf(batch(1, "0", incubatorId = 42)), times)
        assertEquals(42L, plan.single().incubatorId)
    }

    // --- Помощники ------------------------------------------------------------------

    private fun job(batchId: Long, time: String) =
        ReminderJob(batchId, incubatorId = 1, title = "Закладка $batchId", time = time, note = "")

    private fun batch(id: Long, arhive: String, incubatorId: Long = 1) = Batch(
        id = id,
        title = "Закладка $id",
        type = "Курицы",
        data = "01.09.2026",
        eggAll = 30,
        eggAllEND = 0,
        airing = "false",
        over = "false",
        arhive = arhive,
        dateEnd = "",
        note = "",
        incubatorId = incubatorId,
    )

    private fun moment(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    private fun hourIn(millis: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}
