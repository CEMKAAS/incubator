package ru.zaroslikov.incubator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.FieldRadius
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.batch.CellVerdicts
import ru.zaroslikov.incubator.ui.batch.Severity
import ru.zaroslikov.incubator.ui.batch.ValueUiState
import ru.zaroslikov.incubator.ui.batch.airingCellText
import ru.zaroslikov.incubator.ui.batch.filterAiringInput
import ru.zaroslikov.incubator.ui.batch.filterCountInput
import ru.zaroslikov.incubator.ui.batch.filterMeasureInput
import ru.zaroslikov.incubator.ui.batch.overlapDescription
import ru.zaroslikov.incubator.ui.batch.parseAiringCell
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Таблица режима по дням — строка на день, столбцы «Температура», «Влажность»,
 * «Переворот», «Проветривание» и, у своего вида, «Овоскопирование».
 *
 * Жила приватно в [ru.zaroslikov.incubator.ui.batch.AddBatchSheet], где и была
 * нарисована; здесь она потому, что таблиц теперь две и это одна и та же таблица.
 * Конструктор своего вида ([ru.zaroslikov.incubator.ui.species.CustomSpeciesSheet])
 * правит ровно те же четыре величины по тем же правилам ввода, и вторая копия этих
 * клеток разъехалась бы с первой на первой же правке ширины столбца.
 *
 * Ввод по-прежнему живёт в `ui/batch`: [ValueUiState] — состояние строки, а фильтры
 * `filterMeasureInput` / `filterCountInput` / `filterAiringInput` — правила набора.
 * Сюда они импортируются, а не переезжают: это знание о величинах закладки, а не о
 * том, как выглядит клетка.
 */

/**
 * Поля таблицы уже полей формы, и отступы страницы расписания меньше: на 360 dp
 * четыре столбца при [SheetPadding] с каждой стороны сжимаются так, что «37.8» уже
 * не помещается целиком.
 */
internal val SchedulePadding = 16.dp

/** Ширина столбца «День»: в нём двузначное число и точка. */
internal val DayColumnWidth = 30.dp

/** Зазор между клетками строки. */
internal val CellGap = 4.dp

/** Высота клетки — как у полей в редакторе дня закладки. */
internal val CellHeight = 40.dp
internal val CellRadius = 12.dp

/**
 * Доли ширины у столбцов.
 *
 * Четыре числовые: температуре и влажности нужно место под «37.8», перевороту хватает
 * одной цифры, а проветриванию — четырёх знаков «2×15», которые набираются в одну
 * клетку маской (см. `filterAiringInput`).
 *
 * Пятый — овоскопирование — есть только в конструкторе своего вида, и доля у него
 * самая маленькая: там не значение, а отметка, и клетке нужно ровно на значок. На
 * 360 dp шести столбцам остаётся 278 dp, из них температуре достаётся 65 — «37.55»
 * в неё влезает целиком, что и есть предел, ради которого доли подобраны так.
 */
internal const val TempWeight = 1.1f
internal const val DampWeight = 1.1f
internal const val OverWeight = 0.8f
internal const val AiringWeight = 1f
internal const val CandlingWeight = 0.7f

/**
 * Что стоит в клетке, отданной автоматике инкубатора.
 *
 * Слово одно на оба места, где правят режим — таблицу формы закладки и правку дня в
 * шторке, — потому что вопрос один и тот же; два разных слова читались бы как два
 * разных состояния. Оно же когда-то лежало в самих колонках как значение (до девятой
 * версии схемы «Авто» писали в базу), и это ровно та причина, по которой теперь оно
 * подсказка: в базе «нормы нет» — это отсутствие числа, а не текст.
 */
internal const val AutoCellText = "Авто"

/**
 * Шапка таблицы — значками, название столбца выводится по нажатию на значок.
 *
 * Подписями она быть не может: «Проветривание» в свою долю не влезает даже в две
 * строки, а обрезанное «Проветр…» столбец не называет. Значок влезает всегда.
 * Название при этом никуда не девается: оно и в подсказке по нажатию, и в
 * `contentDescription`, то есть его же произносит и `TalkBack`.
 *
 * Высоты шапка при этом не выигрывает — клетка в [CellHeight] чуть выше двух строк
 * подписи. Это плата за площадь нажатия: строка значков, по которым надо попадать
 * пальцем, стоит десятка dp больше, чем выигрыш от одной строки вместо двух.
 *
 * @param candling добавить шестой столбец — отметку овоскопирования. Только у своего
 * вида: у встроенного дни овоскопирования заданы кодом и правке не подлежат.
 */
@Composable
internal fun ScheduleHeaderRow(candling: Boolean = false) {
    val unit = LocalUnits.current.temperature
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScheduleHeaderCell(
            icon = R.drawable.ic_calendar_design,
            title = "День",
            modifier = Modifier.width(DayColumnWidth),
        )
        ScheduleHeaderCell(
            icon = R.drawable.ic_temperature_design,
            title = "Температура, ${unit.symbol}",
            modifier = Modifier.weight(TempWeight),
        )
        ScheduleHeaderCell(
            icon = R.drawable.ic_humidity_design,
            title = "Влажность",
            modifier = Modifier.weight(DampWeight),
        )
        ScheduleHeaderCell(
            icon = R.drawable.ic_turn_design,
            title = "Переворотов за день",
            modifier = Modifier.weight(OverWeight),
        )
        // Обе цифры живут в одной клетке, поэтому подсказка называет их обе: из «2×15»
        // без неё не видно, где разы, а где минуты.
        ScheduleHeaderCell(
            icon = R.drawable.ic_airing_design,
            title = "Проветривание: сколько раз × сколько минут",
            modifier = Modifier.weight(AiringWeight),
        )
        if (candling) {
            // Тот же значок, что и на карточке первого овоскопирования в шторке
            // закладки: в конструкторе отмечают день, а не стадию, и рисунок стадии
            // здесь только сбивал бы — узнаваем должен быть сам предмет.
            ScheduleHeaderCell(
                icon = R.drawable.ic_candling_1,
                title = "Овоскопирование в этот день",
                modifier = Modifier.weight(CandlingWeight),
            )
        }
    }
}

/**
 * Значок столбца; по нажатию над ним всплывает его название.
 *
 * `enableUserInput = false` снимает с подсказки её собственные жесты — наведение и
 * долгое нажатие: нужно обычное короткое, о долгом никто не догадается. Показывает её
 * поэтому сам обработчик нажатия.
 *
 * Нажимается вся клетка, а не 16 dp значка: столбец шириной в четверть строки — это
 * ровно тот случай, когда промах мимо картинки выглядит поломкой.
 *
 * **Доля ширины лежит на своём `Box`, а не на `TooltipBox`, и переносить её туда
 * нельзя.** `BasicTooltipBox` внутри рисует `Box { … }` без модификатора, а переданный
 * отдаёт вложенному узлу — до прямого потомка `Row` вес не доходит и молча пропадает.
 * Стоило это ровно того, что видно: столбцы теряли ширину, `fillMaxWidth` первого из
 * них забирал всю строку, и от шапки оставался один значок температуры. Подсказка
 * поэтому обнимает только значок — заодно и всплывает над ним, а не над краем клетки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleHeaderCell(@DrawableRes icon: Int, title: String, modifier: Modifier) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(CellHeight)
            .clip(RoundedCornerShape(CellRadius))
            .clickable { scope.launch { tooltipState.show() } },
        contentAlignment = Alignment.Center,
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(text = title, style = DesignType.Micro) } },
            state = tooltipState,
            enableUserInput = false,
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Строка одного дня.
 *
 * Температура и влажность фильтруются как замер — две цифры до точки и две после.
 * Переворот — целым числом, проветривание — маской «разы × минуты».
 *
 * @param candling `null` — столбца овоскопирования у этой таблицы нет (форма закладки);
 * иначе состояние отметки. Разделять «нет столбца» и «столбец есть, отметки нет» надо
 * именно так: `false` — это ответ «в этот день не смотрим», и рисовать его пустым
 * местом значило бы, что у формы закладки овоскопирований не бывает вовсе.
 * @param onCandlingToggle нажатие по отметке; без него столбец не рисуется.
 * @param autoTurn закладка идёт на автоперевороте: столбец поворотов не правится и
 * показывает «Авто». Норма в нём — это то, что должен сделать человек, а на автоматике
 * делать нечего; пустая клетка, которую всё же можно заполнить, обещала бы обратное и
 * разошлась бы с флагом закладки, по которому шторка подписывает счётчик «на автомате».
 * @param autoAiring то же для проветриваний.
 * @param verdicts расхождение этого дня с закладками, уже идущими в приборе, — заливка
 * клеток температуры и влажности зелёным, жёлтым или красным (`ScheduleOverlap.kt`).
 * `null` — в этот день соседей нет, клетки белые, как в таблице без соседей вовсе.
 * Красятся только эти две: воздух в приборе общий, а переворот и проветривание у
 * каждого лотка свои.
 */
@Composable
internal fun ScheduleRow(
    row: ValueUiState,
    onChange: (ValueUiState) -> Unit,
    candling: Boolean? = null,
    onCandlingToggle: (() -> Unit)? = null,
    autoTurn: Boolean = false,
    autoAiring: Boolean = false,
    verdicts: CellVerdicts? = null,
) {
    val unit = LocalUnits.current.temperature
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.day.toString(),
            style = DesignType.MonoSmallEmphasis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(DayColumnWidth),
        )
        ScheduleCell(
            value = row.temp,
            onValueChange = { onChange(row.copy(temp = it.filterMeasureInput(unit.integerDigits))) },
            numeric = true,
            modifier = Modifier
                .weight(TempWeight)
                .overlapSemantics(verdicts?.temp, verdicts?.neighbours),
            containerColor = verdicts?.temp.overlapColor(),
        )
        ScheduleCell(
            value = row.damp,
            onValueChange = { onChange(row.copy(damp = it.filterMeasureInput())) },
            numeric = true,
            modifier = Modifier
                .weight(DampWeight)
                .overlapSemantics(verdicts?.damp, verdicts?.neighbours),
            containerColor = verdicts?.damp.overlapColor(),
        )
        // Счётчики: только целые числа. Пустая клетка — «нормы нет», ноль — «не делать».
        // Отданный автоматике столбец не пустой, а закрытый: «Авто» вместо прочерка и
        // ввода нет вовсе.
        ScheduleCell(
            value = row.over,
            onValueChange = { onChange(row.copy(over = it.filterCountInput())) },
            numeric = true,
            modifier = Modifier.weight(OverWeight),
            enabled = !autoTurn,
            placeholder = if (autoTurn) AutoCellText else "—",
        )
        // Проветривание — одна клетка на две величины: «2×15» это два проветривания по
        // пятнадцать минут. Разделитель ставит сама маска, набираются только цифры.
        ScheduleCell(
            value = airingCellText(row.airingCount, row.airingTime),
            onValueChange = { raw ->
                val (count, minutes) = parseAiringCell(raw.filterAiringInput())
                onChange(row.copy(airingCount = count, airingTime = minutes))
            },
            numeric = true,
            modifier = Modifier.weight(AiringWeight),
            enabled = !autoAiring,
            placeholder = if (autoAiring) AutoCellText else "—",
        )
        if (candling != null && onCandlingToggle != null) {
            ScheduleCandlingCell(
                checked = candling,
                onToggle = onCandlingToggle,
                modifier = Modifier.weight(CandlingWeight),
            )
        }
    }
}

/**
 * Клетка таблицы.
 *
 * [enabled] `false` — норму в этом столбце ставит не человек, а инкубатор: поле не
 * принимает ввод и показывает «Авто» ([placeholder]). Не «серое и неактивное», а
 * кремовое, как выведенные поля формы, — см. `SheetTextField`.
 */
@Composable
internal fun ScheduleCell(
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    placeholder: String = "—",
    /** Заливка по приговору расхождения; `null` — белая клетка. */
    containerColor: Color? = null,
) {
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        numeric = numeric,
        // Клетка вчетверо уже поля формы: с отступом по умолчанию в неё не влезает
        // даже «37.8».
        horizontalPadding = 6.dp,
        minHeight = CellHeight,
        radius = CellRadius,
        // По центру, а не по левому краю формы: клетка шире значения, столбцы стоят
        // друг под другом, и прижатое влево «37.8» рядом с «3» рвёт колонку. Номер
        // дня в первом столбце центрирован по той же причине.
        textAlign = TextAlign.Center,
        enabled = enabled,
        containerColor = containerColor,
        modifier = modifier,
    )
}

/**
 * Заливка клетки по приговору: совпадает — зелёная, допустимо — янтарная, расходится —
 * красная; без приговора (пустая клетка, сосед без нормы, день без соседей) — белая.
 *
 * Три цвета, а не два, потому что вопрос трёхступенчатый, как и у плиток замера:
 * «небольшое отклонение» там — это «допустимо» здесь, и красить его красным значило бы
 * пугать полградусом, который эмбрион не заметит.
 */
/**
 * Приговор словами — для `TalkBack`, который цвет не читает: «расходится с 2
 * закладками». Без приговора описания нет, как нет и заливки.
 */
private fun Modifier.overlapSemantics(severity: Severity?, neighbours: Int?): Modifier {
    val description = severity.overlapDescription(neighbours ?: return this) ?: return this
    return semantics { stateDescription = description }
}

@Composable
internal fun Severity?.overlapColor(): Color? = when (this) {
    Severity.Normal -> DesignPalette.ScheduleMatch
    Severity.Minor -> DesignPalette.ScheduleTolerable
    Severity.Major -> DesignPalette.ScheduleConflict
    null -> null
}

/**
 * Отметка овоскопирования — клетка-переключатель в конце строки.
 *
 * Ростом и скруглением ровно как [ScheduleCell], хотя ввода в ней нет: столбец стоит в
 * одном ряду с четырьмя полями, и клетка другой высоты сломала бы строку. Отмеченный
 * день залит акцентом, неотмеченный — белый с той же рамкой, что и поле; так видно
 * сразу весь график: три зелёные клетки в столбце из двадцати одной.
 *
 * Флажка (`Checkbox`) здесь нет намеренно: он рисуется своим размером и своими
 * отступами, из-за которых столбец стал бы шире клетки и уже площади нажатия. Нажимается
 * вся клетка целиком — по той же причине, что и в шапке.
 */
@Composable
private fun ScheduleCandlingCell(checked: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(CellHeight)
            .clip(RoundedCornerShape(CellRadius))
            .background(if (checked) DesignPalette.Accent else DesignPalette.Surface)
            // Рамка только у неотмеченной: у залитой она была бы обводкой того же
            // цвета по краю заливки — линия, которую видно только на светлом.
            .then(
                if (checked) Modifier
                else Modifier.border(0.8.dp, DesignPalette.CardBorder, RoundedCornerShape(CellRadius))
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_candling_1),
            contentDescription = if (checked) {
                "Овоскопирование: назначено"
            } else {
                "Овоскопирование: не назначено"
            },
            tint = if (checked) DesignPalette.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Кнопка над таблицей: подменить режим целиком, добавить день, убрать последний. */
@Composable
internal fun ScheduleActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CellRadius),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DesignPalette.Surface,
            contentColor = DesignPalette.Accent,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(CellHeight),
    ) {
        Text(
            text = text,
            style = DesignType.TabLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Кнопка под свёрнутым списком: разворачивает его целиком и сворачивает обратно.
 *
 * Показывает общее число строк — иначе из свёрнутого списка не видно, сколько их там
 * ещё, и кнопку незачем нажимать. Одна на историю замеров в шторке закладки и на список
 * своих видов в «Настройках»: это одно и то же движение — «покажи остальное», — и две
 * кнопки для него разошлись бы в первой же правке.
 */
@Composable
internal fun ShowAllToggle(expanded: Boolean, total: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CellRadius),
        color = DesignPalette.Surface,
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CellRadius))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "Свернуть" else "Показать все ($total)",
                style = DesignType.CaptionEmphasis,
                color = DesignPalette.Accent,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(
                    id = if (expanded) R.drawable.ic_arrow_up_design
                    else R.drawable.ic_arrow_down_design
                ),
                contentDescription = null,
                tint = DesignPalette.Accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Пунктирная кнопка из макета (узел 12:4866).
 *
 * Своя, а не `DashedButton` с экрана инкубатора: там кнопка на 58 dp со скруглением
 * 22 и текстом 16, здесь — 41 dp, 16 и 13, разница видна.
 */
@Composable
internal fun DashedAddButton(text: String, onClick: () -> Unit) {
    // Цвет читается здесь, а не внутри `drawBehind`: та лямбда — не композиция,
    // а [DesignPalette] живёт только в ней.
    val dashedBorder = DesignPalette.DashedBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(41.dp)
            .clip(RoundedCornerShape(FieldRadius))
            .background(DesignPalette.DashedSurface)
            .drawBehind {
                drawRoundRect(
                    color = dashedBorder,
                    cornerRadius = CornerRadius(FieldRadius.toPx()),
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx()), 0f
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus_design),
            contentDescription = null,
            tint = DesignPalette.Accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text = text, style = DesignType.TabLabel, color = DesignPalette.Accent)
    }
}
