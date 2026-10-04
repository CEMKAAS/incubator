package ru.zaroslikov.incubator.domain.model

/**
 * Электричество инкубатора: сколько он берёт из розетки и почём киловатт-час.
 *
 * Один и тот же набор живёт и у инкубатора ([Incubator.power]), и у закладки
 * ([Batch.power]). У устройства это его паспорт и тариф дома; у закладки — то, с чем она
 * шла на самом деле: другой режим греет иначе, а тариф за месяц мог смениться. Форма
 * новой закладки берёт значения инкубатора по умолчанию, и человек правит их для этой
 * закладки — тогда для расчёта главнее они ([over]).
 *
 * [watts] — средняя потребляемая мощность, Вт. [dayPrice] — цена киловатт-часа; при
 * одном тарифе это и есть вся цена. [nightPrice] — ночной тариф; `null` — тариф один.
 * [nightStart] / [nightEnd] — «ЧЧ:ММ», когда ночной начинается и кончается (у
 * двухзонного тарифа обычно 23:00 и 07:00); ночь может переходить через полночь.
 *
 * Деньги здесь — с копейками, `Double`: тарифы пишут «6,43 ₽», и округлённый до рубля
 * киловатт-час за месяц врал бы на сотни. В рубли итог округляется уже посчитанным
 * ([ElectricityCost] в `domain.stats`).
 */
data class PowerSettings(
    val watts: Int? = null,
    val dayPrice: Double? = null,
    val nightPrice: Double? = null,
    val nightStart: String = "",
    val nightEnd: String = "",
) {
    /** Указан ли тариф — дневная цена есть всегда, когда есть хоть какая-то. */
    val hasTariff: Boolean get() = dayPrice != null && dayPrice > 0.0

    /** Ничего не указано: ни мощности, ни тарифа. */
    val isEmpty: Boolean get() = watts == null && dayPrice == null && nightPrice == null

    /** Два тарифа: ночная цена и оба часа на месте, и ночь не нулевой длины. */
    val twoTariffs: Boolean
        get() {
            val start = clockMinutes(nightStart)
            val end = clockMinutes(nightEnd)
            return nightPrice != null && nightPrice >= 0.0 && start != null && end != null && start != end
        }

    /**
     * Эти настройки поверх [fallback] — настроек инкубатора.
     *
     * Мощность и тариф берутся порознь: закладка, у которой вписали только свою мощность,
     * платит по тарифу дома. А вот тариф — целиком, дневная цена вместе с ночной и её
     * часами: смешанный из двух источников тариф («день — свой, ночь — инкубатора») не
     * действовал нигде и никогда.
     */
    fun over(fallback: PowerSettings): PowerSettings {
        val ownTariff = hasTariff
        return PowerSettings(
            watts = watts ?: fallback.watts,
            dayPrice = if (ownTariff) dayPrice else fallback.dayPrice,
            nightPrice = if (ownTariff) nightPrice else fallback.nightPrice,
            nightStart = if (ownTariff) nightStart else fallback.nightStart,
            nightEnd = if (ownTariff) nightEnd else fallback.nightEnd,
        )
    }

    /**
     * Эти настройки — то, что вернула форма закладки, — без того, что в ней лишь
     * показывалось из инкубатора.
     *
     * Форма правки рисует пустые поля закладки значениями инкубатора ([over]): [own] —
     * свои поля закладки, как они лежат в базе, [shown] — что форма показала. Часть,
     * которой у закладки своей не было и которую не тронули, остаётся пустой: иначе
     * правка одной мощности вписала бы в закладку и нынешний тариф инкубатора, и его
     * следующая цена закладку бы уже не коснулась. Части — те же, что у [over]:
     * мощность порознь, тариф целиком.
     */
    fun withoutInherited(own: PowerSettings, shown: PowerSettings): PowerSettings {
        val keepWatts = own.watts == null && watts == shown.watts
        val keepTariff = !own.hasTariff && sameTariff(shown)
        return PowerSettings(
            watts = if (keepWatts) own.watts else watts,
            dayPrice = if (keepTariff) own.dayPrice else dayPrice,
            nightPrice = if (keepTariff) own.nightPrice else nightPrice,
            nightStart = if (keepTariff) own.nightStart else nightStart,
            nightEnd = if (keepTariff) own.nightEnd else nightEnd,
        )
    }

    private fun sameTariff(other: PowerSettings): Boolean =
        dayPrice == other.dayPrice && nightPrice == other.nightPrice &&
            nightStart == other.nightStart && nightEnd == other.nightEnd

    /** Есть ли из чего посчитать деньги: мощность и тариф. */
    val countable: Boolean get() = (watts ?: 0) > 0 && hasTariff
}

/** «23:00» → 1380 минут от полуночи; `null`, если строка не время. */
fun clockMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}
