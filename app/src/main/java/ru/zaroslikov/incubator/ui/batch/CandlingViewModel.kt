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
 * овоскопированиях». Именно это число и есть потолок ввода: на третьем овоскопировании
 * нельзя выбраковать те яйца, которых после первого уже нет.
 *
 * Выбраковка — одно число: в закладке одна порода (`Batch.breed`), и делить его не по
 * чему. Лоток с двумя породами — это две закладки, у каждой своё овоскопирование.
 *
 * Итог за этот же день, если он уже записан, подставляется в поле: овоскопирование
 * прерывают и возвращаются к нему, и вторая запись должна поправить первую, а не лечь
 * рядом. За это же отвечает уникальный индекс `idPT` + `day` в базе.
 *
 * Производные величины — [rejectedCount], [remaining] — остались свойствами состояния,
 * а не его полями: это разность двух чисел, и пересчитывать её в редьюсере ради
 * экономии одного вычитания было бы дороже самого вычитания.
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
     * Вызывается из `LaunchedEffect` при каждом открытии шторки: состояние
     * перечитывается всегда, а поле сбрасывается только при [resetInput]. Пока шторку
     * держали свёрнутой, выбраковать яйца могли на другом дне, и потолок ввода нужно
     * перечитать; но набранное в поле — это ввод, и он сворачивание переживает, см.
     * [ru.zaroslikov.incubator.design.components.SheetDraft].
     *
     * Номер овоскопирования приходит готовым от хозяина шторки, поэтому подпись не
     * мигает, пока читается закладка. Считать его во ViewModel нельзя: у своего вида
     * дни овоскопирования лежат в базе, и каталог с ними уже есть у шторки закладки.
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
    /**
     * Итог записан — шторку можно закрывать: овоскопирование на этом кончилось.
     *
     * Числа с собой не едут: закладка под шторкой подхватит их сама через свой поток
     * `getCandlings`.
     */
    data object Saved : CandlingEffect
}

/**
 * Шторка овоскопирования.
 *
 * Идентификаторы приходят не из `SavedStateHandle`, а намерением [CandlingIntent.Load] —
 * у шторки нет маршрута в навигации, ровно как у [BatchDetailViewModel] и
 * [AddBatchViewModel].
 */
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
        // Набранное читается до сброса состояния: с отменённым черновиком оно
        // выбрасывается, а со свёрнутой шторкой едет в новое состояние как есть.
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
        // Прежнее чтение снимается: шторку открывают по многу раз на одной и той же
        // ViewModel, и старый ответ лёг бы в состояние уже про другую закладку.
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

    /**
     * Правит поле, не пуская в него больше, чем яиц в лотке.
     *
     * Обрезается именно значение, а не ввод целиком: набранное «50» при сорока яйцах
     * должно стать «40», а не отказаться набираться молча — иначе поле выглядит
     * сломанным.
     */
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
                // Дата — когда записали, справочно. Считают яйца по дню инкубации,
                // а он в закладке от календаря не зависит.
                date = todayText(),
                rejected = state.rejectedCount,
            )
            itemsRepository.saveCandling(candling)
            // Шаг воронки между «создал закладку» и «завершил инкубацию»: до этого
            // отчитывался только переход на экран («Переход в Овоскопирование»), то
            // есть открытая шторка ничем не отличалась от записанного итога.
            Analytics.report(
                Events.CANDLING_SAVED,
                mapOf(
                    "Вид" to state.type,
                    "День" to state.day,
                    "Этап" to state.stage,
                    "Отбраковано" to candling.rejected,
                    "Осталось" to state.remaining,
                    // Повторная запись за тот же день переписывает прежнюю: в отчёте
                    // это одна и та же строка, и без флага её видно как два разных
                    // овоскопирования.
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
            // Не влезло в Int — это «больше потолка», а не «ничего»: поле обрезается, как
            // и обещано, а не стирается.
            value == null -> limit.toString()
            value > limit -> limit.toString()
            else -> digits
        }
    }
}
