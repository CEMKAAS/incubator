package ru.zaroslikov.incubator.airing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила таймера проветривания, которые нельзя проверить руками за разумное время:
 * что происходит с таймером, чей срок вышел, пока процесс был мёртв; сколько минут
 * записывается при досрочном завершении; когда результат считается протухшим; и что
 * запись в настройки восстанавливается в то же состояние.
 */
class AiringTimerTest {

    private val target = AiringTimerTarget(incubatorId = 7, batchId = 3)
    private val start = 1_000_000L

    private fun running(minutes: Int = 15) = AiringTimerState.Running(
        id = start,
        target = target,
        label = "Весенняя",
        startedAt = start,
        endAt = start + minutes * MINUTE_MILLIS,
        minutes = minutes,
    )

    @Test
    fun `remaining and progress follow the clock and clamp at the ends`() {
        val timer = running(10)
        assertEquals(10 * MINUTE_MILLIS, timer.remainingMillis(start))
        assertEquals(0f, timer.progress(start))
        assertEquals(5 * MINUTE_MILLIS, timer.remainingMillis(start + 5 * MINUTE_MILLIS))
        assertEquals(0.5f, timer.progress(start + 5 * MINUTE_MILLIS))
        assertEquals(0L, timer.remainingMillis(start + 11 * MINUTE_MILLIS))
        assertEquals(1f, timer.progress(start + 11 * MINUTE_MILLIS))
    }

    @Test
    fun `early finish records elapsed minutes, at least one and at most the plan`() {
        val timer = running(15)
        // Через 20 секунд — одна минута, не ноль: крышку открывали.
        assertEquals(1, timer.elapsedMinutes(start + 20_000L))
        // 4 мин 20 с — четыре; 4 мин 40 с — пять (к ближайшей).
        assertEquals(4, timer.elapsedMinutes(start + 4 * MINUTE_MILLIS + 20_000L))
        assertEquals(5, timer.elapsedMinutes(start + 4 * MINUTE_MILLIS + 40_000L))
        // Часы перевели вперёд — не больше заданного.
        assertEquals(15, timer.elapsedMinutes(start + 40 * MINUTE_MILLIS))
        // И не меньше одной, если перевели назад.
        assertEquals(1, timer.elapsedMinutes(start - MINUTE_MILLIS))
    }

    @Test
    fun `a running timer whose term passed while the process was dead settles into a silent result`() {
        val timer = running(15)
        val settled = timer.settled(start + 16 * MINUTE_MILLIS)
        assertTrue(settled is AiringTimerState.Done)
        settled as AiringTimerState.Done
        assertEquals(15, settled.minutes)
        assertFalse("звенеть спустя срок незачем", settled.ringing)
        assertEquals(target, settled.target)
    }

    @Test
    fun `a running timer within its term is left alone`() {
        val timer = running(15)
        assertEquals(timer, timer.settled(start + 3 * MINUTE_MILLIS))
    }

    @Test
    fun `a silent result keeps waiting for the measurement until the ttl`() {
        val done = AiringTimerState.Done(
            id = start, target = target, label = "", startedAt = start,
            endAt = start + 15 * MINUTE_MILLIS, minutes = 15, ringing = true,
        )
        // Звенит — остаётся, форме нужно показать «Готово».
        assertEquals(done, done.settled(done.endAt + 1_000L))
        // Смолк — всё равно ждёт: минуты уходят только с записью замера. Именно это
        // держит их для формы, пересозданной переходом по уведомлению.
        val waiting = done.copy(ringing = false)
        assertEquals(waiting, waiting.settled(done.endAt + 1_000L))
        assertEquals(waiting, waiting.settled(done.endAt + RESULT_TTL_MILLIS))
        // …но не дольше срока.
        assertEquals(AiringTimerState.Idle, waiting.settled(done.endAt + RESULT_TTL_MILLIS + 1L))
        // Сработавший с опозданием таймер тоже протухает по тому же сроку.
        assertEquals(AiringTimerState.Idle, running(15).settled(start + 15 * MINUTE_MILLIS + RESULT_TTL_MILLIS + 1L))
    }

    @Test
    fun `a ringing flag outlives nobody - it expires with the melody`() {
        val ringing = AiringTimerState.Done(
            id = start, target = target, label = "", startedAt = start,
            endAt = start + 15 * MINUTE_MILLIS, minutes = 15, ringing = true,
        )
        // Пока срок мелодии не вышел — звенит.
        assertEquals(ringing, ringing.settled(ringing.endAt + RING_TIMEOUT_MILLIS))
        // Процесс умер во время мелодии: поднятая заново служба не должна заиграть снова,
        // а минуты — остаются для формы.
        val silent = ringing.settled(ringing.endAt + RING_TIMEOUT_MILLIS + 1L)
        assertEquals(ringing.copy(ringing = false), silent)
    }

    @Test
    fun `record round trip keeps every field`() {
        val states = listOf(
            AiringTimerState.Idle,
            running(20),
            AiringTimerState.Done(
                id = start, target = AiringTimerTarget(9), label = "Блиц", startedAt = start,
                endAt = start + 100L, minutes = 4, ringing = true,
            ),
        )
        states.forEach { state ->
            assertEquals(state, AiringTimerRecord.of(state).toState())
        }
        assertEquals(AiringTimerState.Idle, AiringTimerRecord().toState())
        assertTrue(AiringTimerTarget(9).fromIncubator)
        assertFalse(target.fromIncubator)
    }

    @Test
    fun `the plan chip exists only for a sane plan, and seeds the custom field`() {
        assertEquals(8, planChoice(8))
        assertEquals(null, planChoice(null))
        // План вне разумного — ни чипа, ни значения по умолчанию.
        assertEquals(null, planChoice(0))
        assertEquals(null, planChoice(MAX_MINUTES + 1))
        assertEquals(8, defaultMinutes(8))
        assertEquals(DEFAULT_MINUTES, defaultMinutes(null))
        assertEquals(DEFAULT_MINUTES, defaultMinutes(0))
    }

    @Test
    fun `custom minutes take digits only, from one to the ceiling`() {
        assertEquals(7, parseCustomMinutes("7"))
        assertEquals(45, parseCustomMinutes(" 45 "))
        assertEquals(MAX_MINUTES, parseCustomMinutes(MAX_MINUTES.toString()))
        assertEquals(null, parseCustomMinutes(""))
        assertEquals(null, parseCustomMinutes("0"))
        assertEquals(null, parseCustomMinutes((MAX_MINUTES + 1).toString()))
        assertEquals(null, parseCustomMinutes("2.5"))
        assertEquals(null, parseCustomMinutes("-5"))
    }
}
