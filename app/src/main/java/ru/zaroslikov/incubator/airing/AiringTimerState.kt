package ru.zaroslikov.incubator.airing

/**
 * Куда таймер проветривания отдаст свои минуты, когда кончится.
 *
 * [batchId] — закладка, в чью форму «Замеры за сегодня» поставили таймер; `0` — форма
 * замера по инкубатору (`IncubatorMeasurementSheet`), где одно показание уходит во все
 * идущие закладки. [incubatorId] нужен обоим: у шторки закладки нет своего маршрута, и
 * уведомление, ведущее к ней, обязано знать её инкубатор — ровно как напоминание.
 */
data class AiringTimerTarget(val incubatorId: Long, val batchId: Long = 0L) {
    val fromIncubator: Boolean get() = batchId == 0L
}

/**
 * Состояние единственного таймера проветривания в приложении.
 *
 * Один на всё приложение, а не по одному на закладку: проветривают, открыв крышку прибора,
 * и двух крышек разом человек не держит — а уведомление со счётчиком и мелодия у таймера
 * одни, второго рядом с ними нечем показать.
 *
 * Три состояния. [Done.ringing] — мелодия ещё играет: её гасит «Готово» в форме,
 * «Закрыл инкубатор» в уведомлении или срок [RING_TIMEOUT_MILLIS].
 *
 * **Результат ([Done]) живёт, пока замер не записан**, — а не пока его не подставили в
 * поле. Подставить его может любая форма, открытая на его цель, и не один раз: форма,
 * открытая до того, как свернули приложение, подставляет минуты в фоне, а переход по
 * уведомлению пересоздаёт экран, и новой форме нужно получить их снова. Поэтому снимает
 * результат запись замера (`AiringTimerController.resultSaved`), «Готово» в карточке или
 * срок [RESULT_TTL_MILLIS] — иначе вчерашние минуты легли бы в сегодняшний замер.
 *
 * [id] — момент запуска: по нему форма отличает результат, который уже подставила, от нового.
 */
sealed interface AiringTimerState {
    data object Idle : AiringTimerState

    data class Running(
        val id: Long,
        val target: AiringTimerTarget,
        val label: String,
        val startedAt: Long,
        val endAt: Long,
        val minutes: Int,
    ) : AiringTimerState

    data class Done(
        val id: Long,
        val target: AiringTimerTarget,
        val label: String,
        val startedAt: Long,
        val endAt: Long,
        /** Минуты, которые уйдут в замер: заданные — или прошедшие, если завершили раньше. */
        val minutes: Int,
        val ringing: Boolean,
    ) : AiringTimerState

    val targetOrNull: AiringTimerTarget?
        get() = when (this) {
            Idle -> null
            is Running -> target
            is Done -> target
        }

    val labelOrEmpty: String
        get() = when (this) {
            Idle -> ""
            is Running -> label
            is Done -> label
        }
}

/** Сколько осталось до конца, не меньше нуля. */
fun AiringTimerState.Running.remainingMillis(now: Long): Long = (endAt - now).coerceAtLeast(0L)

/** Доля пройденного, 0..1 — для кольца. */
fun AiringTimerState.Running.progress(now: Long): Float {
    val total = (endAt - startedAt).coerceAtLeast(1L)
    return ((now - startedAt).toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Минуты, которые записываются при досрочном завершении.
 *
 * Округление к ближайшей минуте, но не меньше одной: крышку открывали, и «0 мин
 * проветривания» было бы записью о том, чего не делали. И не больше заданного — раньше
 * заданного срока больше заданного не пройдёт, но часы телефона могли перевести.
 */
fun AiringTimerState.Running.elapsedMinutes(now: Long): Int {
    val elapsed = (now - startedAt).coerceAtLeast(0L)
    val rounded = ((elapsed + MINUTE_MILLIS / 2) / MINUTE_MILLIS).toInt()
    return rounded.coerceIn(1, minutes)
}

/**
 * Состояние, каким его застало «сейчас»: бегущий таймер, чей срок прошёл, пока процесс
 * был мёртв, становится законченным — без мелодии, звенеть спустя час незачем, но с
 * результатом, который форма ещё заберёт; законченный, чей результат протух
 * ([RESULT_TTL_MILLIS] от конца), уходит в покой.
 */
fun AiringTimerState.settled(now: Long): AiringTimerState = when (this) {
    AiringTimerState.Idle -> this
    is AiringTimerState.Running ->
        if (now >= endAt) {
            AiringTimerState.Done(id, target, label, startedAt, endAt, minutes, ringing = false)
                .settled(now)
        } else {
            this
        }
    is AiringTimerState.Done -> when {
        now - endAt > RESULT_TTL_MILLIS -> AiringTimerState.Idle
        // Мелодия не играет дольше своего срока, кто бы её ни включал: служба могла умереть,
        // не дождавшись конца, и флаг остался бы поднятым навсегда.
        ringing && now - endAt > RING_TIMEOUT_MILLIS -> copy(ringing = false).settled(now)
        else -> this
    }
}

/**
 * Минуты плана, годные для чипа: план дня задаёт минуты одного проветривания, и это
 * то, ради чего таймер ставят чаще всего, — он должен быть в одно касание. `null` —
 * плана нет или он вне разумного, и чипа плана в форме не будет.
 */
fun planChoice(planMinutes: Int?): Int? = planMinutes?.takeIf { it in 1..MAX_MINUTES }

/** Что стоит в поле «своё время», пока его не тронули: минуты плана, а без плана — десять. */
fun defaultMinutes(planMinutes: Int?): Int = planChoice(planMinutes) ?: DEFAULT_MINUTES

/**
 * Минуты из поля «своё время»: только цифры, от одной до [MAX_MINUTES]; всё остальное —
 * «не задано», и кнопка запуска на этом гаснет, а не запускает ноль или полтора часа.
 */
fun parseCustomMinutes(text: String): Int? =
    text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it in 1..MAX_MINUTES }

/**
 * Плоская запись состояния для `SharedPreferences`: одни примитивы, чтобы не тащить
 * сериализацию ради семи полей. `phase` — `IDLE` / `RUNNING` / `DONE`.
 */
data class AiringTimerRecord(
    val phase: String = PHASE_IDLE,
    val id: Long = 0L,
    val incubatorId: Long = 0L,
    val batchId: Long = 0L,
    val label: String = "",
    val startedAt: Long = 0L,
    val endAt: Long = 0L,
    val minutes: Int = 0,
    val ringing: Boolean = false,
) {
    fun toState(): AiringTimerState = when (phase) {
        PHASE_RUNNING -> AiringTimerState.Running(
            id, AiringTimerTarget(incubatorId, batchId), label, startedAt, endAt, minutes,
        )
        PHASE_DONE -> AiringTimerState.Done(
            id, AiringTimerTarget(incubatorId, batchId), label, startedAt, endAt, minutes,
            ringing,
        )
        else -> AiringTimerState.Idle
    }

    companion object {
        const val PHASE_IDLE = "IDLE"
        const val PHASE_RUNNING = "RUNNING"
        const val PHASE_DONE = "DONE"

        fun of(state: AiringTimerState): AiringTimerRecord = when (state) {
            AiringTimerState.Idle -> AiringTimerRecord()
            is AiringTimerState.Running -> AiringTimerRecord(
                phase = PHASE_RUNNING,
                id = state.id,
                incubatorId = state.target.incubatorId,
                batchId = state.target.batchId,
                label = state.label,
                startedAt = state.startedAt,
                endAt = state.endAt,
                minutes = state.minutes,
            )
            is AiringTimerState.Done -> AiringTimerRecord(
                phase = PHASE_DONE,
                id = state.id,
                incubatorId = state.target.incubatorId,
                batchId = state.target.batchId,
                label = state.label,
                startedAt = state.startedAt,
                endAt = state.endAt,
                minutes = state.minutes,
                ringing = state.ringing,
            )
        }
    }
}

const val MINUTE_MILLIS = 60_000L

const val DEFAULT_MINUTES = 10

/** Дольше полутора часов инкубатор открытым не держат; и до глубокого Doze за это время не дойти. */
const val MAX_MINUTES = 90

/** Сколько играет мелодия, если её никто не выключил. */
const val RING_TIMEOUT_MILLIS = 60_000L

/** Сколько результат ждёт форму, прежде чем считаться протухшим. */
const val RESULT_TTL_MILLIS = 2 * 60 * MINUTE_MILLIS
