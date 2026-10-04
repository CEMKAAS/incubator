package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.ui.batch.batchStartMoment
import ru.zaroslikov.incubator.ui.batch.baseBatchTitle
import ru.zaroslikov.incubator.ui.start.SpeciesText
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.shortDate
import ru.zaroslikov.incubator.ui.today
import java.util.Calendar
import java.util.Date

/**
 * «Инкубация завершена» — подсказка тому, у кого закладка отходила срок, пока приложение было
 * закрыто: итог (сколько птенцов) вносит только человек, и пока он не внёс, закладки нет в среднем
 * выводе. Кнопка ведёт в те же два диалога завершения (`FinishBatchHost`), что и шторка и меню
 * карточки.
 */

/**
 * Момент, когда у закладки истекает срок инкубации: дата вывода в час закладки.
 *
 * Дата — как на карточке: начало плюс [SpeciesCatalog.incubationDays]; час — из [Batch.time]:
 * итог рано предлагать утром дня вывода. **Это единственный расчёт, где [Batch.time] участвует, и
 * границ дня он не двигает.** Пустое время (до появления поля) — весь день вывода считается
 * наступившим. `null` — срока нет: вид неизвестен или дата не разобралась.
 */
internal fun batchFinishMoment(batch: Batch, catalog: SpeciesCatalog): Date? {
    val total = catalog.incubationDays(batch.type) ?: return null
    return batchStartMoment(batch)?.plusDays(total)
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
 * Закладки, ждущие итога вместе: одна партия, заложенная на несколько пород.
 *
 * Форма закладки кладёт лоток из двух пород двумя закладками (см. корневой `CLAUDE.md`,
 * «A batch holds one species and one breed»), и срок у них выходит в одну и ту же
 * минуту. Спрашивать их по одной значило бы показать подсказку про одну породу, а про
 * вторую — только при следующем заходе: после «Внести птенцов» подсказка гаснет до
 * ухода с экрана. Человек же закладывал одну партию и итог вносит по ней целиком.
 */
internal data class DueGroup(val batches: List<Batch>, val finishedAt: Date)

/**
 * Какая партия ждёт итога прямо сейчас: самая давняя просроченная закладка и все, что
 * заложены вместе с ней ([laidTogether]).
 *
 * Порядок внутри — по идентификатору, то есть в том, в каком строки пород стояли в форме.
 */
internal fun groupDueToFinish(moments: List<DueBatch>, now: Date = Date()): DueGroup? {
    val first = moments.firstOrNull { !it.finishedAt.after(now) } ?: return null
    val batches = moments
        .filter { !it.finishedAt.after(now) && laidTogether(it.batch, first.batch) }
        .map { it.batch }
        .sortedBy { it.id }
    return DueGroup(batches, first.finishedAt)
}

/**
 * Заложены ли две закладки одним нажатием «Заложить N закладок».
 *
 * Отдельного признака партии в базе нет, и заводить его ради этого вопроса незачем:
 * у таких закладок общие вид, дата и час закладки и название до хвоста с породой
 * ([baseBatchTitle]) — всё это форма копирует в каждую. Две закладки одного вида,
 * заложенные в один час под разными названиями, партией не считаются: «Для себя» и
 * «На продажу» — это разные решения, и объединять их итог было бы самоуправством.
 */
internal fun laidTogether(a: Batch, b: Batch): Boolean =
    a.incubatorId == b.incubatorId &&
        a.type == b.type &&
        a.data == b.data &&
        a.time == b.time &&
        baseBatchTitle(a.title, a.breed) == baseBatchTitle(b.title, b.breed)

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
    batches: List<Batch>,
    finishedAt: Date,
    catalog: SpeciesCatalog,
    onLater: () -> Unit,
    onEnterChicks: () -> Unit,
) {
    val batch = batches.first()
    val several = batches.size > 1
    // У партии из нескольких пород имя общее — то, что стояло в форме до хвоста с
    // породой; у одной закладки — её собственное.
    val name = (if (several) baseBatchTitle(batch.title, batch.breed) else batch.title)
        .ifBlank { batch.type }
    val total = catalog.incubationDays(batch.type)

    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(text = "Инкубация завершена", style = DesignType.SectionTitle) },
        text = {
            // Факт (срок вышел, когда), затем то, о чём спрашивают (закладки — карточкой,
            // как в списке), и сама просьба. Пояснение про архив и средний вывод убрано
            // по просьбе владельца (2026-10-02): кнопки «Внести птенцов» / «Позже» говорят
            // сами за себя. Одним абзацем всё это сливалось, и глазу было не за что зацепиться.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Срок и момент — двумя строками: «истёк …» с датой и часом длиннее
                // половины строки и, дописанный следом, рвался посередине даты.
                if (total != null) {
                    Text(
                        text = "Срок инкубации — ${plural(total, "день", "дня", "дней")}",
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = buildAnnotatedString {
                        append(if (total != null) "Истёк " else "Срок инкубации истёк ")
                        withStyle(SpanStyle(color = DesignPalette.Accent, fontWeight = FontWeight.Medium)) {
                            append(finishedAtText(finishedAt, batch.time))
                        }
                        append(".")
                    },
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(12.dp)
                DueBatchesCard(name = name, batches = batches, several = several)

                FormSpacer(12.dp)
                Text(
                    text = if (several) "Внесите, сколько птенцов вывелось по каждой породе."
                    else "Внесите, сколько птенцов вывелось.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
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

/**
 * Закладки, о которых спрашивают, — карточкой, а не перечислением внутри фразы.
 *
 * Та же белая карточка с тонкой рамкой, что и в списке инкубатора: закладку узнают по
 * её виду. У партии сверху её общее название и вид, строки — породы с числом яиц; у
 * одной закладки строка одна — название, под ним вид и порода.
 */
@Composable
private fun DueBatchesCard(name: String, batches: List<Batch>, several: Boolean) {
    val batch = batches.first()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DesignPalette.Surface)
            .border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (several) {
            SpeciesText(
                bird = batch.type,
                text = "$name · ${batch.type}",
                style = DesignType.CaptionEmphasis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            batches.forEach { part ->
                FormSpacer(6.dp)
                DueBatchRow(
                    title = part.breed.ifBlank { part.title.ifBlank { part.type } },
                    subtitle = null,
                    eggs = part.eggAll,
                )
            }
        } else {
            DueBatchRow(
                title = name,
                bird = batch.type,
                subtitle = listOf(batch.type, batch.breed).filter { it.isNotBlank() }
                    .distinct().joinToString(" · ").ifBlank { null },
                eggs = batch.eggAll,
            )
        }
    }
}

@Composable
private fun DueBatchRow(title: String, subtitle: String?, eggs: Int, bird: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (bird != null) {
                SpeciesText(
                    bird = bird,
                    text = title,
                    style = DesignType.ListItemTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = title,
                    style = DesignType.ListItemTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = plural(eggs, "яйцо", "яйца", "яиц"),
            style = DesignType.MonoEmphasis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// --- Время и склонения ------------------------------------------------------------------------

/**
 * Тот же день, но в указанный час: «ЧЧ:ММ» из [Batch.time].
 *
 * Пустое или непонятное время оставляет дату как есть — полночь, с которой начинается
 * день вывода. Так же ведут себя закладки, заведённые до появления поля.
 *
 * Не `private`: тем же часом начинается и каждый день инкубации (`batchStartMoment`).
 */
internal fun Date.atTimeOf(time: String): Date {
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
