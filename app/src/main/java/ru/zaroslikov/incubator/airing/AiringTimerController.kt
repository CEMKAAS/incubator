package ru.zaroslikov.incubator.airing

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.settings.AppSettings

/**
 * Таймер проветривания: единственный писатель [AiringTimerState] и единственный, кто
 * заводит и гасит [AiringTimerService].
 *
 * В `AppContainer`, а не в ViewModel формы, по той же причине, что и перенос базы: таймер
 * ставят из формы и уходят — в другую шторку, в другое приложение, в карман, — а кончиться
 * он обязан там, где его застанет конец, и вписать минуты в форму, которая к тому моменту,
 * возможно, ещё не открыта. Состояние — [StateFlow]: форма подписывается, когда открыта,
 * служба — пока идёт; ни та ни другая его не хранят, за это отвечает [AppSettings.airingTimer].
 *
 * Все переходы синхронные и на главном потоке: их зовут форма (из `viewModelScope`),
 * служба (из своего scope на `Main`) и приёмник действий уведомления (из `onReceive`), и
 * ни одному из них не нужно ждать. Каждый переход сначала пишется на диск, потом в поток:
 * служба, поднятая системой заново после смерти процесса, читает диск.
 */
class AiringTimerController(
    private val context: Context,
    private val settings: AppSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(settings.airingTimer.settled(now()))
    val state: StateFlow<AiringTimerState> = _state.asStateFlow()

    /**
     * Формы замера, открытые на экране прямо сейчас, — по их целям. Плавающая кнопка
     * таймера (`AiringTimerFab`) висит поверх всего приложения, пока таймер идёт, и
     * прячется на той единственной форме, где стоит его карточка: там всё уже видно.
     * Форма отмечается, пока она в композиции ([formShown] / [formHidden]), а не
     * «пока жива её ViewModel» — та живёт экраном и не знает, свёрнута ли шторка.
     */
    private val _openForms = MutableStateFlow<Set<AiringTimerTarget>>(emptySet())
    val openForms: StateFlow<Set<AiringTimerTarget>> = _openForms.asStateFlow()

    fun formShown(target: AiringTimerTarget) {
        _openForms.value = _openForms.value + target
    }

    fun formHidden(target: AiringTimerTarget) {
        _openForms.value = _openForms.value - target
    }

    /**
     * Сверка при запуске приложения (`MainActivity.onCreate`): срок бегущего таймера мог
     * выйти, пока процесс был мёртв, — тогда результат ждёт форму, а звенеть поздно; а
     * бегущий таймер без службы — служба погибла вместе с процессом — получает её обратно.
     */
    fun refresh() {
        val settled = _state.value.settled(now())
        if (settled != _state.value) write(settled)
        if (settled is AiringTimerState.Running) AiringTimerService.start(context)
    }

    /**
     * Ставит таймер. Отказывает, пока не в покое: у идущего — уведомление и мелодия, и
     * второму рядом с ними нечем показаться, а незабранный результат новый запуск затёр
     * бы без следа. Форма и не предлагает запуск в этих состояниях, но composition
     * переживает состояние, из которого построена.
     */
    fun start(target: AiringTimerTarget, label: String, minutes: Int): Boolean {
        if (_state.value !is AiringTimerState.Idle) return false
        val total = minutes.coerceIn(1, MAX_MINUTES)
        val startedAt = now()
        write(
            AiringTimerState.Running(
                id = startedAt,
                target = target,
                label = label,
                startedAt = startedAt,
                endAt = startedAt + total * MINUTE_MILLIS,
                minutes = total,
            ),
        )
        AiringTimerService.start(context)
        Analytics.report(
            Events.AIRING_TIMER_STARTED,
            mapOf("Минут" to total, "Из инкубатора" to target.fromIncubator),
        )
        return true
    }

    /** «Отменить»: таймер снимается, в замер ничего не идёт. */
    fun cancel() {
        val running = _state.value as? AiringTimerState.Running ?: return
        write(AiringTimerState.Idle)
        // «Минут» — сколько записано; при отмене не записывается ничего.
        reportFinished(0, outcome = "отменён")
    }

    /**
     * «Завершить» раньше срока: крышку уже закрыли, в замер идут прошедшие минуты
     * ([elapsedMinutes]). Без мелодии — человек стоит у прибора и сам всё сделал.
     */
    fun finishNow() {
        val running = _state.value as? AiringTimerState.Running ?: return
        val minutes = running.elapsedMinutes(now())
        write(running.done(minutes, ringing = false))
        reportFinished(minutes, outcome = "досрочно")
    }

    /** Срок вышел — зовёт служба. Мелодия включается, результат ждёт форму. */
    fun timeUp() {
        val running = _state.value as? AiringTimerState.Running ?: return
        write(running.done(running.minutes, ringing = true))
        reportFinished(running.minutes, outcome = "вовремя")
    }

    /** Мелодию выключили — «Готово» в форме, «Закрыл инкубатор» в уведомлении или срок. */
    fun stopRinging() {
        val done = _state.value as? AiringTimerState.Done ?: return
        if (!done.ringing) return
        write(done.copy(ringing = false).settled(now()))
    }

    /**
     * Форма записала минуты в поле. Тот же [id], что у результата, — чтобы форма,
     * открытая второй раз, не забрала его снова: состояние после этого уходит в покой,
     * как только смолкнет мелодия.
     */
    fun take(id: Long) {
        val done = _state.value as? AiringTimerState.Done ?: return
        if (done.id != id || done.taken) return
        write(done.copy(taken = true).settled(now()))
    }

    /** «Готово» в форме: мелодия смолкает, результат больше никого не ждёт. */
    fun dismiss() {
        if (_state.value !is AiringTimerState.Done) return
        write(AiringTimerState.Idle)
    }

    private fun AiringTimerState.Running.done(minutes: Int, ringing: Boolean) =
        AiringTimerState.Done(
            id = id,
            target = target,
            label = label,
            startedAt = startedAt,
            endAt = now(),
            minutes = minutes,
            ringing = ringing,
            taken = false,
        )

    private fun write(state: AiringTimerState) {
        settings.airingTimer = state
        _state.value = state
        // Уведомление «время вышло» после ухода службы держится само, и снять его,
        // когда результат забран или отброшен, больше некому: службы уже нет. Снимается и
        // при новом запуске: его намерение ведёт к цели нового таймера, а текст — о старом.
        if (state is AiringTimerState.Idle || state is AiringTimerState.Running) {
            NotificationManagerCompat.from(context).cancel(AIRING_DONE_NOTIFICATION_ID)
        }
        // Запасной будильник живёт ровно столько, сколько бежит таймер: см. [AiringTimerAlarm].
        if (state is AiringTimerState.Running) AiringTimerAlarm.schedule(context, state.endAt)
        else AiringTimerAlarm.cancel(context)
    }

    private fun reportFinished(minutes: Int, outcome: String) {
        Analytics.report(
            Events.AIRING_TIMER_FINISHED,
            mapOf("Минут" to minutes, "Исход" to outcome),
        )
    }
}
