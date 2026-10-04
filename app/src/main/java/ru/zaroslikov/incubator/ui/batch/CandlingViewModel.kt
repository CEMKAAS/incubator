package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.todayText

/**
 * Состояние овоскопирования: сколько яиц сейчас в лотке и сколько из них выбраковали.
 *
 * [eggsBefore] — не «заложено», а «заложено минус всё, что убрали на других
 * овоскопированиях»: это потолок ввода.
 *
 * Выбраковка — одно число: в закладке одна порода (`Batch.breed`).
 *
 * Итог за этот же день, если он уже записан, подставляется в поле: вторая запись
 * поправляет первую (уникальный индекс `idPT` + `day` в базе).
 */
@Immutable
data class CandlingState(
    val batchId: Long = 0,
    val day: Int = 0,
    val type: String = "",
    /** Какое это овоскопирование по счёту — 1, 2, 3; у своего вида — сколько отметили. */
    val stage: Int = 0,
    /** Яиц в инкубаторе к началу этого овоскопирования. */
    val eggsBefore: Int = 0,
    /** Уже записанный итог этого дня; `null` — записывают впервые. */
    val saved: Candling? = null,
    val loaded: Boolean = false,
    /** Поле «сколько отбраковано» — строка, как и все числовые поля в приложении. */
    val rejected: String = "",
) {
    /** Пока закладка не прочитана, записывать итог не к чему и потолок ввода неизвестен. */
    val canSave: Boolean get() = loaded && batchId != 0L

    /** Сколько отбраковано; пустое поле — ноль, «всё цело». */
    val rejectedCount: Int get() = (rejected.toIntOrNull() ?: 0).coerceIn(0, eggsBefore)

    /** Сколько останется в инкубаторе после этого овоскопирования. */
    val remaining: Int get() = (eggsBefore - rejectedCount).coerceAtLeast(0)
}

sealed interface CandlingIntent {
    /**
     * Вызывается из `LaunchedEffect` при каждом открытии шторки: потолок ввода
     * перечитывается всегда, а поле сбрасывается только при [resetInput]
     * (см. [ru.zaroslikov.incubator.design.components.SheetDraft]).
     *
     * Номер овоскопирования приходит от хозяина шторки: каталог со днями своего вида
     * есть у шторки закладки, а не здесь.
     */
    data class Load(
        val batchId: Long,
        val day: Int,
        val type: String,
        val stage: Int,
        val resetInput: Boolean = true,
    ) : CandlingIntent

    /** Правка поля выбраковки. */
    data class UpdateRejected(val input: String) : CandlingIntent

    data object Save : CandlingIntent
}

sealed interface CandlingEffect {
    /** Итог записан — шторку можно закрывать; закладка подхватит число через `getCandlings`. */
    data object Saved : CandlingEffect
}

/** Идентификаторы приходят намерением [CandlingIntent.Load]: у шторки нет маршрута в навигации. */
class CandlingViewModel(
    private val itemsRepository: ItemsRepository,
) : StatefulMviViewModel<CandlingState, CandlingIntent, CandlingEffect>(CandlingState()) {

    private var loadJob: Job? = null

    override fun onIntent(intent: CandlingIntent) {
        when (intent) {
            is CandlingIntent.Load ->
                load(intent.batchId, intent.day, intent.type, intent.stage, intent.resetInput)

            is CandlingIntent.UpdateRejected -> updateRejected(intent.input)
            CandlingIntent.Save -> save()
        }
    }

    private fun load(batchId: Long, day: Int, type: String, stage: Int, resetInput: Boolean) {
        val kept = current.rejected
        reduce {
            CandlingState(
                batchId = batchId,
                day = day,
                type = type,
                stage = stage,
                rejected = if (resetInput) "" else kept,
            )
        }
        // Старый ответ не должен лечь в состояние уже другой закладки.
        loadJob?.cancel()
        if (batchId == 0L) return

        loadJob = viewModelScope.launch {
            val batch = itemsRepository.getBatch(batchId).filterNotNull().first()
            val history = itemsRepository.getCandlings(batchId).first()
            val saved = history.firstOrNull { it.day == day }
            // Выбраковка остальных дней уже случилась, этого — ещё нет: она и есть то,
            // что сейчас вводят, поэтому из потолка она не вычитается.
            val others = history.filter { it.day != day }
            // Ручная отбраковка из формы закладки вычитается целиком: она про яйца,
            // убранные между овоскопированиями, и в лотке их уже нет.
            val rejectedElsewhere = others.sumOf { it.rejected } + batch.eggRejected
            val eggsBefore = (batch.eggAll - rejectedElsewhere).coerceAtLeast(0)

            reduce {
                val loaded = copy(eggsBefore = eggsBefore, saved = saved, loaded = true)
                if (resetInput) {
                    // Уже записанный итог этого дня подставляется в поле; ноль — пустым.
                    loaded.copy(rejected = saved?.rejected?.takeIf { it > 0 }?.toString().orEmpty())
                } else {
                    // Сохранённый черновик остаётся на месте, но потолок у него теперь
                    // новый: прогоняем набранное через тот же фильтр, что и ввод с
                    // клавиатуры.
                    loaded.copy(rejected = clamp(loaded.rejected, eggsBefore))
                }
            }
        }
    }

    /** Обрезается значение, а не ввод: «50» при сорока яйцах становится «40», а не игнорируется. */
    private fun updateRejected(input: String) {
        reduce { copy(rejected = clamp(input, eggsBefore)) }
    }

    private fun save() {
        val state = current
        if (!state.canSave) return
        viewModelScope.launch {
            val candling = Candling(
                id = state.saved?.id ?: 0,
                idPT = state.batchId,
                day = state.day,
                // Дата — когда записали, справочно: яйца считаются по дню инкубации.
                date = todayText(),
                rejected = state.rejectedCount,
            )
            itemsRepository.saveCandling(candling)
            Analytics.report(
                Events.CANDLING_SAVED,
                mapOf(
                    "Вид" to state.type,
                    "День" to state.day,
                    "Этап" to state.stage,
                    "Отбраковано" to candling.rejected,
                    "Осталось" to state.remaining,
                    // Повторная запись за тот же день переписывает прежнюю.
                    "Правка" to (state.saved != null),
                ),
            )
            sendEffect(CandlingEffect.Saved)
        }
    }

    private fun clamp(input: String, limit: Int): String {
        val digits = input.filterCountInput()
        val value = digits.toIntOrNull()
        return when {
            digits.isBlank() -> ""
            // Не влезло в Int — это «больше потолка», а не «ничего».
            value == null -> limit.toString()
            value > limit -> limit.toString()
            else -> digits
        }
    }
}
