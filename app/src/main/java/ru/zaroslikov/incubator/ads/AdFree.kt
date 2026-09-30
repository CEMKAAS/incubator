package ru.zaroslikov.incubator.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Выключатель всей рекламы приложения — для Premium.
 *
 * Один на процесс, как [MobileAdsSdk], и по той же причине: его читают и баннер в
 * композиции, и [AppOpenAdController], живущий в контейнере, а источник у них один. Сам
 * он не знает, откуда берётся ответ, — пакет `ads/` об аккаунтах не знает ничего.
 * Значение ставит `AccountRepository`: из кэша при запуске процесса (см.
 * `AccountStore.adFreeUntil`) — синхронно, до первого `ON_START`, иначе реклама при
 * запуске успела бы загрузиться для того, кто за её отсутствие заплатил, — и затем из
 * каждого ответа сервера о подписке.
 *
 * Пока выключатель поднят, баннеры не рисуют ничего и SDK не поднимают, а реклама при
 * запуске не грузится, не показывается и не держит заставку «загружаем рекламу».
 */
object AdFree {

    private val state = MutableStateFlow(false)

    /** `true` — рекламу не показывать нигде. */
    val active: StateFlow<Boolean> = state.asStateFlow()

    fun set(active: Boolean) {
        state.value = active
    }
}
