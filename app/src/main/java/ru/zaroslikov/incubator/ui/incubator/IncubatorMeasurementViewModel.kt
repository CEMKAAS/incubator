package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.airing.AiringTimerController
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.airing.AiringTimerTarget
import ru.zaroslikov.incubator.airing.settled
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.batch.AiringTimerAction
import ru.zaroslikov.incubator.ui.batch.AiringTimerSlot
import ru.zaroslikov.incubator.ui.batch.MeasurementForm
import ru.zaroslikov.incubator.ui.batch.batchStartMoment
import ru.zaroslikov.incubator.ui.batch.incubationDay
import ru.zaroslikov.incubator.ui.batch.parseClock
import ru.zaroslikov.incubator.ui.batch.toCelsiusOrNull
import ru.zaroslikov.incubator.ui.batch.toCountOrNull
import ru.zaroslikov.incubator.ui.batch.toForm
import ru.zaroslikov.incubator.ui.batch.toMeasureOrNull
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.today
import java.util.Date
import java.util.UUID

/**
 * Состояние шторки «Замеры за сегодня» по инкубатору.
 *
 * [targets] — идущие закладки инкубатора со строкой их сегодняшнего дня, [rows] — замеры
 * этих дней, как их отдала база. Четыре последних поля — производные, и считаются они в
 * [withDerived], а не при чтении: тело шторки читает форму и перерисовывается на каждый
 * символ, а группировка замеров ездить вместе с этим не должна — то же правило, что у
 * `BatchDetailState`.
 */
@Immutable
internal data class IncubatorMeasurementState(
    val loaded: Boolean = false,
    val targets: List<MeasurementTarget> = emptyList(),
    val rows: List<Measurement> = emptyList(),
    val form: MeasurementForm = MeasurementForm(),
    /**
     * Закладки, снятые галочкой в «Справке», — копия им не пишется. Снятые, а не
     * выбранные: по умолчанию выбраны все, и новая закладка выбрана сама ([selectedTargets]).
     * Ввод, как и форма: сбрасывается вместе с ней при [IncubatorMeasurementIntent.Load].
     */
    val deselected: Set<Long> = emptySet(),
    /** Цели, которые получат копию: со строкой дня и не снятые. Производное. */
    val recipients: List<MeasurementTarget> = emptyList(),
    /** Журнал за сегодня: одна строка на группу копий ([deviceMeasurements]). Производное. */
    val measurements: List<Measurement> = emptyList(),
    /** Общая цель для плиток и аналитики — среднее по выбранным ([averagePlan]); `null` — целей нет. Производное. */
    val plan: Value? = null,
    /**
     * Все цели на автоматике по этой колонке — форма запирает клетку, как в закладке.
     * Достаточно одной закладки, которая переворачивает руками, чтобы клетка открылась:
     * ей число нужно, а остальным его всё равно не запишут ([measurementCopies]).
     * Производное.
     */
    val autoTurn: Boolean = false,
    val autoAiring: Boolean = false,
    /**
     * Инкубатор убран в архив — писать нельзя ничего, только смотреть. Свой флаг, а не
     * «целей нет»: архив останавливает все закладки, и целей в нём действительно не
     * бывает, но запрет по совпадению — это не запрет, а на экран кнопку не выводят
     * (`IncubatorScreen` не рисует её в архиве); ViewModel отказывает сама, как и
     * `IncubatorViewModel` своим операциям.
     */
    val archived: Boolean = false,
    /** Есть хоть одна выбранная цель со строкой дня и инкубатор не в архиве — иначе записывать некуда. Производное. */
    val canRecord: Boolean = false,
    /** Инкубатор шторки и его имя — цель таймера проветривания и подпись его уведомления. */
    val incubatorId: Long = 0,
    val incubatorName: String = "",
    /** Таймер проветривания — единственный на приложение; чей он, решает [timerSlot]. */
    val airingTimer: AiringTimerState = AiringTimerState.Idle,
) {
    val timerTarget: AiringTimerTarget get() = AiringTimerTarget(incubatorId)

    /**
     * С какого часа считать сутки журнала — час закладки первой идущей закладки. У
     * закладок одного прибора он может различаться, и единственно верного ответа нет;
     * без него утренние замеры после полуночи опускались бы под вечерние. Дёшево: пара целей.
     */
    val dayStart: String
        get() = targets.map { it.batch.time.trim() }.firstOrNull { parseClock(it) != null }.orEmpty()

    /** Что показать под полями формы. Дёшево: три поля, без проходов. */
    val timerSlot: AiringTimerSlot
        get() = AiringTimerSlot(
            state = airingTimer,
            mine = airingTimer.targetOrNull?.let { it == timerTarget } ?: true,
            planMinutes = plan?.airingTime,
        )
}

private fun IncubatorMeasurementState.withDerived(): IncubatorMeasurementState {
    // Флаги автоматики и общая цель — по тем же целям, что и `canRecord`: закладка без
    // строки дня или снятая галочкой копию не получает, и открывать ради неё клетку
    // «ПЕРЕВ.», чтобы введённое число потом молча сняла `measurementCopies`, или
    // гасить общую цель её планом было бы обманом.
    val recipients = selectedTargets(targets, deselected)
    return copy(
        recipients = recipients,
        measurements = deviceMeasurements(rows),
        plan = averagePlan(recipients),
        autoTurn = recipients.isNotEmpty() && recipients.all { it.autoTurn },
        autoAiring = recipients.isNotEmpty() && recipients.all { it.autoAiring },
        canRecord = !archived && recipients.isNotEmpty(),
    )
}

internal sealed interface IncubatorMeasurementIntent {
    /**
     * Шторку открыли. Цели и замеры перечитываются всегда — пока она была свёрнута,
     * закладку могли завершить, — а форма сбрасывается только при [resetForm]: см.
     * `SheetDraft`.
     */
    data class Load(val incubatorId: Long, val resetForm: Boolean = true) : IncubatorMeasurementIntent

    data class UpdateForm(val form: MeasurementForm) : IncubatorMeasurementIntent

    /** «Изм.» в строке журнала — группа уезжает в форму значениями своей первой копии. */
    data class StartEdit(val measurement: Measurement) : IncubatorMeasurementIntent

    data object CancelEdit : IncubatorMeasurementIntent

    data object Save : IncubatorMeasurementIntent

    /** Крестик в строке журнала — удаляются все копии группы. */
    data class Delete(val measurement: Measurement) : IncubatorMeasurementIntent

    /** Галочка у закладки в «Справке»: снять — копия ей не пишется, поставить — снова пишется. */
    data class ToggleTarget(val batchId: Long) : IncubatorMeasurementIntent

    data object SelectAll : IncubatorMeasurementIntent

    data object SelectNone : IncubatorMeasurementIntent

    /** Кнопки карточки таймера проветривания под полями формы. */
    data class AiringTimer(val action: AiringTimerAction) : IncubatorMeasurementIntent
}

/** Одноразовых событий у шторки нет: запись не закрывает её, журнал обновится сам. */
internal sealed interface IncubatorMeasurementEffect

/**
 * Шторка замера по инкубатору: одно показание прибора — в каждую идущую закладку. Запись кладётся
 * копиями в строку сегодняшнего дня каждой закладки с общей меткой [Measurement.groupId]; от замеров,
 * внесённых в закладке, копии ничем не отличаются.
 *
 * Состояние локальное ([StatefulMviViewModel]): половина — ввод, потоки базы читаются внутри одного
 * [load], который при новом открытии отменяется целиком.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class IncubatorMeasurementViewModel(
    private val settings: AppSettings,
    private val itemsRepository: ItemsRepository,
    private val airingTimer: AiringTimerController,
) : StatefulMviViewModel<IncubatorMeasurementState, IncubatorMeasurementIntent, IncubatorMeasurementEffect>(
    IncubatorMeasurementState(),
) {
    /** Градусы полей ввода — из «Настроек», при каждом обращении (см. `BatchDetailViewModel`). */
    private val temperatureUnit: TemperatureUnit
        get() = settings.temperatureUnit

    private var loadJob: Job? = null

    /** Инкубатор, который читают сейчас; ответы отменённого чтения сверяются с ним. */
    private var activeIncubatorId: Long = 0

    /** Результат таймера, чьи минуты форма уже подставила; см. `BatchDetailViewModel`. */
    private var filledTimerId: Long = 0L

    override fun onIntent(intent: IncubatorMeasurementIntent) {
        when (intent) {
            is IncubatorMeasurementIntent.Load -> load(intent.incubatorId, intent.resetForm)
            is IncubatorMeasurementIntent.UpdateForm -> reduce { copy(form = intent.form) }
            is IncubatorMeasurementIntent.StartEdit ->
                reduce { copy(form = intent.measurement.toForm(temperatureUnit)) }
            IncubatorMeasurementIntent.CancelEdit -> reduce { copy(form = MeasurementForm()) }
            IncubatorMeasurementIntent.Save -> save()
            is IncubatorMeasurementIntent.Delete -> delete(intent.measurement)
            is IncubatorMeasurementIntent.ToggleTarget -> reduce {
                val next = if (intent.batchId in deselected) deselected - intent.batchId
                else deselected + intent.batchId
                copy(deselected = next).withDerived()
            }
            IncubatorMeasurementIntent.SelectAll -> reduce { copy(deselected = emptySet()).withDerived() }
            IncubatorMeasurementIntent.SelectNone -> reduce {
                copy(deselected = targets.map { it.batch.id }.toSet()).withDerived()
            }
            is IncubatorMeasurementIntent.AiringTimer -> onTimer(intent.action)
        }
    }

    private fun load(incubatorId: Long, resetForm: Boolean) {
        if (incubatorId == 0L) return
        // Сброшенная форма снова готова принять минуты ждущего результата таймера.
        if (resetForm) filledTimerId = 0L
        reduce {
            copy(
                loaded = if (activeIncubatorId == incubatorId) loaded else false,
                incubatorId = incubatorId,
                form = if (resetForm) MeasurementForm() else form,
                deselected = if (resetForm) emptySet() else deselected,
            )
        }
        activeIncubatorId = incubatorId

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Один снимок каталога и один «сегодня» на всё открытие — как в шторке
            // закладки: срок вида не меняется, пока шторка открыта, а день — тем более.
            val catalog = SpeciesCatalog(itemsRepository.getCustomSpecies().first())
            // Момент, а не полночь: день инкубации начинается в час закладки.
            val today = Date()
            launch { watchAiringTimer(incubatorId) }
            itemsRepository.getBatchesFor(incubatorId)
                .map { batches -> batches.filter { it.status == BatchStatus.Active } }
                .distinctUntilChanged()
                // Цели — по строке дня на закладку, столько подписок, сколько идущих
                // закладок в приборе; их единицы. Список целей меняется редко, и
                // `flatMapLatest` пересобирает подписки только тогда.
                .flatMapLatest { active -> targetsOf(active, catalog, today) }
                // Room инвалидирует потаблично: правка любой строки `Batch_value` где
                // угодно переиздаёт тот же список целей, и без этого подписка на замеры
                // ниже пересобиралась бы на каждую такую правку — журнал мигал бы.
                .distinctUntilChanged()
                // Замеры — одним запросом по всем сегодняшним дням: группа копий
                // приходит целиком из одного ответа, склеивать её из потоков не надо.
                .flatMapLatest { targets ->
                    val ids = targets.mapNotNull { it.plan?.id }
                    val rows = if (ids.isEmpty()) flowOf(emptyList())
                    else itemsRepository.getMeasurementsForDays(ids)
                    rows.map { targets to it }
                }
                // Архив читается потоком, а не снимком: «Вернуть в список» на стартовом
                // экране может случиться, пока шторка свёрнута, и запрет должен сняться
                // вместе с ним. Удалённый инкубатор считается архивным — писать некуда.
                .combine(itemsRepository.getIncubator(incubatorId)) { (targets, rows), incubator ->
                    Triple(targets, rows, incubator)
                }
                .collect { (targets, rows, incubator) ->
                    if (activeIncubatorId != incubatorId) return@collect
                    reduce {
                        copy(
                            loaded = true,
                            targets = targets,
                            rows = rows,
                            archived = incubator?.hidden ?: true,
                            incubatorName = incubator?.name.orEmpty(),
                        ).withDerived()
                    }
                }
        }
    }

    private fun targetsOf(
        active: List<Batch>,
        catalog: SpeciesCatalog,
        today: Date,
    ): Flow<List<MeasurementTarget>> {
        if (active.isEmpty()) return flowOf(emptyList())
        val flows = active.map { batch ->
            // Тот же расчёт дня, что в `BatchDetailViewModel.load`: копия обязана лечь
            // в строку, которую закладка покажет как «Замеры за сегодня».
            val total = catalog.incubationDays(batch.type)
            val start = batchStartMoment(batch)
            val day = incubationDay(start, total, today)
            // Дату закладки не разобрать — какой сегодня у неё день, неизвестно, и копия
            // в первый день была бы записью в чужое число. Такая закладка остаётся в
            // списке без строки дня, и карточка говорит, что записи туда не будет.
            if (start == null) flowOf(MeasurementTarget(batch, day, null, total))
            else itemsRepository.getBatchValueForDay(batch.id, day)
                .map { MeasurementTarget(batch, day, it, total) }
        }
        return combine(flows) { it.toList() }
    }

    private fun save() {
        val snapshot = current
        val form = snapshot.form
        if (!form.isValid || !snapshot.canRecord) return
        val unit = temperatureUnit
        viewModelScope.launch {
            // Шаблон — то, что ввели, без поправки на автоматику: её накладывает
            // по-закладочно `measurementCopies` / `updatedCopies`, потому что у двух
            // закладок одного прибора она разная.
            val template = Measurement(
                idValue = 0,
                time = form.time.ifBlank { clockText() },
                temp = form.temp.toCelsiusOrNull(unit),
                damp = form.damp.toMeasureOrNull(),
                over = form.over.toCountOrNull(),
                airingCount = form.airingCountValue,
                airingTime = form.airingTime.toCountOrNull(),
                note = form.note.trim(),
            )
            val editing = form.editingId != 0L
            val written = if (editing) {
                // Копии группы — по метке из формы и из базы, а не из `rows`: там лежат
                // только сегодняшние дни идущих закладок, а закладку могли завершить
                // после записи, и её копия обязана поправиться вместе с остальными.
                // Пустой ответ — группу удалили из закладки, пока форма была открыта.
                val groupId = form.groupId ?: return@launch reduce { copy(form = MeasurementForm()) }
                val group = itemsRepository.getMeasurementGroup(groupId)
                val copies = updatedCopies(group, snapshot.targets, template)
                if (copies.isEmpty()) return@launch reduce { copy(form = MeasurementForm()) }
                // Копия, которую автоматика её закладки опустошила, — не замер, а пустая
                // строка в журнале закладки: её удаляем, остальные переписываем.
                val (kept, emptied) = copies.partition { it.hasContent() }
                // И обратно: закладке, пропущенной при записи из-за пустой копии, правка
                // могла дать что записать — её копия добавляется в ту же группу.
                val added = missingCopies(group, snapshot.recipients, template)
                // Одной транзакцией: группа ложится целиком или не ложится вовсе.
                itemsRepository.replaceMeasurementGroup(kept, emptied, added)
                kept.size + added.size
            } else {
                val copies = measurementCopies(snapshot.recipients, template, UUID.randomUUID().toString())
                if (copies.isEmpty()) return@launch reduce { copy(form = MeasurementForm()) }
                itemsRepository.insertMeasurements(copies)
                copies.size
            }
            // То же событие, что у записи из закладки, — в воронке это один и тот же шаг;
            // «Из инкубатора» и «Закладок» отличают этот путь от того.
            Analytics.report(
                Events.MEASUREMENT_SAVED,
                mapOf(
                    "Вид" to snapshot.targets.map { it.batch.type }.distinct().joinToString(", "),
                    "Температура" to (template.temp != null),
                    "Влажность" to (template.damp != null),
                    "Перевороты" to (template.over != null),
                    "Проветривание" to (template.airingTime != null),
                    "Заметка" to template.note.isNotBlank(),
                    "Правка" to editing,
                    "Из инкубатора" to true,
                    "Закладок" to written,
                ),
            )
            reduce { copy(form = MeasurementForm()) }
            // Замер записан — результат таймера этого инкубатора отработал.
            airingTimer.resultSaved(snapshot.timerTarget)
        }
    }

    // --- Таймер проветривания ---------------------------------------------------------------

    /**
     * Слушает таймер и подставляет его результат, когда он поставлен из этой шторки, — то
     * же, что делает `BatchDetailViewModel.watchAiringTimer`, с целью «инкубатор, без
     * закладки»: один раз на результат и форму, а снимает результат запись замера. Минуты
     * ложатся в общую форму, то есть уйдут копиями во все выбранные закладки; под общим
     * автопроветриванием поле заперто, и подставлять некуда.
     */
    private suspend fun watchAiringTimer(incubatorId: Long) {
        airingTimer.state.collect { raw ->
            if (activeIncubatorId != incubatorId) return@collect
            // По часам, как в шторке закладки: протухший результат в замер не идёт.
            val timer = raw.settled(System.currentTimeMillis())
            if (timer != raw) {
                airingTimer.refresh()
                return@collect
            }
            reduce { copy(airingTimer = timer) }
            val snapshot = current
            if (timer is AiringTimerState.Done &&
                timer.id != filledTimerId &&
                timer.target == snapshot.timerTarget &&
                !snapshot.autoAiring
            ) {
                filledTimerId = timer.id
                reduce { copy(form = form.copy(airingTime = timer.minutes.toString())) }
            }
        }
    }

    private fun onTimer(action: AiringTimerAction) {
        val snapshot = current
        val target = snapshot.timerTarget
        val mine = snapshot.airingTimer.targetOrNull == target
        when (action) {
            is AiringTimerAction.Start -> {
                if (!snapshot.canRecord || snapshot.autoAiring) return
                if (snapshot.airingTimer !is AiringTimerState.Idle) return
                airingTimer.start(target, snapshot.incubatorName, action.minutes)
            }
            AiringTimerAction.Cancel -> if (mine) airingTimer.cancel()
            AiringTimerAction.Finish -> if (mine) airingTimer.finishNow()
            AiringTimerAction.Dismiss -> if (mine) airingTimer.dismiss()
            AiringTimerAction.Silence -> airingTimer.stopRinging()
        }
    }

    private fun delete(measurement: Measurement) {
        val groupId = measurement.groupId ?: return
        // Тот же отказ, что у записи: архивный инкубатор — только на просмотр, и
        // composition могла пережить состояние, из которого построена.
        if (current.archived) return
        viewModelScope.launch {
            // По метке из базы, как и правка: копия в закладке, завершённой после
            // записи, в `rows` уже не лежит, а удалиться обязана вместе со всеми.
            itemsRepository.deleteMeasurements(itemsRepository.getMeasurementGroup(groupId))
            reduce {
                copy(form = if (form.editingId == measurement.id) MeasurementForm() else form)
            }
        }
    }
}
