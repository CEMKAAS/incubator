package ru.zaroslikov.incubator.rustore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import kotlin.math.roundToInt

/**
 * Карточка «доступно обновление» — поверх всего приложения, внизу экрана.
 *
 * **Почему поверх, а не пунктом на каком-нибудь экране.** Обновление не относится ни к
 * инкубатору, ни к закладке, а нужно сказать о нём один раз и там, где человек в эту
 * минуту находится. Место в корне — то же, что у заставки рекламы
 * (`ads/AdLoadingNotice.kt`): один узел в `MainActivity`, а не одинаковая карточка,
 * размноженная по экранам и разъехавшаяся при первой же правке.
 *
 * **Почему внизу и не держит экран.** Отложенное обновление тем и отличается от
 * немедленного, что приложением можно продолжать пользоваться: карточка занимает полосу
 * над системной кнопкой «назад» и ничего не перекрывает, кроме края списка, до которого
 * ещё надо долистать. Отсюда же «Позже» рядом с каждым действием — предложение, от
 * которого нельзя отказаться, читается как сбой, а не как забота.
 */
@Composable
fun AppUpdateNotice(
    state: AppUpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Последнее, что карточка показывала. Нужно ровно на время прощальной анимации:
    // `AnimatedVisibility` продолжает рисовать содержимое, пока оно уезжает, а к этому
    // моменту состояние уже `Hidden` — и вместо уезжающей карточки уезжала бы пустая.
    val shown = remember { mutableStateOf<AppUpdateState>(AppUpdateState.Hidden) }
    if (state != AppUpdateState.Hidden) shown.value = state

    AnimatedVisibility(
        visible = state != AppUpdateState.Hidden,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        NoticeCard(
            state = shown.value,
            onDownload = onDownload,
            onInstall = onInstall,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun NoticeCard(
    state: AppUpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.Surface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            when (state) {
                is AppUpdateState.Offered -> Offered(state, onDownload, onDismiss)
                is AppUpdateState.Downloading -> Downloading(state)
                AppUpdateState.ReadyToInstall -> ReadyToInstall(onInstall, onDismiss)
                // Видимой не бывает: карточка рисуется только под `visible`, а на
                // время ухода состояние уже не меняется.
                AppUpdateState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun Offered(
    state: AppUpdateState.Offered,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Title("Вышла версия ${state.versionName}")
    Spacer(Modifier.height(6.dp))
    Description(
        "Скачается в фоне — приложением можно пользоваться." +
            megabytes(state.sizeBytes)?.let { " Размер $it." }.orEmpty()
    )
    Spacer(Modifier.height(16.dp))
    Actions(action = "Обновить", onAction = onDownload, onDismiss = onDismiss)
}

@Composable
private fun Downloading(state: AppUpdateState.Downloading) {
    val percent = state.percent
    Title("Загружаем обновление")
    Spacer(Modifier.height(6.dp))
    Description(
        if (percent == null) {
            "Качается в фоне. Можно закрыть приложение — загрузка не прервётся."
        } else {
            "Скачано $percent %. Можно закрыть приложение — загрузка не прервётся."
        }
    )
    Spacer(Modifier.height(14.dp))
    // Полоса без числа — неопределённая: замершая на нуле определённая читалась бы
    // как «зависло», а это ровно то состояние, в котором размер ещё не сообщили.
    if (percent == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = DesignPalette.Accent,
            trackColor = DesignPalette.ProgressTrack,
        )
    } else {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = DesignPalette.Accent,
            trackColor = DesignPalette.ProgressTrack,
        )
    }
}

@Composable
private fun ReadyToInstall(onInstall: () -> Unit, onDismiss: () -> Unit) {
    Title("Обновление скачано")
    Spacer(Modifier.height(6.dp))
    Description("Установка займёт несколько секунд, приложение перезапустится. Данные останутся на месте.")
    Spacer(Modifier.height(16.dp))
    Actions(action = "Установить", onAction = onInstall, onDismiss = onDismiss)
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = DesignType.SectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Description(text: String) {
    Text(
        text = text,
        style = DesignType.Body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Actions(action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onDismiss) {
            Text(
                text = "Позже",
                style = DesignType.ButtonLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = DesignPalette.Accent,
                contentColor = DesignPalette.OnAccent,
            ),
        ) {
            Text(text = action, style = DesignType.ButtonLabel)
        }
    }
}

/**
 * Размер файла человеческими мегабайтами, `null` — если RuStore его не сообщил.
 *
 * Одна десятая доля: «14,3 МБ» — это ответ на «сколько это займёт», а «14,28» —
 * ответ на вопрос, которого никто не задавал. Мегабайт здесь десятичный (10⁶), как в
 * самом RuStore и в системном списке приложений: расхождение с тем, что человек видит
 * в магазине, выглядело бы ошибкой.
 */
private fun megabytes(bytes: Long): String? {
    if (bytes <= 0) return null
    val value = (bytes / 100_000f).roundToInt() / 10f
    return "%.1f МБ".format(value)
}
