package ru.zaroslikov.incubator.ui.profile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.account.AccountApi
import ru.zaroslikov.incubator.account.AccountSubscription
import ru.zaroslikov.incubator.account.adFreeAt
import ru.zaroslikov.incubator.account.adFreeUntil
import ru.zaroslikov.incubator.account.PremiumPayment
import ru.zaroslikov.incubator.account.PremiumPlan
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Premium: разбор ответов сервера (подписка, тарифы, платёж) и правила профиля — значок
 * только у действующей подписки, «Купить Premium» только при известном «нет».
 */
class PremiumTest {

    @Test
    fun parsesSubscriptionFromMe() {
        val active = AccountApi.parseSubscription(
            JSONObject("""{"active":true,"planId":"month","expiresAt":"2026-10-30T09:00:00.000Z"}""")
        )
        assertTrue(active.active)
        assertEquals("month", active.planId)
        assertEquals("2026-10-30T09:00:00.000Z", active.expiresAt)

        val none = AccountApi.parseSubscription(JSONObject("""{"active":false,"planId":null,"expiresAt":null}"""))
        assertFalse(none.active)
        assertNull(none.expiresAt)
    }

    @Test
    fun adsDisabledFromMe() {
        val me = AccountApi.parseMeSubscription(
            JSONObject(
                """{"user":{},"adsDisabled":true,
                   "subscription":{"active":true,"planId":"year","expiresAt":"2027-09-30T00:00:00Z"}}"""
            )
        )
        assertTrue(me.adsDisabled)
        // Сервер может отключить рекламу и без подписки — флаг важнее `active`.
        val granted = AccountApi.parseMeSubscription(
            JSONObject("""{"adsDisabled":true,"subscription":{"active":false,"planId":null,"expiresAt":"2027-01-01T00:00:00Z"}}""")
        )
        assertTrue(granted.adsDisabled)
        // Флага нет — по подписке.
        val noFlag = AccountApi.parseMeSubscription(
            JSONObject("""{"subscription":{"active":false,"planId":null,"expiresAt":null}}""")
        )
        assertFalse(noFlag.adsDisabled)
    }

    @Test
    fun adFreeUntilAndAt() {
        val iso = "2027-09-30T00:00:00Z"
        val until = AccountSubscription(true, "year", iso).adFreeUntil()
        assertEquals(Instant.parse(iso).toEpochMilli(), until)
        assertTrue(adFreeAt(until, Instant.parse("2027-09-29T23:59:59Z").toEpochMilli()))
        assertFalse(adFreeAt(until, Instant.parse("2027-09-30T00:00:01Z").toEpochMilli()))
        // Не отключена — ноль, и ноль никогда не «в силе».
        assertEquals(0L, AccountSubscription(false, null, iso).adFreeUntil())
        assertFalse(adFreeAt(0L, 0L))
        // Действует без читаемого срока — до следующего ответа сервера.
        assertEquals(Long.MAX_VALUE, AccountSubscription(true, "year", null).adFreeUntil())
    }

    @Test
    fun badgeOnlyForActive() {
        assertTrue(showsPremiumBadge(AccountSubscription(true, "month", null)))
        assertFalse(showsPremiumBadge(AccountSubscription(false, "month", "2025-01-01T00:00:00Z")))
        assertFalse(showsPremiumBadge(null))
    }

    @Test
    fun buyOfferedOnlyForKnownNo() {
        assertTrue(offersPremium(AccountSubscription(false, null, null)))
        assertFalse(offersPremium(AccountSubscription(true, "month", null)))
        // Ответа ещё нет — не предлагать: может быть, только что оплатили.
        assertFalse(offersPremium(null))
    }

    @Test
    fun untilIsLocalDateOfActiveOnly() {
        val iso = "2026-10-30T09:00:00.000Z"
        val expected = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        assertEquals(expected, premiumUntil(AccountSubscription(true, "month", iso)))
        assertNull(premiumUntil(AccountSubscription(true, "month", "вчера")))
        assertNull(premiumUntil(AccountSubscription(false, "month", iso)))
    }

    @Test
    fun parsesPlans() {
        val plans = AccountApi.parsePlans(
            JSONArray(
                """[{"id":"month","title":"Месяц","price":{"amountKopecks":19900,"currency":"RUB","display":"199 ₽"},"durationDays":30},
                   {"id":"year","title":"Год","price":{"amountKopecks":149000,"currency":"RUB","display":"1 490 ₽"},"durationDays":365}]"""
            )
        )
        assertEquals(listOf("month", "year"), plans.map { it.id })
        assertEquals("1 490 ₽", plans[1].priceDisplay)
        assertEquals(365, plans[1].durationDays)
    }

    @Test
    fun parsesCheckoutAndPayment() {
        val checkout = AccountApi.parseCheckout(
            JSONObject("""{"paymentId":"p1","status":"pending","confirmationUrl":"https://pay.example/p1"}""")
        )
        assertEquals("p1", checkout.paymentId)
        assertEquals("https://pay.example/p1", checkout.confirmationUrl)
        assertNull(AccountApi.parseCheckout(JSONObject("""{"paymentId":"p2","status":"succeeded","confirmationUrl":null}""")).confirmationUrl)

        val paid = AccountApi.parsePayment(
            JSONObject(
                """{"paymentId":"p1","status":"succeeded","planId":"month",
                   "amount":{"amountKopecks":19900,"currency":"RUB","display":"199 ₽"},
                   "subscription":{"active":true,"planId":"month","expiresAt":"2026-10-30T09:00:00.000Z"}}"""
            )
        )
        assertTrue(paid.isFinal)
        assertEquals(PremiumPayment.SUCCEEDED, paid.status)
        assertTrue(paid.subscription.active)
        assertFalse(PremiumPayment("pending", AccountSubscription(false, null, null)).isFinal)
        assertTrue(PremiumPayment("canceled", AccountSubscription(false, null, null)).isFinal)
    }

    @Test
    fun monthlyHintOnlyForLongRublePlans() {
        val year = PremiumPlan("year", "Год", "990 ₽", 365, 99_000, "RUB")
        assertEquals("≈ 81 ₽ в месяц", monthlyHint(year))
        assertNull(monthlyHint(year.copy(durationDays = 30)))
        assertNull(monthlyHint(year.copy(currency = "USD")))
        assertNull(monthlyHint(year.copy(amountKopecks = 0)))
    }

    @Test
    fun periodWords() {
        assertEquals("на месяц", periodLabel(30))
        assertEquals("на 3 месяца", periodLabel(90))
        assertEquals("на год", periodLabel(365))
        assertEquals("на 2 года", periodLabel(730))
        assertEquals("на 7 дней", periodLabel(7))
        assertEquals("", periodLabel(0))
    }
}
