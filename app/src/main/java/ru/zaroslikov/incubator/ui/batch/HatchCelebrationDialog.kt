package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.zaroslikov.incubator.design.components.DialogStatusBar
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.Fireworks
import ru.zaroslikov.incubator.design.components.HintIcon
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.domain.stats.combined
import ru.zaroslikov.incubator.farm.FarmChicks
import ru.zaroslikov.incubator.farm.FarmStatus
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.components.formatKwh
import ru.zaroslikov.incubator.ui.incubator.formatMoney
import ru.zaroslikov.incubator.ui.incubator.formatMoneySigned
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.start.SpeciesText

/**
 * Поздравление с выводом: салют на весь экран, «Поздравляем!» и краткий итог закладки.
 *
 * Показывает его экран инкубатора после записи закладки, доведённой до срока, при любом из
 * трёх путей завершения (кнопка «Обзора», «Убрать в архив», подсказка «Инкубация завершена»).
 * Сводка приезжает эффектом завершения (`BatchDetailEffect.Finished` /
 * `FinishGroupEffect.Finished`), а не читается из базы заново.
 *
 * **Салют — только за птенцов.** У партии решает сумма: хоть один птенец хоть по одной
 * породе, и тогда в итоге стоят все завершённые породы, включая нулевую.
 *
 * Диалог на весь экран (`usePlatformDefaultWidth = false`): салют летит вокруг карточки.
 * Цифры те же, что покажут «Статистика» и «Финансы» (`hatchSummaryOf` в `:domain`).
 * Деньги — блоком «Прибыль» под плитками; только введённые: прибыль — при обеих ценах,
 * иначе разность выдумала бы убыток или прибыль.
 *
 * Просьба оценить приложение приходит при закрытии поздравления из `IncubatorScreen`:
 * окно RuStore поверх салюта было бы окном поверх праздника.
 *
 * [farm] — птенцы для «Моего хозяйства», по строке на закладку с выводом; пустой — предлагать
 * нечего. [farmStatus]: `Ready` — кнопки «добавить», `Outdated` — одна «Обновить «Моё
 * хозяйство»» (молчаливо спрятанная кнопка читалась бы как баг), `Absent` — блока нет.
 * Экран перепроверяет хозяйство на возврате, так что «Обновить» сама сменяется на «Добавить».
 *
 * **Прерванная закладка получает ту же карточку без салюта** — «Инкубация прервана»
 * (`HatchSummary.stopped`): день остановки, причина, заложено / отбраковано / оставалось и
 * блок «Расходы» вместо «Прибыли». Ни салюта, ни хозяйства, ни просьбы об оценке.
 *
 * **Вывод «ноль» в срок — третий вид карточки, «Птенцы не вывелись»** (у партии — ноль по
 * всем породам): плитки как есть, деньги — «Расходы» ([lossView]). Тоже без салюта,
 * хозяйства и оценки.
 */
@Composable
internal fun HatchCelebrationDialog(
    summaries: List<HatchSummary>,
    onDismiss: () -> Unit,
    farm: List<FarmChicks> = emptyList(),
    farmStatus: FarmStatus = FarmStatus.Absent,
    onSendToFarm: (FarmChicks) -> Boolean = { false },
    onUpdateFarm: () -> Unit = {},
) {
    if (summaries.isEmpty()) return
    val total = summaries.combined()
    val several = summaries.size > 1
    // Прерывают по одной закладке, так что «все» здесь — одна; партия в срок сюда с
    // причиной не приходит.
    val stopped = summaries.all { it.stopped }
    // Довели до срока, а птенцов нет — у партии ноль по всем породам. Та же карточка без
    // салюта, что и у прерванной, только итог другой: срок выдержан, вывод пустой.
    val empty = !stopped && total.hatched == 0
    val quiet = stopped || empty

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DialogStatusBar()
        // Где карточка стоит на экране — доли высоты окна; `null`, пока не измерена.
        var screenHeight by remember { mutableIntStateOf(0) }
        var cardSpan by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        // Без птенцов салюта нет вовсе — ни за карточкой, ни поверх неё.
        val fireworks = cardSpan?.takeIf { !quiet }
            ?.let { (top, bottom) -> fireworksPlacement(top, bottom) }
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { screenHeight = it.height },
        ) {
            // Салют — за карточкой, на всём окне: искры летят по всему экрану, и
            // карточка стоит среди них, а не они внутри неё. Залпы рождаются там, где
            // карточки нет: та непрозрачна, и залп за ней не виден вовсе.
            if (fireworks != null && !fireworks.overCard) {
                Fireworks(modifier = Modifier.fillMaxSize(), originBands = fireworks.bands)
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DesignPalette.Surface,
                border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
                modifier = Modifier
                    .align(Alignment.Center)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .widthIn(max = 400.dp)
                    .onGloballyPositioned { card ->
                        if (screenHeight > 0) {
                            val bounds = card.boundsInParent()
                            // До сотых: полосы — ключ анимации салюта, и дрожь в
                            // последнем пикселе запускала бы его заново.
                            fun fraction(px: Float) = (px / screenHeight * 100).roundToInt() / 100f
                            val span = fraction(bounds.top) to fraction(bounds.bottom)
                            if (span != cardSpan) cardSpan = span
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = if (quiet) "🥚" else "🐣", fontSize = 48.sp, lineHeight = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            stopped -> "Инкубация прервана"
                            empty -> "Птенцы не вывелись"
                            else -> "Поздравляем!"
                        },
                        style = DesignType.SheetTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            stopped -> stoppedLine(summaries.first())
                            empty -> emptyHatchLine(summaries)
                            else -> celebrationLine(summaries)
                        },
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(18.dp))
                    if (stopped) {
                        val summary = summaries.first()
                        StopReason(summary.endReason)
                        Spacer(Modifier.height(12.dp))
                        StoppedTiles(summary)
                        Spacer(Modifier.height(12.dp))
                        ProfitBlock(title = "Расходы", view = lossView(summaries))
                    } else {
                        if (several) {
                            BreedResults(summaries)
                            Spacer(Modifier.height(12.dp))
                        }
                        HatchTiles(total)
                        Spacer(Modifier.height(12.dp))
                        // Без птенцов выручки нет по построению — «Прибыль» сказала бы
                        // «заработок неизвестен» о закладке, про которую всё известно.
                        if (empty) ProfitBlock(title = "Расходы", view = lossView(summaries))
                        else ProfitBlock(title = "Прибыль", view = profitView(summaries))
                    }

                    if (!quiet && farm.isNotEmpty()) {
                        when (farmStatus) {
                            FarmStatus.Ready -> {
                                Spacer(Modifier.height(16.dp))
                                FarmOffer(farm = farm, onSend = onSendToFarm)
                            }
                            FarmStatus.Outdated -> {
                                Spacer(Modifier.height(16.dp))
                                FarmUpdateOffer(onUpdate = onUpdateFarm)
                            }
                            FarmStatus.Absent -> Unit
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(FieldRadius),
                        colors = accentButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(text = if (quiet) "Понятно" else "Отлично", style = DesignType.ButtonLabel)
                    }
                }
            }
            // Карточка заняла почти весь экран — свободного места под залпы нет, и
            // салют идёт поверх неё. Касаний холст не перехватывает, так что кнопки под
            // искрами нажимаются как обычно.
            if (fireworks != null && fireworks.overCard) {
                Fireworks(modifier = Modifier.fillMaxSize(), originBands = fireworks.bands)
            }
        }
    }
}

/** Где рождаются залпы салюта и рисуется ли он поверх карточки. */
internal data class FireworksPlacement(
    val bands: List<ClosedFloatingPointRange<Float>>,
    val overCard: Boolean,
)

/**
 * Полосы для залпов по тому, где стоит карточка ([cardTop] и [cardBottom] — доли высоты
 * окна). Без Compose — её закрепляет `HatchCelebrationTest`.
 *
 * Залпы рождаются в свободном месте над карточкой и под ней — салют за карточкой, она
 * среди искр. Полосы когда-то были постоянными (верхние и нижние 14 % экрана), и как
 * только в карточке появился блок «Прибыль», она накрыла их обе: салют шёл, но целиком
 * за карточкой, и его не было видно. Полоса берётся, только если в ней есть место для
 * залпа ([MIN_FREE_BAND]); нет ни одной — салют рисуется поверх верхней части карточки,
 * над 🐣 и «Поздравляем!»: лучше искры над заголовком, чем праздник без искр.
 */
internal fun fireworksPlacement(cardTop: Float, cardBottom: Float): FireworksPlacement {
    val bands = buildList {
        if (cardTop >= MIN_FREE_BAND) add(0.02f..(cardTop - 0.03f))
        if (1f - cardBottom >= MIN_FREE_BAND) add((cardBottom + 0.03f)..0.97f)
    }
    return if (bands.isNotEmpty()) FireworksPlacement(bands, overCard = false)
    else FireworksPlacement(listOf(0.04f..0.24f), overCard = true)
}

/** Сколько свободной высоты (доля окна) нужно, чтобы залп в ней был виден. */
private const val MIN_FREE_BAND = 0.1f

/**
 * «Добавить в «Моё хозяйство»» — по кнопке на закладку с птенцами: у одной закладки одна
 * кнопка, у партии — по породе, потому что в хозяйстве это разные группы и выбор
 * проекта открывается на одну.
 *
 * **Нажатая кнопка не гаснет.** Отсюда не видно, выбрали ли там проект: шторку в
 * хозяйстве можно закрыть мимо, и погасшая кнопка оставила бы птенцов без пути туда.
 * Дубль ей не грозит — хозяйство каждый раз спрашивает «куда», и второе добавление
 * случится только если его выбрать.
 *
 * Цвета — вторичной кнопки (`IncomeSurface` и акцент), как у «Отправить в другое
 * приложение»: главная кнопка карточки — «Отлично», и эта не должна с ней спорить.
 */
@Composable
private fun FarmOffer(farm: List<FarmChicks>, onSend: (FarmChicks) -> Boolean) {
    val several = farm.size > 1
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        farm.forEach { chicks ->
            FarmButton(
                text = farmButtonLabel(chicks, several),
                onClick = { onSend(chicks) },
            )
        }
        FarmNote("В «Моём хозяйстве» выберите новый проект или имеющийся — птенцы добавятся туда группой.")
    }
}

/**
 * Хозяйство стоит, но версия до договора: птенцов оно не примет. Одна кнопка на всю
 * партию — обновляется приложение, а не порода, — и строка, почему: без неё «Обновить»
 * посреди поздравления читалась бы как реклама.
 */
@Composable
private fun FarmUpdateOffer(onUpdate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FarmButton(text = "Обновить «Моё хозяйство»", onClick = onUpdate)
        FarmNote(
            "Ваша версия «Моего хозяйства» ещё не умеет принимать птенцов. " +
                "Обновите её и вернитесь сюда — появится кнопка «Добавить».",
        )
    }
}

@Composable
private fun FarmButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(FieldRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = DesignPalette.IncomeSurface,
            contentColor = DesignPalette.Accent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text = text, style = DesignType.ButtonLabel, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FarmNote(text: String) {
    Text(
        text = text,
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Подпись кнопки: у одной закладки — просто действие, у партии — чья порода и сколько
 * голов, иначе три одинаковые кнопки не различить. Число — в именительном
 * («18 птенцов → в хозяйство»): винительный после «добавить» с числительным
 * («добавить 22 птенца») по-русски шаток. Без Compose — её закрепляет
 * `HatchCelebrationTest`.
 */
internal fun farmButtonLabel(chicks: FarmChicks, several: Boolean): String {
    val who = chicks.breed.ifBlank { chicks.name }
    return if (several) "$who, ${plural(chicks.count, "птенец", "птенца", "птенцов")} → в «Моё хозяйство»"
    else "Добавить птенцов в «Моё хозяйство»"
}

/**
 * Итог по породам партии, строкой на каждую: порода слева, «18 из 20 · 90 %» справа.
 * Порода с нулём стоит наравне с остальными — так и было.
 */
@Composable
private fun BreedResults(summaries: List<HatchSummary>) {
    Column(Modifier.fillMaxWidth()) {
        summaries.forEachIndexed { index, summary ->
            if (index > 0) HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeciesText(
                    bird = summary.species,
                    text = summary.breed.ifBlank { summary.title.ifBlank { summary.species } },
                    style = DesignType.ListItemTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${summary.hatched} из ${summary.eggs} · ${summary.rate}%",
                    style = DesignType.MonoEmphasis,
                    color = if (summary.hatched > 0) DesignPalette.Accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Плитки итога — те же, что у «Итога по замерам» на «Обзоре» ([StatTile] с заливкой
 * [DesignPalette.MeasureTile]): три обязательные, потом только те, о которых есть что
 * сказать — брак, если он был, и срок, если вид известен. Деньги — не здесь, а в блоке
 * «Прибыль» под плитками ([ProfitBlock]).
 *
 * В плитке на треть ширины — голое число, а слово уходит в подпись: «21 птенец» в ней
 * обрезалось до «21 пт…», то есть до ничего.
 */
@Composable
private fun HatchTiles(total: HatchSummary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            HatchTile(label = "Заложено яиц", value = formatCount(total.eggs))
            HatchTile(
                label = "Выведено птенцов",
                value = formatCount(total.hatched),
                // Ноль зелёным читался бы как успех.
                valueColor = if (total.hatched > 0) DesignPalette.Accent
                else MaterialTheme.colorScheme.onSurface,
            )
            HatchTile(label = "Вывод", value = "${total.rate}%")
        }
        if (total.rejected > 0 || total.termDays != null) {
            StatRow {
                if (total.rejected > 0) {
                    HatchTile(label = "Отбраковано яиц", value = formatCount(total.rejected))
                }
                total.termDays?.let { HatchTile(label = "Срок инкубации", value = plural(it, "день", "дня", "дней")) }
            }
        }
    }
}

/**
 * Что показывает блок «Прибыль» — без Compose, его закрепляет `HatchCelebrationTest`.
 *
 * Считается **по породам**, а не по сводке партии: [combined] складывает цену, только
 * если она есть у каждой закладки, и партия, где у одной породы цену яиц оставили
 * пустой, говорила «стоимость яиц не указана» о закладке, у второй породы которой она
 * указана. Здесь суммируется то, что известно, а порода без цены называется по имени.
 *
 * Крупная цифра — прибыль ([HatchSummary.profit] партии), только когда обе цены есть у
 * всех пород: разность с недостающим слагаемым выдумала бы убыток или прибыль. Иначе это
 * выручка за птенцов, и подпись так её и называет, или `null`, если не знаем и её.
 *
 * Пояснения — откуда взялась сумма и чего в ней не хватает — не текстом под строками, а
 * за значком «i» у заголовка и у каждой строки ([explanation], `*Hint`): блок читают
 * ради цифр, а объяснение нужно тому, кто спросил.
 */
internal data class ProfitView(
    val headline: Int?,
    val isProfit: Boolean,
    /** Короткая подпись под крупной цифрой: что это за число. */
    val caption: String,
    /** Пояснение у заголовка «Прибыль»: как посчитано или почему прибыли нет. */
    val explanation: String,
    val income: Int?,
    val incomeHint: String,
    val eggs: Int?,
    val eggsHint: String,
    val electricity: Int?,
    val electricityHint: String,
)

internal fun profitView(summaries: List<HatchSummary>): ProfitView {
    val several = summaries.size > 1
    fun names(list: List<HatchSummary>) =
        list.joinToString { "«" + it.breed.ifBlank { it.title.ifBlank { it.species } } + "»" }

    val withChick = summaries.filter { it.hasChickPrice }
    val withEgg = summaries.filter { it.hasEggPrice }
    val withLight = summaries.filter { it.electricity != null }
    val noChick = summaries - withChick.toSet()
    val noEgg = summaries - withEgg.toSet()
    val noLight = summaries - withLight.toSet()

    val income = withChick.takeIf { it.isNotEmpty() }?.sumOf { it.income }
    // По породам, а не из `combined()`: та роняет свет, если он посчитан не у каждой, и
    // крупная цифра тогда не сходилась бы со строкой «Электроэнергия» под ней.
    val profit = if (summaries.all { it.profit != null }) summaries.sumOf { it.profit ?: 0 } else null
    val headline = profit ?: income

    // «не указана стоимость яиц» — у одной закладки без имён, у партии — с породами.
    fun lacks(what: String, missing: List<HatchSummary>) =
        if (several) "у ${names(missing)} не указана $what" else "не указана $what"

    val explanation = when {
        profit != null -> "Выручка за птенцов минус стоимость яиц" +
            (if (withLight.isNotEmpty()) " и электроэнергии." else ".")
        income != null -> "Прибыль не посчитать: " +
            listOfNotNull(
                lacks("стоимость яиц", noEgg).takeIf { noEgg.isNotEmpty() },
                lacks("стоимость птенцов", noChick).takeIf { noChick.isNotEmpty() },
            ).joinToString(", ") +
            ". Крупная цифра — только выручка за птенцов, расходы из неё не вычтены."
        else -> "Стоимость птенцов не указана — заработок неизвестен. Ниже перечислены " +
            "расходы, которые известны."
    }

    val kwh = withLight.mapNotNull { it.kwh }.takeIf { it.isNotEmpty() }?.sum()
    return ProfitView(
        headline = headline,
        isProfit = profit != null,
        caption = when {
            profit != null -> "заработано на птенцах"
            income != null -> "выручка за птенцов"
            else -> "заработок неизвестен"
        },
        explanation = explanation,
        income = income,
        incomeHint = "Сумма за птенцов по цене, введённой при завершении: за птенца × " +
            "выведено или за всех сразу." +
            (if (several && noChick.isNotEmpty()) " ${lacks("стоимость птенцов", noChick).replaceFirstChar { it.uppercase() }} — их выручки здесь нет." else ""),
        eggs = withEgg.takeIf { it.isNotEmpty() }?.sumOf { it.invested },
        eggsHint = "Во что обошлись заложенные яйца — по цене из формы закладки: за яйцо × " +
            "заложено или за всё сразу." +
            (if (several && noEgg.isNotEmpty()) " ${lacks("стоимость яиц", noEgg).replaceFirstChar { it.uppercase() }} — их яйца не вычтены." else ""),
        electricity = withLight.takeIf { it.isNotEmpty() }?.sumOf { it.electricity ?: 0 },
        electricityHint = "Свет за всё время закладки — по потреблению и тарифу, до момента, " +
            "когда выключили инкубатор." +
            (kwh?.let { " Потреблено ${formatKwh(it)} кВт·ч." } ?: "") +
            (if (several && noLight.isNotEmpty()) " У ${names(noLight)} не указаны потребление или тариф — их свет не вычтен." else ""),
    )
}

/**
 * Деньги закладки без птенцов — прерванной или с выводом «ноль» в срок — тем же
 * [ProfitView], что у поздравления, чтобы блок был один. Без Compose, его закрепляет
 * `HatchCelebrationTest`.
 *
 * Выручки нет по построению: птенцов нет, и всё вложенное — расход. Крупная цифра —
 * сумма известного (яйца и свет) со знаком минус; не знаем ни того, ни другого —
 * прочерк. У партии, как и в [profitView], суммируется то, что известно, по породам;
 * чего в сумме не хватает, говорит пояснение у заголовка.
 */
internal fun lossView(summaries: List<HatchSummary>): ProfitView {
    val several = summaries.size > 1
    val withEgg = summaries.filter { it.hasEggPrice }
    val withLight = summaries.filter { it.electricity != null }
    val eggs = withEgg.takeIf { it.isNotEmpty() }?.sumOf { it.invested }
    val light = withLight.takeIf { it.isNotEmpty() }?.sumOf { it.electricity ?: 0 }
    val known = eggs != null || light != null
    val kwh = withLight.mapNotNull { it.kwh }.takeIf { it.isNotEmpty() }?.sum()
    val noEggs = when {
        withEgg.size == summaries.size -> null
        several && eggs != null -> "Стоимость яиц указана не у всех пород — в сумме только известная."
        else -> "Стоимость яиц не указана — в сумме её нет."
    }
    val noLight = when {
        withLight.size == summaries.size -> null
        several && light != null -> "Электроэнергия посчитана не у всех пород."
        else -> "Электроэнергия не посчитана: не указаны потребление или тариф."
    }
    return ProfitView(
        headline = if (known) -((eggs ?: 0) + (light ?: 0)) else null,
        // «Со знаком»: минус перед суммой и есть её смысл.
        isProfit = true,
        caption = if (known) "ушло в расход" else "расходы неизвестны",
        explanation = if (known) {
            listOfNotNull("Птенцов нет, и всё вложенное в закладку — расход.", noEggs, noLight)
                .joinToString(" ")
        } else {
            "Стоимость яиц не указана, а электроэнергия не посчитана — во что обошлась " +
                "закладка, неизвестно."
        },
        income = null,
        incomeHint = "",
        eggs = eggs,
        eggsHint = "Во что обошлись заложенные яйца — по цене из формы закладки: за яйцо × " +
            "заложено или за всё сразу.",
        electricity = light,
        electricityHint = "Свет за всё время закладки — по потреблению и тарифу, до момента, " +
            "когда выключили инкубатор." +
            (kwh?.let { " Потреблено ${formatKwh(it)} кВт·ч." } ?: ""),
    )
}

/**
 * Первая строка карточки «Птенцы не вывелись»: срок выдержан, вывод пустой. Имена — как
 * в [celebrationLine]: закладка по названию, партия по общему имени и числу пород. Без
 * Compose, чтобы её закреплял `HatchCelebrationTest`.
 */
internal fun emptyHatchLine(summaries: List<HatchSummary>): String {
    val total = summaries.combined()
    // После «из» — родительный: «из 21 яйца», «из 24 яиц».
    val eggs = plural(total.eggs, "яйца", "яиц", "яиц")
    return if (summaries.size == 1) {
        val name = total.title.ifBlank { total.species }
        "Закладка «$name» доведена до срока, но из $eggs не вывелся ни один птенец."
    } else {
        val first = summaries.first()
        val name = baseBatchTitle(first.title, first.breed).ifBlank { first.species }
        val breeds = plural(summaries.size, "породе", "породам", "породам")
        "Партия «$name» доведена до срока, но по $breeds из $eggs не вывелся ни один птенец."
    }
}

/**
 * Первая строка карточки «Инкубация прервана»: что остановили и на каком дне. Без дня
 * (дата закладки не разобралась) — просто «досрочно»; срок приписывается, только когда
 * вид известен. Без Compose, чтобы её закреплял `HatchCelebrationTest`.
 */
internal fun stoppedLine(summary: HatchSummary): String {
    val name = summary.title.ifBlank { summary.species }
    val day = summary.stoppedDay ?: return "Закладка «$name» остановлена досрочно."
    val term = summary.termDays?.takeIf { it >= day }
    return "Закладка «$name» остановлена на $day-й день" + (term?.let { " из $it." } ?: ".")
}

/**
 * Причина остановки — красной плашкой, как `EndReasonCard` на «Обзоре» закладки и чип
 * «Не завершено»: один красный на один исход. Текст свободный, поэтому целиком.
 */
@Composable
private fun StopReason(reason: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DesignPalette.ExpenseSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = "Причина", style = DesignType.CardHeading, color = DesignPalette.Expense)
            Spacer(Modifier.height(6.dp))
            Text(
                text = reason,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Плитки прерванной закладки — те же [HatchTile], другой вопрос: не «сколько вывелось»,
 * а «сколько пропало». «Отбраковано» — убранное до остановки (овоскопирования и ручная
 * отбраковка), «Оставалось» — яйца, лежавшие в инкубаторе, когда её прервали. Второй ряд
 * — день остановки и срок вида, только известные.
 */
@Composable
private fun StoppedTiles(summary: HatchSummary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            HatchTile(label = "Заложено яиц", value = formatCount(summary.eggs))
            HatchTile(label = "Отбраковано яиц", value = formatCount(summary.rejected))
            HatchTile(
                label = "Оставалось яиц",
                value = formatCount(summary.remaining),
                valueColor = DesignPalette.Expense,
            )
        }
        if (summary.stoppedDay != null || summary.termDays != null) {
            StatRow {
                summary.stoppedDay?.let { HatchTile(label = "День остановки", value = formatCount(it)) }
                summary.termDays?.let { HatchTile(label = "Срок инкубации", value = plural(it, "день", "дня", "дней")) }
            }
        }
    }
}

/**
 * Денежный блок под плитками. У поздравления это «Прибыль»: сколько заработали на
 * птенцах, крупной цифрой, и из чего она сложилась — выручка за птенцов, минус яйца,
 * минус свет ([profitView]). У прерванной — «Расходы»: то же без выручки
 * ([lossView]) — и у вывода «ноль» тоже. Пояснения — за значками «i» ([HintIcon]) у заголовка и у каждой
 * строки. Фон — зелёный при плюсе, красный при убытке, нейтральный, пока сказать нечего.
 */
@Composable
private fun ProfitBlock(title: String, view: ProfitView) {
    val currency = LocalUnits.current.currency
    val headline = view.headline
    val surface = when {
        headline == null -> DesignPalette.MeasureTile
        headline >= 0 -> DesignPalette.IncomeSurface
        else -> DesignPalette.ExpenseSurface
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = DesignType.CardHeading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HintIcon(hint = view.explanation)
            }
            Text(
                text = when {
                    headline == null -> "—"
                    view.isProfit -> formatMoneySigned(headline, currency)
                    else -> formatMoney(headline, currency)
                },
                style = DesignType.MeasureValue,
                color = when {
                    headline == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    headline >= 0 -> DesignPalette.Accent
                    else -> DesignPalette.Expense
                },
            )
            Text(
                text = view.caption,
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (view.income != null || view.eggs != null || view.electricity != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                Spacer(Modifier.height(4.dp))
                view.income?.let {
                    ProfitLine(
                        label = "Выручка за птенцов",
                        hint = view.incomeHint,
                        value = "+" + formatMoney(it, currency),
                        color = DesignPalette.Accent,
                    )
                }
                view.eggs?.let {
                    ProfitLine(
                        label = "Яйца",
                        hint = view.eggsHint,
                        value = "−" + formatMoney(it, currency),
                        color = DesignPalette.Expense,
                    )
                }
                view.electricity?.let {
                    ProfitLine(
                        label = "Электроэнергия",
                        hint = view.electricityHint,
                        value = "−" + formatMoney(it, currency),
                        color = DesignPalette.Expense,
                    )
                }
            }
        }
    }
}

/** Строка блока: подпись со значком «i», сумма справа. Круг значка задаёт высоту строки. */
@Composable
private fun ProfitLine(label: String, value: String, color: Color, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            HintIcon(hint = hint)
        }
        Spacer(Modifier.width(10.dp))
        Text(text = value, style = DesignType.MoneyRow, color = color, maxLines = 1)
    }
}

@Composable
private fun RowScope.HatchTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    StatTile(
        label = label,
        value = value,
        valueColor = valueColor,
        surface = DesignPalette.MeasureTile,
        border = null,
    )
}

/**
 * Первая строка поздравления: что завершилось и сколько вывелось.
 *
 * У одной закладки — её название (или вид, если названия нет); у партии — общее имя
 * до хвоста с породой, как в подсказке «Инкубация завершена», и число пород. Без
 * Compose, чтобы её закреплял `HatchCelebrationTest`.
 */
internal fun celebrationLine(summaries: List<HatchSummary>): String {
    val total = summaries.combined()
    // Глагол согласуется с числом: «вывелся 21 птенец», но «вывелось 22 птенца».
    val verb = if (total.hatched % 10 == 1 && total.hatched % 100 != 11) "вывелся" else "вывелось"
    val hatched = plural(total.hatched, "птенец", "птенца", "птенцов")
    // После «из» — родительный: «из 21 яйца», «из 24 яиц».
    val eggs = plural(total.eggs, "яйца", "яиц", "яиц")
    return if (summaries.size == 1) {
        val name = total.title.ifBlank { total.species }
        "Закладка «$name» доведена до срока: $verb $hatched из $eggs."
    } else {
        val first = summaries.first()
        val name = baseBatchTitle(first.title, first.breed).ifBlank { first.species }
        val breeds = plural(summaries.size, "породе", "породам", "породам")
        "Партия «$name» доведена до срока: по $breeds $verb $hatched из $eggs."
    }
}

/**
 * Сводки в `Bundle` — семнадцатью строками на закладку: поворот экрана не должен
 * гасить поздравление, а `Bundle` знает только простые значения. Без своего
 * `Parcelable` ради одного диалога, по тому же правилу, что `FinishRowSaver`.
 */
internal val HatchSummariesSaver: Saver<List<HatchSummary>, Any> = listSaver(
    save = { list ->
        list.flatMap {
            listOf(
                it.batchId.toString(), it.title, it.species, it.breed,
                it.eggs.toString(), it.rejected.toString(), it.hatched.toString(),
                it.termDays?.toString() ?: "",
                it.invested.toString(), it.income.toString(),
                it.hasEggPrice.toString(), it.hasChickPrice.toString(),
                it.dateEnd,
                it.electricity?.toString() ?: "",
                it.kwh?.toString() ?: "",
                it.endReason,
                it.stoppedDay?.toString() ?: "",
            )
        }
    },
    restore = { flat ->
        flat.chunked(17).map { f ->
            HatchSummary(
                batchId = f[0].toLong(),
                title = f[1],
                species = f[2],
                breed = f[3],
                eggs = f[4].toInt(),
                rejected = f[5].toInt(),
                hatched = f[6].toInt(),
                termDays = f[7].toIntOrNull(),
                invested = f[8].toInt(),
                income = f[9].toInt(),
                hasEggPrice = f[10].toBoolean(),
                hasChickPrice = f[11].toBoolean(),
                dateEnd = f[12],
                electricity = f[13].toIntOrNull(),
                kwh = f[14].toDoubleOrNull(),
                endReason = f[15],
                stoppedDay = f[16].toIntOrNull(),
            )
        }
    },
)
