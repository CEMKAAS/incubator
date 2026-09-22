package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.zaroslikov.incubator.formatterTime

/**
 * Выбор времени напоминания.
 *
 * Диалог остался с прежних форм и в макете
 * [12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555)
 * не нарисован — в нём показано только само поле. Material-овский `TimePicker`
 * внутри выглядит как системный, поэтому переводить его на цвета макета нечего.
 *
 * @param showDialog вызывается и при подтверждении, и при отмене; при отмене приходит
 *        то же время, что и передали, — вызывающему не нужно различать эти случаи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePicker(time: String, showDialog: (String) -> Unit) {

    // Разбор терпит к мусору: время закладки у старых закладок пустое, и «ЧЧ» с «ММ»
    // взять неоткуда, а падать посреди формы диалог не должен.
    val parts = time.split(":")
    val timeState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: FALLBACK_HOUR,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    )
    Dialog(
        onDismissRequest = { showDialog(time) }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.background)
                    .padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        showDialog(formatterTime(timeState.hour, timeState.minute))
                    }) {
                        Text(text = "Принять")
                    }

                    TextButton(onClick = {
                        showDialog(time)
                    }) {
                        Text(text = "Назад")
                    }
                }
            }
        }
    }
}

/** Час, с которого диалог начинает, когда времени ему не передали. */
private const val FALLBACK_HOUR = 8
