package ru.zaroslikov.incubator.ui.incubator

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.transfer.ScheduleTransferState
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Куда девать файл расписания: на устройство или сразу в другое приложение.
 *
 * Тот же диалог, что у копии базы в настройках, и по той же причине два ответа, а не
 * одна кнопка с догадкой: «на устройстве» — файл про запас, «отправить» — файл уходит
 * наружу, к другому человеку, и такое решение должно быть нажатием. Текст говорит, что
 * именно уезжает — режим по дням и вид птицы, и ничего больше о закладке, — и что
 * открыть файл сможет только это приложение.
 */
@Composable
internal fun ExportScheduleDialog(
    batch: Batch,
    onDismiss: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = "Экспортировать расписание?",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = "В файл уйдёт режим по дням закладки «${batch.title.ifBlank { batch.type }}» " +
                        "— температура, влажность, поворот и проветривание на каждый день, план и " +
                        "среднее по замерам — " +
                        "и вид птицы, для которого он написан; для справки — порода и модель инкубатора. " +
                        "Название, яйца, сами замеры и " +
                        "напоминания в файл не идут. Файл зашифрован, открыть его сможет " +
                        "только это приложение — на вашем телефоне или на чужом.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSaveToDevice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.Accent,
                        contentColor = DesignPalette.OnAccent,
                    ),
                ) {
                    Text("Сохранить на устройстве", style = DesignType.ButtonLabel)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.IncomeSurface,
                        contentColor = DesignPalette.Accent,
                    ),
                ) {
                    Text(
                        text = "Отправить в другое приложение",
                        style = DesignType.ButtonLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Отмена",
                    style = DesignType.ButtonLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Итог экспорта расписания: файл сохранён либо почему нет. Ничего не перезапускает. */
@Composable
internal fun ScheduleTransferDialog(state: ScheduleTransferState, onDismiss: () -> Unit) {
    val text = when (state) {
        is ScheduleTransferState.Saved -> "Файл расписания сохранён."
        is ScheduleTransferState.Failed -> state.message
        else -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = if (state is ScheduleTransferState.Failed) "Не получилось" else "Готово",
                style = DesignType.SheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = text,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Понятно", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
            }
        },
    )
}

/**
 * Отдаёт готовый файл расписания системному окну «Поделиться» — так же, как копию
 * базы в настройках: `createChooser`, потому что файл не открывается ничем, кроме этого
 * приложения, и «приложения по умолчанию» для него нет; `FLAG_GRANT_READ_URI_PERMISSION`,
 * потому что адрес от FileProvider сам по себе прав на чтение не даёт.
 */
internal fun shareScheduleFile(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Отправить расписание"))
    } catch (e: Exception) {
        // Отправлять нечем — ни одно приложение не принимает файлы. Файл на устройство
        // остаётся второй кнопкой того же диалога.
    }
}
