package ru.zaroslikov.incubator.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountResult
import ru.zaroslikov.incubator.account.PremiumPayment
import ru.zaroslikov.incubator.account.PremiumPlan
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel

/** Где человек сейчас в окне Premium. */
enum class PremiumStage {
    /** Предложение: что даёт Premium, тарифы, «Оформить». */
    Offer,

    /** Страница оплаты открыта в браузере, ждём ответа платёжной системы. */
    Waiting,

    /** Оплата прошла — Premium подключён. */
    Done,
}

sealed interface PremiumIntent {
    /** Окно открыто — загрузить тарифы, если их ещё нет. */
    data object Open : PremiumIntent
    data class SelectPlan(val id: String) : PremiumIntent
    data object Subscribe : PremiumIntent

    /** Вернулись из браузера — спросить о платеже сразу, не дожидаясь следующего опроса. */
    data object Resumed : PremiumIntent
    data object RetryPlans : PremiumIntent

    /** Закрыть ожидание: платёж, возможно, ещё придёт — значок появится при следующем входе. */
    data object BackToOffer : PremiumIntent
    data object DismissError : PremiumIntent
}

sealed interface PremiumEffect {
    /** Открыть страницу оплаты. */
    data class OpenPayment(val url: String) : PremiumEffect
}

@Immutable
data class PremiumState(
    val plans: List<PremiumPlan> = emptyList(),
    val plansLoading: Boolean = false,
    val plansError: String? = null,
    val selectedPlanId: String? = null,
    val stage: PremiumStage = PremiumStage.Offer,
    /** Создаётся платёж — кнопка занята. */
    val busy: Boolean = false,
    val error: String? = null,
    /** Страница оплаты, чтобы открыть её снова, если браузер закрыли раньше времени. */
    val paymentUrl: String? = null,
) {
    val selectedPlan: PremiumPlan? get() = plans.firstOrNull { it.id == selectedPlanId }
    val canSubscribe: Boolean get() = selectedPlan != null && !busy
}

/**
 * Окно Premium: предложение, оплата, подтверждение.
 *
 * Оплата идёт по контракту `server_ferma`: `POST /subscription/checkout` даёт ссылку на
 * страницу платёжной системы, она открывается в браузере, а приложение опрашивает
 * `GET /subscription/payments/{id}`, пока платёж не станет `succeeded` или `canceled`.
 * Опрос — в `viewModelScope`: ViewModel живёт, пока жив экран профиля, а человек, ушедший в
 * браузер, этот экран не закрывал. Подписка из ответа сразу ложится в
 * [AccountRepository.subscription], так что значок Premium на профиле появляется сам.
 */
class PremiumViewModel(
    private val account: AccountRepository,
) : StatefulMviViewModel<PremiumState, PremiumIntent, PremiumEffect>(PremiumState()) {

    private var paymentId: String? = null
    private var planId: String? = null
    private var polling: Job? = null

    override fun onIntent(intent: PremiumIntent) {
        when (intent) {
            PremiumIntent.Open -> {
                Analytics.report(Events.PREMIUM_OPENED)
                if (current.plans.isEmpty() && !current.plansLoading) loadPlans()
            }
            PremiumIntent.RetryPlans -> loadPlans()
            is PremiumIntent.SelectPlan -> reduce { copy(selectedPlanId = intent.id) }
            PremiumIntent.Subscribe -> subscribe()
            PremiumIntent.Resumed -> if (current.stage == PremiumStage.Waiting) poll(immediate = true)
            PremiumIntent.BackToOffer -> {
                polling?.cancel()
                reduce { copy(stage = PremiumStage.Offer, error = null) }
            }
            PremiumIntent.DismissError -> reduce { copy(error = null) }
        }
    }

    private fun loadPlans() {
        reduce { copy(plansLoading = true, plansError = null) }
        viewModelScope.launch {
            when (val result = account.premiumPlans()) {
                is AccountResult.Success -> {
                    val plans = result.value
                    reduce {
                        copy(
                            plans = plans,
                            plansLoading = false,
                            plansError = if (plans.isEmpty()) "Тарифы пока недоступны. Загляните позже." else null,
                            // Самый длинный срок — первым выбором: он и есть «премиальный».
                            selectedPlanId = selectedPlanId?.takeIf { id -> plans.any { it.id == id } }
                                ?: plans.maxByOrNull { it.durationDays }?.id,
                        )
                    }
                }
                is AccountResult.Failure -> reduce {
                    copy(plansLoading = false, plansError = result.error.message)
                }
            }
        }
    }

    private fun subscribe() {
        val plan = current.selectedPlan ?: return
        if (current.busy) return
        reduce { copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = account.checkout(plan.id)) {
                is AccountResult.Success -> {
                    val url = result.value.confirmationUrl
                    paymentId = result.value.paymentId
                    planId = plan.id
                    if (url == null) {
                        // Платёж подтвердился без страницы оплаты — сразу спросить статус.
                        reduce { copy(busy = false, stage = PremiumStage.Waiting, paymentUrl = null) }
                        poll(immediate = true)
                    } else {
                        Analytics.report(Events.PREMIUM_CHECKOUT, mapOf("Тариф" to plan.id))
                        reduce { copy(busy = false, stage = PremiumStage.Waiting, paymentUrl = url) }
                        sendEffect(PremiumEffect.OpenPayment(url))
                        poll(immediate = false)
                    }
                }
                is AccountResult.Failure -> reduce { copy(busy = false, error = result.error.message) }
            }
        }
    }

    /**
     * Опрашивает платёж каждые [POLL_INTERVAL_MILLIS], не дольше [POLL_LIMIT_MILLIS]. Пока
     * человек платит, страница может быть открыта долго; после потолка опрос встаёт, но
     * возвращение в приложение ([PremiumIntent.Resumed]) спрашивает снова.
     */
    private fun poll(immediate: Boolean) {
        val id = paymentId ?: return
        polling?.cancel()
        polling = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + POLL_LIMIT_MILLIS
            if (!immediate) delay(POLL_INTERVAL_MILLIS)
            while (System.currentTimeMillis() < deadline) {
                val result = account.paymentStatus(id)
                if (result is AccountResult.Success && result.value.isFinal) {
                    finish(result.value)
                    return@launch
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun finish(payment: PremiumPayment) {
        if (payment.status == PremiumPayment.SUCCEEDED) {
            Analytics.report(Events.PREMIUM_PURCHASED, mapOf("Тариф" to (planId ?: "")))
            reduce { copy(stage = PremiumStage.Done, paymentUrl = null) }
        } else {
            reduce {
                copy(
                    stage = PremiumStage.Offer,
                    paymentUrl = null,
                    error = "Оплата отменена — деньги не списаны. Можно попробовать ещё раз.",
                )
            }
        }
        paymentId = null
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 2_500L
        private const val POLL_LIMIT_MILLIS = 10 * 60_000L
    }
}
