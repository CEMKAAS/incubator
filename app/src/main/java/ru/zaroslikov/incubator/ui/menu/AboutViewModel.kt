package ru.zaroslikov.incubator.ui.menu

import androidx.compose.runtime.Immutable
import ru.zaroslikov.incubator.rustore.AppUpdateController
import ru.zaroslikov.incubator.rustore.ReviewController
import ru.zaroslikov.incubator.rustore.UpdateCheckOutcome
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel

@Immutable
data class AboutState(
    /** Ручная проверка обновления ждёт ответа RuStore. */
    val checkingUpdate: Boolean = false,
    /** Ответ последней ручной проверки; `null` — проверки не было. */
    val updateOutcome: UpdateCheckOutcome? = null,
)

sealed interface AboutIntent {
    /** «Оценить приложение» — без условий; нечем показать окно — откроется страница RuStore. */
    data object Rate : AboutIntent

    /** «Проверить обновление» — найденное покажет карточка внизу экрана. */
    data object CheckUpdate : AboutIntent
}

/** Одноразовых событий нет: ссылки и письма экран открывает сам, это платформенные намерения. */
sealed interface AboutEffect

/** «О приложении»: две двери в RuStore. Всё остальное на экране — статичный текст и ссылки. */
class AboutViewModel(
    private val review: ReviewController,
    private val appUpdate: AppUpdateController,
) : StatefulMviViewModel<AboutState, AboutIntent, AboutEffect>(AboutState()) {

    override fun onIntent(intent: AboutIntent) {
        when (intent) {
            AboutIntent.Rate -> review.open()
            AboutIntent.CheckUpdate -> checkUpdate()
        }
    }

    private fun checkUpdate() {
        // Повторное нажатие поверх идущей проверки ничего не добавит: ответ придёт на первое.
        if (current.checkingUpdate) return
        reduce { copy(checkingUpdate = true, updateOutcome = null) }
        appUpdate.check(manual = true) { outcome ->
            reduce { copy(checkingUpdate = false, updateOutcome = outcome) }
        }
    }
}
