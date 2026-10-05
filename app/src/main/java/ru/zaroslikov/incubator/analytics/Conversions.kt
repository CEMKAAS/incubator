package ru.zaroslikov.incubator.analytics

import java.util.concurrent.TimeUnit

/**
 * Этапы конверсии — «впервые сделал то, ради чего приложение ставят».
 *
 * Воронку по обычным событиям AppMetrica строит и сама, но цель конверсии (и в отчёте
 * «Конверсии», и в рекламном кабинете, куда AppMetrica отдаёт события) — это **одно
 * событие на установку**: «Замер записан» приходит сотни раз за инкубацию, и цель по
 * нему считала бы замеры, а не людей. Поэтому у каждого этапа своё имя, и шлётся оно
 * ровно один раз — в ту минуту, когда установка впервые доходит до этого шага, с
 * параметром «Дней с установки»: скорость доходимости — второй ответ, который нужен от
 * конверсии.
 *
 * Этап привязан к событию воронки ([trigger]), а не расставлен по экранам вторым
 * вызовом: место, где пишется замер, и так отчитывается [Events.MEASUREMENT_SAVED], и
 * второй вызов рядом с ним рано или поздно забыли бы в третьем месте записи.
 *
 * **Имена — часть ряда статистики, как и у [Events]: не переименовывать.**
 */
enum class Milestone(val event: String, private val trigger: Set<String>) {
    FIRST_INCUBATOR("Конверсия: первый инкубатор", setOf(Events.INCUBATOR_CREATED)),
    FIRST_BATCH("Конверсия: первая закладка", setOf(Events.BATCH_CREATED)),
    FIRST_MEASUREMENT("Конверсия: первый замер", setOf(Events.MEASUREMENT_SAVED)),
    FIRST_CANDLING("Конверсия: первое овоскопирование", setOf(Events.CANDLING_SAVED)),
    FIRST_FINISH(
        "Конверсия: первая завершённая инкубация",
        setOf(Events.FINISH_ON_TIME, Events.FINISH_EARLY),
    ),

    /** Птенцы есть: [Events.HATCH_CELEBRATED] шлётся только тогда. */
    FIRST_HATCH("Конверсия: первый вывод", setOf(Events.HATCH_CELEBRATED)),

    /**
     * Новая закладка после первой завершённой инкубации — человек вернулся на второй
     * круг. Главный признак того, что приложение прижилось: одна инкубация бывает и у
     * того, кто попробовал и бросил.
     *
     * Отсчитывается от завершения, а не от второго [Events.BATCH_CREATED]: партия из двух
     * пород создаёт две закладки одним нажатием, и «вторая закладка» засчитывалась бы
     * в ту же секунду, что и первая.
     */
    REPEAT_BATCH("Конверсия: повторная закладка", setOf(Events.BATCH_CREATED)) {
        override fun reachableFrom(reached: Set<Milestone>) = FIRST_FINISH in reached
    };

    /** Можно ли засчитать этап при уже пройденных [reached]. */
    protected open fun reachableFrom(reached: Set<Milestone>): Boolean = true

    internal fun triggeredBy(event: String, reached: Set<Milestone>): Boolean =
        event in trigger && this !in reached && reachableFrom(reached)

    companion object {
        /**
         * Ключ в настройках — имя события, а не `name` константы: релиз минифицирован, и
         * R8 переименовал бы константы, превратив отметки прошлой версии в незнакомые
         * строки (та же ловушка, что у `ThemeMode`). Имя события не меняется по правилу
         * [Events], и для R8 это обычная строка.
         */
        fun fromKey(key: String): Milestone? = entries.firstOrNull { it.event == key }
    }
}

/**
 * Этапы, которые засчитывает событие [event] при уже пройденных [reached].
 *
 * [Milestone.REPEAT_BATCH] смотрит на [reached] до этого события: завершение и новая
 * закладка — разные нажатия, одним событием оба этапа не взять.
 */
fun milestonesFor(event: String, reached: Set<Milestone>): List<Milestone> =
    Milestone.entries.filter { it.triggeredBy(event, reached) }

/**
 * Этапы, уже пройденные по данным в базе, — их отмечают молча, без событий.
 *
 * Без этого обновившийся с тремя инкубаторами прислал бы «первый инкубатор» в день,
 * когда завёл четвёртый, и конверсия по версии с этим кодом вышла бы фантастической.
 * Так же и база, приехавшая импортом: её закладки пройдены не здесь, но и первыми их
 * уже не назовёшь.
 *
 * Повторная закладка по базе угадывается приближённо — закладок больше одной и хотя бы
 * одна кончилась. Ошибка здесь стоит одного несосланного события, а не ложного.
 */
fun milestonesInDatabase(
    incubators: Int,
    batches: Int,
    hasMeasurements: Boolean,
    candlings: Int,
    finishedBatches: Int,
    batchesWithChicks: Int,
): Set<Milestone> = buildSet {
    if (incubators > 0) add(Milestone.FIRST_INCUBATOR)
    if (batches > 0) add(Milestone.FIRST_BATCH)
    if (hasMeasurements) add(Milestone.FIRST_MEASUREMENT)
    if (candlings > 0) add(Milestone.FIRST_CANDLING)
    if (finishedBatches > 0) add(Milestone.FIRST_FINISH)
    if (batchesWithChicks > 0) add(Milestone.FIRST_HATCH)
    if (batches > 1 && finishedBatches > 0) add(Milestone.REPEAT_BATCH)
}

/**
 * Полных суток с установки. `firstInstallTime` переживает обновления, так что это
 * возраст установки, а не версии; часы, переведённые назад, дают ноль, а не минус.
 */
fun daysSinceInstall(installedAt: Long, now: Long): Int =
    TimeUnit.MILLISECONDS.toDays((now - installedAt).coerceAtLeast(0)).toInt()
