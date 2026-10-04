package ru.zaroslikov.incubator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FieldLabelWithHint
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.SheetPickerField
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.ToggleRow
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.clockMinutes
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.batch.TimePicker
import ru.zaroslikov.incubator.ui.batch.filterMeasureInput
import java.util.Locale

/** Ночной тариф двухзонного счётчика начинается в 23:00 и кончается в 07:00 — так почти везде. */
const val DEFAULT_NIGHT_START = "23:00"
const val DEFAULT_NIGHT_END = "07:00"

/**
 * Поля «Электроэнергия» строками — общие для формы инкубатора и формы закладки.
 *
 * [twoTariffs] живёт только в форме: в базе два тарифа — это ночная цена, отличная от
 * `null` ([PowerSettings.nightPrice]). Выключенный переключатель поэтому стирает ночь при
 * сохранении, а не прячет её: иначе спрятанная ночная цена продолжала бы считаться.
 */
data class PowerFormState(
    val watts: String = "",
    val dayPrice: String = "",
    val twoTariffs: Boolean = false,
    val nightPrice: String = "",
    val nightStart: String = DEFAULT_NIGHT_START,
    val nightEnd: String = DEFAULT_NIGHT_END,
) {
    /** Ничего не вписано — форма закладки тогда может принять значения инкубатора. */
    val isBlank: Boolean get() = watts.isBlank() && dayPrice.isBlank() && nightPrice.isBlank()
}

fun PowerFormState.toSettings(): PowerSettings {
    val night = if (twoTariffs) nightPrice.toPriceOrNull() else null
    return PowerSettings(
        watts = watts.toIntOrNull()?.takeIf { it > 0 },
        dayPrice = dayPrice.toPriceOrNull(),
        nightPrice = night,
        nightStart = if (night != null) nightStart else "",
        nightEnd = if (night != null) nightEnd else "",
    )
}

fun PowerSettings.toFormState(): PowerFormState = PowerFormState(
    watts = watts?.toString().orEmpty(),
    dayPrice = dayPrice?.toPriceText().orEmpty(),
    twoTariffs = nightPrice != null,
    nightPrice = nightPrice?.toPriceText().orEmpty(),
    nightStart = nightStart.ifBlank { DEFAULT_NIGHT_START },
    nightEnd = nightEnd.ifBlank { DEFAULT_NIGHT_END },
)

private fun String.toPriceOrNull(): Double? =
    replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

/** «6.43», «6.4», «6» — без лишних нулей, с точкой, как температура. */
internal fun Double.toPriceText(): String =
    String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

/** «12,4» — киловатт-часы, с одним знаком после запятой. */
internal fun formatKwh(kwh: Double): String =
    String.format(Locale("ru"), "%.1f", kwh).removeSuffix(",0")

/**
 * Сколько стоят сутки работы при этих настройках, — подпись под полями: человек видит,
 * что вписал не ватты вместо киловатт. `null` — посчитать нечего.
 */
internal fun PowerSettings.dailyCost(): Double? {
    if (!countable) return null
    val kw = (watts ?: 0) / 1000.0
    val day = dayPrice ?: 0.0
    if (!twoTariffs) return kw * 24 * day
    val start = clockMinutes(nightStart)!!
    val end = clockMinutes(nightEnd)!!
    val nightHours = ((end - start + 24 * 60) % (24 * 60)) / 60.0
    return kw * ((24 - nightHours) * day + nightHours * (nightPrice ?: day))
}

/**
 * Поля «Электроэнергия»: потребление, цена киловатт-часа и, по переключателю, второй —
 * ночной — тариф с его часами.
 *
 * Одни и те же в форме инкубатора и в форме закладки: в закладке их значения приходят
 * от инкубатора, и [hint] говорит об этом, а правка здесь действует на одну закладку.
 */
@Composable
fun PowerFields(
    state: PowerFormState,
    onChange: (PowerFormState) -> Unit,
    hint: String,
) {
    val currency = LocalUnits.current.currency
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    if (pickStart) {
        TimePicker(time = state.nightStart) {
            onChange(state.copy(nightStart = it))
            pickStart = false
        }
    }
    if (pickEnd) {
        TimePicker(time = state.nightEnd) {
            onChange(state.copy(nightEnd = it))
            pickEnd = false
        }
    }

    FieldLabelWithHint(text = "Электроэнергия", hint = hint)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel(text = "Потребление, Вт")
            SheetTextField(
                value = state.watts,
                onValueChange = { onChange(state.copy(watts = it.filter(Char::isDigit).take(5))) },
                placeholder = "—",
                numeric = true,
                imeAction = ImeAction.Next,
            )
        }
        Column(Modifier.weight(1f)) {
            FieldLabel(text = if (state.twoTariffs) "День, ${perKwh(currency)}" else "Тариф, ${perKwh(currency)}")
            SheetTextField(
                value = state.dayPrice,
                onValueChange = { onChange(state.copy(dayPrice = it.filterMeasureInput(PRICE_DIGITS))) },
                placeholder = "—",
                numeric = true,
                imeAction = ImeAction.Next,
            )
        }
    }
    FormSpacer(8.dp)
    ToggleRow(
        title = "Два тарифа — день и ночь",
        checked = state.twoTariffs,
        onCheckedChange = { onChange(state.copy(twoTariffs = it)) },
    )
    if (state.twoTariffs) {
        FormSpacer(12.dp)
        FieldLabel(text = "Ночь, ${perKwh(currency)}")
        SheetTextField(
            value = state.nightPrice,
            onValueChange = { onChange(state.copy(nightPrice = it.filterMeasureInput(PRICE_DIGITS))) },
            placeholder = "—",
            numeric = true,
            imeAction = ImeAction.Next,
        )
        FormSpacer(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                FieldLabel(text = "Ночной с")
                ClockField(value = state.nightStart, onClick = { pickStart = true })
            }
            Column(Modifier.weight(1f)) {
                FieldLabel(text = "до")
                ClockField(value = state.nightEnd, onClick = { pickEnd = true })
            }
        }
        // Ночь нулевой длины — не ночь (`PowerSettings.twoTariffs`): весь счёт пошёл бы
        // по дневной цене, а переключатель говорил бы «два тарифа».
        if (state.nightStart == state.nightEnd) {
            FormSpacer(6.dp)
            Text(
                text = "Ночь начинается и кончается в одно время — считается только дневной тариф",
                style = DesignType.Caption,
                color = DesignPalette.Expense,
            )
        }
    }
    val daily = state.toSettings().dailyCost()
    if (daily != null) {
        FormSpacer(6.dp)
        val kwh = (state.watts.toIntOrNull() ?: 0) * 24 / 1000.0
        Text(
            text = "≈ ${formatKwh(kwh)} кВт·ч и ${formatRubles(daily, currency)} в сутки",
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockField(value: String, onClick: () -> Unit) {
    SheetPickerField(
        value = value,
        placeholder = "—",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        textStyle = DesignType.MonoField,
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_clock_design),
                contentDescription = "Выбрать время",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

private fun perKwh(currency: Currency): String = "${currency.symbol}/кВт·ч"

/** Сумма с копейками: суточный счёт — это рубли и десятки копеек, целые тут врут. */
private fun formatRubles(amount: Double, currency: Currency): String =
    String.format(Locale("ru"), "%.2f", amount).removeSuffix(",00") + " " + currency.symbol

/** Цена киловатт-часа — до четырёх цифр до запятой: тенге и иены дороже рубля. */
private const val PRICE_DIGITS = 4
