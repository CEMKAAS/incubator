package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.ui.batch.eggsWord
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.shortDate
import ru.zaroslikov.incubator.ui.today
import java.util.Calendar
import java.util.Date

/**
 * «Инкубация завершена» — подсказка, встречающая на экране инкубатора того, у кого
 * закладка отходила свой срок, пока приложение было закрыто.
 *
 * Зачем вообще: итог закладки — сколько птенцов вывелось — вносит только человек, и
 * пока он его не внёс, закладка висит в работе, а в среднем выводе инкубатора её нет.
 * Ждать, что он сам вспомнит про день вывода, значит терять именно ту цифру, ради
 * которой закладку и заводили.
 *
 * Спрашивает подсказка не сама: кнопка ведёт в те же два диалога завершения
 * (`FinishBatchHost`), которыми закладку завершают из шторки и из меню карточки.
 * Собственная форма ввода птенцов здесь была бы третьей копией одного и того же.
 */

/**
 * Момент, когда у закладки истекает срок инкубации: дата вывода в час закладки.
 *
 * Дата вывода — та же, что показывает карточка закладки: начало плюс полный срок вида
 * ([SpeciesCatalog.incubationDays]). Час берётся из [Batch.time] — того самого времени, в которое яйца
 * заложили: двадцать один день у курицы отсчитывается от момента закладки, а не от
 * полуночи, и предлагать итог утром того дня, когда птенцы ещё не проклюнулись, рано.
 *
 * **Это единственный расчёт, в котором [Batch.time] участвует, и границ дня он не
 * двигает.** День инкубации по-прежнему считается целыми сутками от [Batch.data];
 * время решает лишь, с какого часа последнего дня показывать подсказку. Пустое время у
 * закладок, созданных до появления поля, означает «не спрашивали» — тогда весь день
 * вывода считается наступившим, как и было раньше.
 *
 * `null` — срока нет: вид неизвестен или дата начала не разобралась.
 */
internal fun batchFinishMoment(batch: Batch, catalog: SpeciesCatalog): Date? {
    val total = catalog.incubationDays(batch.type) ?: return null
    val start = parseDate(batch.data) ?: return null
    return start.plusDays(total).atTimeOf(batch.time)
}

/** Закладка, ждущая итога, вместе с моментом, в который у неё вышел срок. */
internal data class DueBatch(val batch: Batch, val finishedAt: Date)

/**
 * Какая закладка ждёт итога прямо сейчас — самая ранняя из тех, у кого срок уже вышел.
 *
 * Только идущие: у завершённой итог уже записан. Не «ровно сегодня», а «сегодня или
 * раньше»: в приложение могли не заходить неделю, и подсказка, пропавшая на следующий
 * день после вывода, не сделала бы ничего.
 *
 * Из нескольких просроченных берётся одна, самая давняя: два диалога поверх друг друга
 * не прочитать, а завершив первую, пользователь тут же увидит следующую.
 */
internal fun batchDueToFinish(
    batches: List<Batch>,
    catalog: SpeciesCatalog,
    now: Date = Date(),
): DueBatch? = dueMoments(batches, catalog).firstOrNull { !it.finishedAt.after(now) }

/**
 * Моменты, в которые у идущих закладок выходит срок, от самого раннего к позднему.
 *
 * Отделено от [batchDueToFinish] ради экрана: здесь разбираются даты всех закладок
 * инкубатора, и это единственная дорогая часть ответа, тогда как «вышел ли срок» — это
 * сравнение двух `Date`. Экран запоминает этот список по закладкам и каталогу, а с
 * текущим временем сверяется на каждой перерисовке — а их у него много, по одной на
 * каждую открытую и закрытую шторку. Раньше на каждую такую перерисовку заново
 * разбирались все даты инкубатора.
 */
internal fun dueMoments(batches: List<Batch>, catalog: SpeciesCatalog): List<DueBatch> = batches
    .filter { it.arhive == "0" }
    .mapNotNull { batch -> batchFinishMoment(batch, catalog)?.let { DueBatch(batch, it) } }
    .sortedBy { it.finishedAt.time }

/**
 * Сама подсказка.
 *
 * Двухшаговая — сперва сообщение, и только по кнопке форма итога — намеренно: экран
 * открывают и просто посмотреть, а вываливать поле ввода поверх списка на входе значит
 * заставлять закрывать его каждый раз.
 *
 * «Позже» — полноценный ответ, а не отказ: птенцы наклёвываются не по часам, и срок,
 * который вышел, ещё не значит, что вывод закончился. Закладка остаётся в работе.
 */
@Composable
internal fun FinishedBatchPrompt(
    batch: Batch,
    finishedAt: Date,
    catalog: SpeciesCatalog,
    onLater: () -> Unit,
    onEnterChicks: () -> Unit,
) {
    val name = batch.title.ifBlank { batch.type }
    val total = catalog.incubationDays(batch.type)

    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(text = "Инкубация завершена", style = DesignType.SectionTitle) },
        text = {
            Column {
                Text(
                    text = buildAnnotatedString {
                        append("«$name»")
                        if (total != null) {
                            append(" — срок инкубации, $total ${daysWord(total)},")
                        } else {
                            append(" — срок инкубации")
                        }
                        append(" истёк ")
                        withStyle(SpanStyle(color = DesignPalette.Accent)) {
                            append(finishedAtText(finishedAt, batch.time))
                        }
                        append(".")
                    },
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(10.dp)
                Text(
                    text = "Внесите, сколько птенцов вывелось из " +
                        "${batch.eggAll} ${eggsWord(batch.eggAll)}: закладка уйдёт в архив " +
                        "с итогом и попадёт в средний вывод инкубатора.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(10.dp)
                Text(
                    text = "Если вывод ещё идёт — «Позже»: закладка останется в работе.",
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEnterChicks) {
                Text(text = "Внести птенцов", color = DesignPalette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(text = "Позже", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

// --- Время и склонения ------------------------------------------------------------------------

/**
 * Тот же день, но в указанный час: «ЧЧ:ММ» из [Batch.time].
 *
 * Пустое или непонятное время оставляет дату как есть — полночь, с которой начинается
 * день вывода. Так же ведут себя закладки, заведённые до появления поля.
 */
private fun Date.atTimeOf(time: String): Date {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return this
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return this
    if (hour !in 0..23 || minute !in 0..59) return this
    return Calendar.getInstance().let {
        it.time = this
        it.set(Calendar.HOUR_OF_DAY, hour)
        it.set(Calendar.MINUTE, minute)
        it.time
    }
}

/** Полночь того же дня — от неё считается, сколько дней назад вышел срок. */
private fun Date.startOfDay(): Date = Calendar.getInstance().let {
    it.time = this
    it.set(Calendar.HOUR_OF_DAY, 0)
    it.set(Calendar.MINUTE, 0)
    it.set(Calendar.SECOND, 0)
    it.set(Calendar.MILLISECOND, 0)
    it.time
}

/**
 * «сегодня в 08:00», «вчера», «21 авг., 3 дня назад».
 *
 * Час дописывается только когда он известен: у старых закладок времени нет, и «в 00:00»
 * было бы выдумкой, а не справкой.
 */
private fun finishedAtText(finishedAt: Date, time: String): String {
    val days = daysBetween(finishedAt.startOfDay(), today())
    val day = when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "${shortDate(finishedAt)}, $days ${daysWord(days)} назад"
    }
    return if (time.isNotBlank()) "$day в $time" else day
}

/** «1 день», «3 дня», «21 день», «28 дней». */
private fun daysWord(count: Int): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return "дней"
    return when (count % 10) {
        1 -> "день"
        2, 3, 4 -> "дня"
        else -> "дней"
    }
}
