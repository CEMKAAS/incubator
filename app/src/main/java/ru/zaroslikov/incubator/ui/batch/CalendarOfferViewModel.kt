package ru.zaroslikov.incubator.ui.batch

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.calendar.CalendarDate
import ru.zaroslikov.incubator.calendar.CalendarOffer
import ru.zaroslikov.incubator.calendar.CalendarWriter
import ru.zaroslikov.incubator.calendar.DeviceCalendar
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel

/** Шаг диалога «Добавить даты в календарь?». Хранится в `SavedStateHandle` по имени. */
enum class CalendarStep { Ask, Pick, Writing, Manual, Done }

/** Почему даты добавляются по одной — от этого зависит первая фраза шага [CalendarStep.Manual]. */
enum class ManualReason { Denied, NoCalendar, Failed }

@Immutable
data class CalendarOfferState(
    /** Предложение; `null` до первого [CalendarOfferIntent.Load]. */
    val offer: CalendarOffer? = null,
    val step: CalendarStep = CalendarStep.Ask,
    val reason: ManualReason = ManualReason.Denied,
    /** Индексы снятых галочек — не отмеченных: по умолчанию отмечено всё. */
    val unchecked: Set<Int> = emptySet(),
    /** Какие даты на шаге «по одной» уже открывали в календаре. */
    val opened: Set<Int> = emptySet(),
    /** Календари для шага «Выбор»; в `Bundle` не лежат — после смерти процесса перечитываются. */
    val calendars: List<DeviceCalendar>? = null,
    val chosenCalendar: Long = -1L,
    /** Сколько событий записано напрямую — для шага «Готово». */
    val added: Int = 0,
    /** Идёт чтение календарей или запрос разрешения: кнопки глухи, второй тап не запускает второго запроса. */
    val busy: Boolean = false,
    val noCalendarApp: Boolean = false,
) {
    val selected: List<CalendarDate>
        get() = offer?.dates?.filterIndexed { index, _ -> index !in unchecked }.orEmpty()
}

sealed interface CalendarOfferIntent {
    /** Диалог показан для этого предложения; повтор после поворота ничего не сбрасывает. */
    data class Load(val offer: CalendarOffer) : CalendarOfferIntent
    data class Toggle(val index: Int) : CalendarOfferIntent

    /** «Добавить» на шаге вопроса. */
    data object Add : CalendarOfferIntent

    /** Ответ системного окна разрешений. */
    data class PermissionResult(val result: Map<String, Boolean>) : CalendarOfferIntent
    data class Choose(val calendarId: Long) : CalendarOfferIntent

    /** «Записать» на шаге выбора календаря. */
    data object Write : CalendarOfferIntent

    /** «Открыть» у даты на шаге «по одной». */
    data class Open(val index: Int) : CalendarOfferIntent

    /** Календарь открылся на событии даты [index]. */
    data class Opened(val index: Int) : CalendarOfferIntent

    /** На телефоне нет приложения, принимающего `ACTION_INSERT`. */
    data object NoCalendarApp : CalendarOfferIntent

    /** Закрытие диалога — кнопкой или мимо него; что оно значит, решает шаг. */
    data object Dismiss : CalendarOfferIntent
}

sealed interface CalendarOfferEffect {
    data object RequestPermission : CalendarOfferEffect
    data class OpenInsert(val index: Int, val intent: Intent) : CalendarOfferEffect
    data object Closed : CalendarOfferEffect
}

/**
 * «Добавить даты в календарь?» — машина шагов, запись и аналитика (`ui/batch/AddToCalendarDialog.kt`).
 *
 * Шаг сохраняется в [SavedStateHandle] **до** запуска записи: `applyBatch` блокирующий, и
 * если процесс умрёт посреди неё, восстановленный «Запись» без живой корутины значит
 * «транзакция закоммичена» и ведёт на «Готово», а не на повторное «Добавить» — второе
 * нажатие записало бы каждое событие дважды. Поворот ViewModel переживает вместе с корутиной.
 */
class CalendarOfferViewModel(
    private val application: Application,
    private val handle: SavedStateHandle,
) : StatefulMviViewModel<CalendarOfferState, CalendarOfferIntent, CalendarOfferEffect>(
    restore(handle),
) {

    private var writeJob: Job? = null
    private var loadJob: Job? = null
    private var closed = false

    override fun onIntent(intent: CalendarOfferIntent) {
        when (intent) {
            is CalendarOfferIntent.Load -> load(intent.offer)
            is CalendarOfferIntent.Toggle -> update {
                copy(unchecked = if (intent.index in unchecked) unchecked - intent.index else unchecked + intent.index)
            }
            CalendarOfferIntent.Add -> add()
            is CalendarOfferIntent.PermissionResult -> onPermission(intent.result)
            is CalendarOfferIntent.Choose -> update { copy(chosenCalendar = intent.calendarId) }
            CalendarOfferIntent.Write -> if (!current.busy && current.chosenCalendar >= 0) write(current.chosenCalendar)
            is CalendarOfferIntent.Open -> open(intent.index)
            is CalendarOfferIntent.Opened -> update { copy(opened = opened + intent.index) }
            CalendarOfferIntent.NoCalendarApp -> reduce { copy(noCalendarApp = true) }
            CalendarOfferIntent.Dismiss -> dismiss()
        }
    }

    private fun open(index: Int) {
        val offer = current.offer ?: return
        val date = offer.dates.getOrNull(index) ?: return
        sendEffect(CalendarOfferEffect.OpenInsert(index, CalendarWriter.insertIntent(offer, date)))
    }

    private fun load(offer: CalendarOffer) {
        // Ключ ViewModel собран из содержимого предложения, и две одинаковые закладки подряд
        // получают тот же экземпляр: закрытый диалог начинается заново, а не с чужого шага.
        if (closed) {
            closed = false
            writeJob = null
            loadJob = null
            update { CalendarOfferState(offer = offer) }
            return
        }
        if (current.offer == null) reduce { copy(offer = offer) }
        when (current.step) {
            // Процесс умер посреди записи: транзакция провайдера завершилась без нас.
            CalendarStep.Writing -> if (writeJob?.isActive != true) {
                update { copy(added = selected.size, step = CalendarStep.Done) }
            }
            CalendarStep.Pick -> if (current.calendars == null && loadJob?.isActive != true) {
                loadJob = viewModelScope.launch { route(loadCalendars()) }
            }
            else -> Unit
        }
    }

    private fun add() {
        if (current.busy || current.selected.isEmpty()) return
        if (CalendarWriter.hasPermission(application)) {
            proceed()
        } else {
            reduce { copy(busy = true) }
            sendEffect(CalendarOfferEffect.RequestPermission)
        }
    }

    private fun onPermission(result: Map<String, Boolean>) {
        reduce { copy(busy = false) }
        when {
            // Пустой ответ — запрос прервали (или он был вторым подряд): это не отказ,
            // вопрос остаётся, и «Добавить» можно нажать снова.
            result.isEmpty() -> Unit
            result.values.all { it } -> proceed()
            else -> goManual(ManualReason.Denied)
        }
    }

    private fun proceed() {
        reduce { copy(busy = true) }
        loadJob = viewModelScope.launch {
            val list = loadCalendars()
            reduce { copy(busy = false) }
            route(list)
        }
    }

    /** Календари для записи; `null` — доступа нет (его могли отозвать, пока диалог висел). */
    private suspend fun loadCalendars(): List<DeviceCalendar>? = try {
        withContext(Dispatchers.IO) { CalendarWriter.calendars(application) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        emptyList()
    }

    /** Один календарь — пишем сразу, несколько — спрашиваем, куда. */
    private fun route(list: List<DeviceCalendar>?) {
        when {
            list == null -> goManual(ManualReason.Denied)
            list.isEmpty() -> goManual(ManualReason.NoCalendar)
            list.size == 1 -> write(list.single().id)
            else -> update {
                copy(
                    calendars = list,
                    chosenCalendar = if (list.none { it.id == chosenCalendar }) list.first().id else chosenCalendar,
                    step = CalendarStep.Pick,
                )
            }
        }
    }

    private fun write(calendarId: Long) {
        val offer = current.offer ?: return
        if (writeJob?.isActive == true) return
        update { copy(step = CalendarStep.Writing) }
        val dates = current.selected
        writeJob = viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    CalendarWriter.insert(application, calendarId, offer, dates)
                }
                Analytics.report(
                    Events.CALENDAR_ADDED,
                    mapOf("Дат" to count, "Способ" to "напрямую", "Вид" to offer.species),
                )
                update { copy(added = count, step = CalendarStep.Done) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                goManual(ManualReason.Denied)
            } catch (e: Exception) {
                goManual(ManualReason.Failed)
            }
        }
    }

    private fun goManual(reason: ManualReason) {
        update { copy(reason = reason, step = CalendarStep.Manual) }
    }

    private fun dismiss() {
        val offer = current.offer ?: return
        if (closed) return
        when (current.step) {
            CalendarStep.Ask, CalendarStep.Pick -> {
                if (current.busy) return
                Analytics.report(Events.CALENDAR_DECLINED, mapOf("Дат" to offer.dates.size))
            }
            CalendarStep.Writing -> return
            CalendarStep.Manual -> {
                // «Открыто», а не «Дат»: сохранил ли человек событие в чужом приложении, отсюда
                // не видно, и смешивать это число с записанными напрямую нельзя.
                if (current.opened.isNotEmpty()) {
                    Analytics.report(
                        Events.CALENDAR_ADDED,
                        mapOf("Открыто" to current.opened.size, "Способ" to "по одной", "Вид" to offer.species),
                    )
                } else {
                    Analytics.report(
                        Events.CALENDAR_DECLINED,
                        mapOf("Дат" to offer.dates.size, "Этап" to "по одной"),
                    )
                }
            }
            CalendarStep.Done -> Unit
        }
        closed = true
        sendEffect(CalendarOfferEffect.Closed)
    }

    /** [reduce] плюс запись сохраняемых полей в [handle] — синхронно, до любого запуска. */
    private fun update(transform: CalendarOfferState.() -> CalendarOfferState) {
        reduce { transform() }
        val s = current
        handle[KEY_STEP] = s.step.name
        handle[KEY_REASON] = s.reason.name
        handle[KEY_UNCHECKED] = s.unchecked.toIntArray()
        handle[KEY_OPENED] = s.opened.toIntArray()
        handle[KEY_CHOSEN] = s.chosenCalendar
        handle[KEY_ADDED] = s.added
    }

    private companion object {
        const val KEY_STEP = "step"
        const val KEY_REASON = "reason"
        const val KEY_UNCHECKED = "unchecked"
        const val KEY_OPENED = "opened"
        const val KEY_CHOSEN = "chosen"
        const val KEY_ADDED = "added"

        fun restore(handle: SavedStateHandle) = CalendarOfferState(
            step = handle.get<String>(KEY_STEP)?.let(CalendarStep::valueOf) ?: CalendarStep.Ask,
            reason = handle.get<String>(KEY_REASON)?.let(ManualReason::valueOf) ?: ManualReason.Denied,
            unchecked = handle.get<IntArray>(KEY_UNCHECKED)?.toSet().orEmpty(),
            opened = handle.get<IntArray>(KEY_OPENED)?.toSet().orEmpty(),
            chosenCalendar = handle.get<Long>(KEY_CHOSEN) ?: -1L,
            added = handle.get<Int>(KEY_ADDED) ?: 0,
        )
    }
}
