package ru.zaroslikov.incubator.ui.qr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.qr.QrLink
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel

/** Что сканер говорит поверх камеры, когда код прочитан, но открыть по нему нечего. */
enum class ScanMessage(val text: String) {
    /** Прочитан код, но это не ссылка на инкубатор — чужой QR-код. */
    NotOurCode("Это не QR-код инкубатора"),

    /** Ссылка наша, но такого инкубатора в базе нет: удалён или напечатан с другого телефона. */
    NotFound("Такого инкубатора нет в приложении — код напечатан с другого телефона или инкубатор удалён"),
}

@Immutable
data class ScanQrState(
    val message: ScanMessage? = null,
    /**
     * Инкубатор найден, экран уходит к нему. Дальнейшие кадры не разбираются: камера
     * продолжает отдавать их, пока идёт навигация, и без этого один код открывал бы
     * инкубатор дважды.
     */
    val done: Boolean = false,
)

sealed interface ScanQrIntent {
    /** Анализатор прочитал в кадре текст. Приходит по многу раз в секунду, пока код в кадре. */
    data class Decoded(val text: String) : ScanQrIntent
}

sealed interface ScanQrEffect {
    data class Open(val incubatorId: Long) : ScanQrEffect
}

/**
 * Экран сканера: разбирает прочитанный текст и решает, куда вести.
 *
 * Найденный инкубатор — эффект, а не состояние: переход должен случиться один раз, а
 * состояние можно прочитать дважды (поворот). Ненайденный — сообщение в состоянии,
 * которое гаснет само через [MESSAGE_MILLIS]: пока код в кадре, анализатор шлёт один и
 * тот же текст десятки раз, и на каждый повтор сообщение не перезапускается — только
 * на новый текст или после того, как старое погасло. Иначе оно мигало бы с частотой
 * кадров.
 *
 * Проверка «есть ли такой инкубатор» идёт в базу здесь, а не на экране инкубатора:
 * человек стоит с камерой у прибора, и «такого нет» должно быть сказано там же, где он
 * стоит, а не после перехода на пустой экран.
 */
class ScanQrViewModel(
    private val itemsRepository: ItemsRepository,
) : StatefulMviViewModel<ScanQrState, ScanQrIntent, ScanQrEffect>(ScanQrState()) {

    private var shownText: String? = null
    private var messageJob: Job? = null
    private var lookupJob: Job? = null

    override fun onIntent(intent: ScanQrIntent) {
        when (intent) {
            is ScanQrIntent.Decoded -> decoded(intent.text)
        }
    }

    private fun decoded(text: String) {
        if (current.done) return
        // Тот же текст, о котором уже сказано, — не новость; и пока идёт проверка в
        // базе по одному коду, второй кадр того же кода не заводит вторую.
        if (text == shownText && (current.message != null || lookupJob?.isActive == true)) return
        // Новый текст в кадре отменяет проверку прежнего, каким бы он ни был: иначе чужой
        // код показал бы «не наш», а через сто миллисекунд прежняя проверка увела бы к
        // инкубатору, который уже никто не сканирует.
        lookupJob?.cancel()
        val id = QrLink.parse(text)
        if (id == null) {
            show(text, ScanMessage.NotOurCode)
            return
        }
        shownText = text
        lookupJob = viewModelScope.launch {
            val incubator = itemsRepository.getIncubator(id).first()
            if (incubator == null) {
                show(text, ScanMessage.NotFound)
            } else {
                messageJob?.cancel()
                reduce { copy(message = null, done = true) }
                sendEffect(ScanQrEffect.Open(id))
            }
        }
    }

    private fun show(text: String, message: ScanMessage) {
        shownText = text
        messageJob?.cancel()
        reduce { copy(message = message) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_MILLIS)
            shownText = null
            reduce { copy(message = null) }
        }
    }

    companion object {
        /** Сколько держится подпись «не тот код»: хватает прочитать две строки. */
        const val MESSAGE_MILLIS = 3_000L
    }
}
