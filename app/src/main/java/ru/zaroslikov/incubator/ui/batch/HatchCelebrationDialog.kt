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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
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
import ru.zaroslikov.incubator.design.components.accentButtonColors
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.domain.stats.combined
import ru.zaroslikov.incubator.farm.FarmChicks
import ru.zaroslikov.incubator.farm.FarmStatus
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.incubator.formatMoney
import ru.zaroslikov.incubator.ui.incubator.formatMoneySigned
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.start.speciesEmoji

/**
 * Поздравление с выводом: салют на весь экран, «Поздравляем!» и краткий итог закладки.
 *
 * Показывает его экран инкубатора — сразу после того, как закладка, доведённая до
 * срока, записана с птенцами, каким бы из трёх путей её ни завершали: кнопкой внизу
 * «Обзора», «Убрать в архив» из меню карточки или подсказкой «Инкубация завершена»
 * (одна закладка или партия пород). Сводка приезжает эффектом завершения
 * (`BatchDetailEffect.Finished` / `FinishGroupEffect.Finished`), а не читается из базы
 * заново: закладка к тому моменту уже записана, а её брак и срок вида ViewModel и так
 * держала.
 *
 * **Только за птенцов.** Досрочно прерванная закладка и вывод «ноль» — это горе, а не
 * праздник: там шторка просто закрывается. У партии решает сумма — салют, если вывелся
 * хоть один птенец хоть по одной породе, и тогда в итоге стоят все завершённые породы,
 * включая ту, где ноль: партия одна, и половина итога была бы ложью о ней.
 *
 * Диалог на весь экран (`usePlatformDefaultWidth = false`), а не `AlertDialog`: салют
 * нужен вокруг карточки, а не внутри неё, — за границей карточки искры и летят. Цифры
 * те же, что через минуту покажут «Статистика» и «Финансы» (`hatchSummaryOf` в
 * `:domain` считает их тем же кодом), и плитки те же, что у «Итога по замерам».
 * Деньги — только введённые: «Вложено» при цене яиц, «Выручка» при цене птенцов и
 * «Итог» только при обеих, иначе разность выдумала бы убыток или прибыль.
 *
 * Просьба оценить приложение приходит, когда поздравление закрывают — «Отлично»,
 * тап мимо карточки или «назад» — из `IncubatorScreen`: окно RuStore поверх салюта
 * было бы окном поверх праздника.
 *
 * [farm] — птенцы, которых можно завести в «Моём хозяйстве», по строке на закладку с
 * выводом; пустой — предлагать нечего. [farmStatus] решает, что с ними делать: при
 * `Ready` — кнопки «добавить», при `Outdated` — одна «Обновить «Моё хозяйство»» (стоящая
 * версия птенцов не принимает, и молча прятать кнопку значило бы, что человек так и не
 * узнает, почему её нет), при `Absent` блока нет. Карточка поздравления остаётся
 * открытой: хозяйство и магазин открываются поверх, а возвращаются из них сюда же — и
 * экран перепроверяет хозяйство на возврате, так что после обновления «Обновить» сама
 * сменяется на «Добавить».
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DialogStatusBar()
        Box(Modifier.fillMaxSize()) {
            // Салют — за карточкой, на всём окне: искры летят по всему экрану, и
            // карточка стоит среди них, а не они внутри неё. Залпы рождаются над и под
            // карточкой: та занимает середину экрана и непрозрачна, и залп за ней
            // не виден вовсе — первая версия так и выглядела: салют без искр.
            Fireworks(
                modifier = Modifier.fillMaxSize(),
                originBands = listOf(0.03f..0.14f, 0.84f..0.95f),
            )
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = DesignPalette.Surface,
                border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
                modifier = Modifier
                    .align(Alignment.Center)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .widthIn(max = 400.dp),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "🐣", fontSize = 48.sp, lineHeight = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Поздравляем!",
                        style = DesignType.SheetTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = celebrationLine(summaries),
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(18.dp))
                    if (several) {
                        BreedResults(summaries)
                        Spacer(Modifier.height(12.dp))
                    }
                    HatchTiles(total)

                    if (farm.isNotEmpty()) {
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
                        Text(text = "Отлично", style = DesignType.ButtonLabel)
                    }
                }
            }
        }
    }
}

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
                Text(
                    text = "${speciesEmoji(summary.species)} " +
                        summary.breed.ifBlank { summary.title.ifBlank { summary.species } },
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
 * сказать — брак, если он был, срок, если вид известен, и деньги, если их вводили.
 *
 * В плитке на треть ширины — голое число, а слово уходит в подпись: «21 птенец» в ней
 * обрезалось до «21 пт…», то есть до ничего. Деньги идут по две в ряд, а «Итог» —
 * отдельной строкой во всю ширину: «+2 670 ₽» в треть не помещается, и это к тому же
 * та цифра, ради которой цены и вводили.
 */
@Composable
private fun HatchTiles(total: HatchSummary) {
    val currency = LocalUnits.current.currency
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            HatchTile(label = "Заложено яиц", value = formatCount(total.eggs))
            HatchTile(
                label = "Выведено птенцов",
                value = formatCount(total.hatched),
                valueColor = DesignPalette.Accent,
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
        if (total.hasMoney) {
            StatRow {
                if (total.hasEggPrice) HatchTile(label = "Вложено в яйца", value = formatMoney(total.invested, currency))
                if (total.hasChickPrice) HatchTile(label = "Выручка за птенцов", value = formatMoney(total.income, currency))
            }
            total.profit?.let { profit ->
                StatRow {
                    HatchTile(
                        label = "Итог закладки",
                        value = formatMoneySigned(profit, currency),
                        valueColor = if (profit >= 0) DesignPalette.Accent else DesignPalette.Expense,
                    )
                }
            }
        }
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
 * Сводки в `Bundle` — тринадцатью строками на закладку: поворот экрана не должен
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
            )
        }
    },
    restore = { flat ->
        flat.chunked(13).map { f ->
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
            )
        }
    },
)
