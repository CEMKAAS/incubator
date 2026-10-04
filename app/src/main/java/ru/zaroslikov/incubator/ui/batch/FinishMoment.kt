package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.DatePickerDialogSample
import ru.zaroslikov.incubator.PastOrPresentSelectableDates
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.SheetPickerField
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.dateToPickerMillis
import ru.zaroslikov.incubator.ui.incubator.atTimeOf
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.todayText
import java.util.Date

/**
 * Когда закладку закончили — выключили инкубатор или вынули птенцов.
 *
 * Спрашивается в диалогах завершения — в срок и досрочно — ради счёта за свет: электричество закладки
 * идёт от момента закладки до этого момента, а не до часа, когда человек добрался до
 * телефона вписать птенцов. По умолчанию — сейчас: чаще всего вписывают сразу.
 * Записывается в `Batch.dateEnd` и `Batch.timeEnd` (`finishedOnTime`).
 */
data class FinishMoment(val date: String, val time: String) {
    fun toDate(): Date? = parseDate(date)?.atTimeOf(time)
}

internal fun finishMomentNow(): FinishMoment = FinishMoment(todayText(), clockText())

/**
 * Что не так с моментом, или `null`. Раньше закладки нельзя — свет считался бы отрицательным
 * временем; позже «сейчас» — тоже: птенцов ещё не вынимали. Минута запаса на то, что
 * часы успели перескочить, пока открыт диалог.
 */
internal fun finishMomentError(moment: FinishMoment, start: Date?, now: Date = Date()): String? {
    val at = moment.toDate() ?: return "Укажите дату и время"
    return when {
        start != null && at.before(start) -> "Раньше, чем заложили яйца"
        at.time > now.time + 60_000 -> "Этот момент ещё не наступил"
        else -> null
    }
}

internal val FinishMomentSaver = listSaver<FinishMoment, String>(
    save = { listOf(it.date, it.time) },
    restore = { FinishMoment(it[0], it[1]) },
)

/**
 * Поля даты и времени окончания с пояснением, зачем они, и ошибкой под ними.
 * Те же поля выбора, что «Дата закладки» и «Время закладки» в форме закладки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishMomentFields(
    moment: FinishMoment,
    onChange: (FinishMoment) -> Unit,
    error: String?,
    label: String = "Когда выключили инкубатор или вынули птенцов",
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    if (pickDate) {
        val pickerState = rememberDatePickerState(
            selectableDates = PastOrPresentSelectableDates,
            initialSelectedDateMillis = dateToPickerMillis(moment.date) ?: System.currentTimeMillis(),
        )
        DatePickerDialogSample(pickerState, moment.date) { date ->
            onChange(moment.copy(date = date))
            pickDate = false
        }
    }
    if (pickTime) {
        TimePicker(time = moment.time) { time ->
            onChange(moment.copy(time = time))
            pickTime = false
        }
    }

    FieldLabel(text = label)
    Text(
        text = "По этому моменту считается электроэнергия закладки.",
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FormSpacer(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            SheetPickerField(
                value = moment.date,
                placeholder = "—",
                onClick = { pickDate = true },
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_design),
                        contentDescription = "Выбрать дату",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        Column(Modifier.weight(0.75f)) {
            SheetPickerField(
                value = moment.time,
                placeholder = "—",
                onClick = { pickTime = true },
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
    }
    if (error != null) {
        FormSpacer(6.dp)
        Text(text = error, style = DesignType.Caption, color = DesignPalette.Expense)
    }
}
