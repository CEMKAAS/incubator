package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.BannerAdHost
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.stats.HatchRecord
import ru.zaroslikov.incubator.domain.stats.IncubatorStats
import ru.zaroslikov.incubator.domain.stats.NO_BREED
import ru.zaroslikov.incubator.domain.stats.SpeciesStats
import ru.zaroslikov.incubator.domain.stats.StatsSlice
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.components.formatCount
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.ui.start.speciesEmoji
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Вкладка «Статистика»
 * ([узел 11:3144](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=11-3144)).
 *
 * Всё считается из уже имеющихся закладок — новых полей в базе не появилось, — а сам
 * счёт живёт в `:domain` (`stats/IncubatorStats.kt`), сюда приходит готовым. Экран
 * только раскладывает числа по карточкам.
 *
 * Принимает [IncubatorStats], а не состояние экрана инкубатора: ровно те же карточки
 * показывает «Аналитика», где статистика посчитана по всем закладкам сразу. От разреза
 * зависит одна-единственная фраза внизу, и за неё отвечает [scope].
 *
 * Порядок карточек отвечает на вопросы по убыванию общности: четыре итога сверху
 * («сколько всего», «насколько хорошо», «сколько вывелось», «сколько потеряли»), затем
 * диаграмма видов с породами внутри, затем эффективность — тоже по видам, с породами
 * внутри, — и в конце история выводов, самая подробная и самая редко нужная.
 *
 * Сама карточка, её заголовок, плитка с числом и склонение по числу лежат не здесь, а в
 * `IncubatorTabParts.kt`: «Финансы» собраны из тех же деталей, и вторая их копия
 * разошлась бы с этой на первой же правке скругления.
 */
@Composable
internal fun StatsTab(
    stats: IncubatorStats,
    scope: TabScope = TabScope.One,
    /**
     * Владелец рекламного объявления этой вкладки; `null` — вкладка без рекламы.
     * Заводится на уровне экрана, а не здесь: страница пейджера уходит из композиции
     * при свайпе, и объявление заказывалось бы заново на каждое переключение.
     */
    adHost: BannerAdHost? = null,
) {

    SummaryGrid(stats)

    // Реклама — сразу после первой карточки, а не в хвосте вкладки: ниже ещё пять
    // блоков с диаграммами и историей, и до конца прокрутки доходят единицы.
    if (adHost != null) {
        Spacer(Modifier.height(16.dp))
        AdBanner(adHost)
    }

    Spacer(Modifier.height(16.dp))
    SpeciesChartCard(stats.bySpecies)

    Spacer(Modifier.height(16.dp))
    SpeciesEfficiencyCard(stats.bySpecies)

    Spacer(Modifier.height(16.dp))
    HatchHistoryCard(stats.history)

    Spacer(Modifier.height(16.dp))
    InsightCard(stats, scope)
}

// --- Итоги сверху --------------------------------------------------------------------------

/**
 * Четыре числа: всего яиц, эффективность, вылупилось, отбраковано.
 *
 * Плитки по две в ряд, а не одной сеткой: `LazyVerticalGrid` внутри уже прокручиваемой
 * страницы (`ScrollingPage`) уронил бы измерение — вложенный скролл той же оси.
 * Ряд — [TileRow]: он тянет обе плитки до высоты более высокой из них. В макете они
 * выровнены, а подпись «Отбраковано» легко переносится на две строки.
 *
 * **Под каждым числом стоит его знаменатель, и это не украшение.** «700 яиц» считается
 * по всем закладкам, а «429 вылупилось» — только по завершённым, и рядом эти два числа
 * читаются как ошибка счёта: 700 − 429 − 89 ни на что не похоже. Разошлись они потому,
 * что часть яиц ещё в инкубаторе, а часть завершённых просто не вывелась. Ни одно из
 * двух не подогнать под другое, не потеряв ответа, — поэтому вместо подгонки экран
 * говорит, из чего каждое число сложено.
 */
@Composable
private fun SummaryGrid(stats: IncubatorStats) {
    val total = stats.total
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = formatCount(stats.totalEggs),
            label = "Всего яиц",
            hint = totalEggsHint(total),
            highlighted = true,
            modifier = tileWeight(),
        )
        MetricCard(
            value = stats.hatchRate?.let { "$it%" } ?: "—",
            label = "Эффективность",
            hint = if (total.finishedEggs > 0) {
                "${formatCount(total.finishedHatched)} из ${formatCount(total.finishedEggs)} завершённых"
            } else {
                "нет завершённых закладок"
            },
            modifier = tileWeight(),
        )
    }
    Spacer(Modifier.height(12.dp))
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = formatCount(stats.hatched),
            label = "Вылупилось",
            hint = if (total.finishedEggs > 0) "из ${formatCount(total.finishedEggs)} завершённых" else null,
            valueColor = DesignPalette.Accent,
            modifier = tileWeight(),
        )
        MetricCard(
            value = formatCount(stats.rejected),
            label = "Отбраковано",
            hint = if (total.eggs > 0) "из ${formatCount(total.eggs)} заложенных" else null,
            // Красный тот же, что у расхода и у прерванной закладки: потеря есть потеря,
            // и второго красного в приложении заводить незачем.
            valueColor = DesignPalette.Expense,
            modifier = tileWeight(),
        )
    }
}

/** «10 закладок · 150 в инкубации» — вторая половина только когда идущие есть. */
private fun totalEggsHint(total: StatsSlice): String = buildString {
    append(plural(total.batches, "закладка", "закладки", "закладок"))
    if (total.activeEggs > 0) append(" · ${formatCount(total.activeEggs)} в инкубации")
}

// --- Диаграмма «яйца по видам, породы внутри» ----------------------------------------------

/** Высота самого высокого столбца — снята с макета (78.75 % от 160 dp). */
private val MaxBarHeight = 126.dp
private val BarWidth = 38.dp

/**
 * Наименьший просвет между столбцами — он же то, что делает ряд прокручиваемым.
 *
 * В макете видов три, и они расставлены `SpaceEvenly` по ширине карточки. На
 * «Аналитике» ряд считается по всему хозяйству: пять встроенных видов плюс любое число
 * своих, — и при восьми столбцах `SpaceEvenly` сводит просветы к нулю, а дальше просто
 * срезает лишние столбцы краем карточки, ничего об этом не сказав. Столбец уже
 * [BarWidth], и сужать его некуда: под ним стоит эмодзи, а над ним число.
 *
 * 16 dp — примерно тот просвет, который `SpaceEvenly` и так даёт пяти столбцам на узком
 * телефоне, так что на помещающемся ряде ничего не меняется.
 */
private val MinBarGap = 16.dp

/**
 * Высота ряда столбцов: 126 dp самого столбца плюс подпись сверху и эмодзи снизу.
 *
 * В макете ряд ровно 160 dp, но там над столбцом ничего не стоит. Число яиц над
 * столбцом добавлено сверх макета — иначе высоту приходится читать глазами по
 * легенде, — и ряду нужно на него место: при 160 dp самый высокий столбец
 * выталкивал свой эмодзи за нижнюю границу.
 */
private val ChartRowHeight = 190.dp

/**
 * Столбцы по видам птицы, а под ними — раскрываемая легенда с породами.
 *
 * Породы спрятаны за нажатием на строку вида, а не разложены сразу: у одного вида их
 * бывает пять-шесть, и развёрнутыми они превращают карточку в простыню, из которой
 * главное — соотношение видов — уже не читается. Раскрытое состояние живёт в
 * `rememberSaveable`, чтобы поворот экрана и уход на соседнюю вкладку его не сбрасывали.
 *
 * **Ряд столбцов ездит вбок, когда виды в него не помещаются.** В макете видов три и
 * они расставлены по ширине карточки; на «Аналитике» ряд считается по всему хозяйству —
 * пять встроенных видов плюс любое число своих, — и раньше лишние столбцы просто
 * срезались краем карточки, молча. Прокрутка и мерка, по которой она включается, —
 * в [MinBarGap].
 *
 * [BoxWithConstraints] обнимает заголовок и график, но **не** легенду: ширина нужна для
 * обоих — подзаголовок называет прокрутку, только когда она есть, — а легенда
 * разворачивает породы анимацией по высоте, и держать её внутри подкомпозиции значило
 * бы измерять её заново на каждом кадре этого разворота.
 */
@Composable
private fun SpeciesChartCard(species: List<SpeciesStats>) {
    TabCard {
        val withEggs = species.filter { it.slice.eggs > 0 }
        if (withEggs.isEmpty()) {
            CardHeader(
                title = "Яйца по видам птицы",
                subtitle = "Нажмите вид, чтобы увидеть породы",
            )
            EmptyNote("Пока не заложено ни одного яйца.")
            return@TabCard
        }

        val max = withEggs.maxOf { it.slice.eggs }.coerceAtLeast(1)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Ряд прокручивается ровно тогда, когда не помещается, и одним и тем же
            // кодом: ширина содержимого — либо ширина карточки (столбцы расставляет
            // `SpaceEvenly`, то есть в точности макет), либо необходимая, и тогда
            // просветы сжимаются до [MinBarGap], а ряд начинает ездить. Отдельной
            // ветки «а если их много» нет — она разошлась бы с первой при любой
            // правке столбца.
            val needed = BarWidth * withEggs.size + MinBarGap * (withEggs.size + 1)
            // Ширина снимается здесь, а не читается по месту: внутри `Column` неявным
            // получателем становится `ColumnScope`, и `maxWidth` из констрейнтов оттуда
            // уже не виден.
            val available = maxWidth
            val scrollable = needed > available

            Column {
                CardHeader(
                    title = "Яйца по видам птицы",
                    // Про прокрутку сказано словами, и только когда она есть. Сама
                    // собой она себя не выдаёт: столбцы расставлены равномерно, так
                    // что у правого края стоит просвет, а не обрезанный столбец, — ряд
                    // выглядит законченным. Обещать же листание там, где листать
                    // нечего, — значит учить не верить подписям.
                    subtitle = if (scrollable) {
                        "Нажмите вид, чтобы увидеть породы · листайте график вбок"
                    } else {
                        "Нажмите вид, чтобы увидеть породы"
                    },
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .width(needed.coerceAtLeast(available))
                        .height(ChartRowHeight),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    withEggs.forEachIndexed { index, item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatCount(item.slice.eggs),
                                style = DesignType.MonoAccent,
                                color = barColor(index),
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(BarWidth)
                                    .height(MaxBarHeight * (item.slice.eggs / max.toFloat()))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(barColor(index))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(text = speciesEmoji(item.slice.name), fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
        Spacer(Modifier.height(12.dp))

        withEggs.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            SpeciesLegendRow(item = item, color = barColor(index))
        }
    }
}

@Composable
private fun SpeciesLegendRow(item: SpeciesStats, color: Color) {
    // Вид, у которого единственная «порода» — это «Без породы», раскрывать нечего:
    // строка внутри повторила бы ту же цифру под именем, которое ничего не называет.
    val expandable = item.breeds.size > 1 ||
            item.breeds.singleOrNull()?.name?.let { it != NO_BREED } == true
    var expanded by rememberSaveable(item.slice.name) { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "speciesArrow",
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (expandable) Modifier.clickable { expanded = !expanded } else Modifier
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = speciesEmoji(item.slice.name), fontSize = 15.sp)
            Spacer(Modifier.size(8.dp))
            // Вес — на обёртке, а не на самой строке: [TruncatedText] отдаёт модификатор
            // вложенному узлу, где `weight` не значит ничего. См. её описание.
            Box(Modifier.weight(1f)) {
                TruncatedText(
                    text = item.slice.name,
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (expandable) {
                Text(
                    text = "›",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrow),
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = plural(item.slice.eggs, "яйцо", "яйца", "яиц"),
                style = DesignType.MonoAccent,
                color = color,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(start = 23.dp, top = 2.dp, bottom = 4.dp)) {
                item.breeds.forEach { breed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.weight(1f)) {
                            TruncatedText(
                                text = breed.name,
                                style = DesignType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = breedSummary(breed),
                            style = DesignType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** «120 яиц · 87 %» либо просто «120 яиц», пока выводить нечего. */
private fun breedSummary(breed: StatsSlice): String =
    plural(breed.eggs, "яйцо", "яйца", "яиц").let { eggs ->
        breed.rate?.let { "$eggs · $it%" } ?: eggs
    }

// --- Эффективность -------------------------------------------------------------------------

/**
 * Виды полосками, а породы вида — под его строкой, по нажатию.
 *
 * Разрезы без единой завершённой закладки ([StatsSlice.rate] == null) не отбрасываются,
 * а показываются с прочерком: «мы это закладывали, но итога ещё нет» — ответ, а пустая
 * строчка вместо вида выглядела бы как потерянные данные.
 *
 * **Породы и виды — одна карточка, а не две подряд, и породы спрятаны за нажатием.** До
 * этого «Эффективность по породам» стояла отдельной карточкой сразу под этой: те же
 * полоски, тот же процент, те же цвета, — и вид назывался дважды, второй раз заголовком
 * группы, который нарочно не нёс чисел, чтобы не читаться как ещё одна порода. Держать
 * их рядом развёрнутыми было нечем: у одного вида пород бывает пять-шесть, и вся
 * страница между «Эффективностью» и историей выводов превращалась в простыню, из
 * которой соотношение самих видов — то, ради чего карточку открывают, — уже не
 * читалось. Породы лежат там, где им и место: внутри своего вида, на расстоянии одного
 * нажатия.
 *
 * Это ровно та же складка, что у легенды диаграммы выше (`SpeciesLegendRow`), и
 * намеренно: на одной вкладке два разных способа раскрыть породы вида человек считал бы
 * за два разных действия. Отсюда и общие мелочи — стрелка «›», поворачивающаяся на 90°,
 * `rememberSaveable` по имени вида (поворот экрана и уход на соседнюю вкладку не
 * схлопывают раскрытое) и правило «раскрывать нечего»: вид, у которого единственная
 * «порода» — это [NO_BREED], не раскрывается вовсе, иначе строка внутри повторила бы
 * цифры вида под именем, которое ничего не называет.
 *
 * Цвет пород — цвет их вида (`barColor` по его позиции), тот же, что у столбца
 * диаграммы и у строки легенды. Отступ в 20 dp у вложенных полосок не украшение:
 * начинаясь там же, где полоска вида, они читались бы как продолжение общего списка
 * видов.
 */
@Composable
private fun SpeciesEfficiencyCard(species: List<SpeciesStats>) {
    TabCard {
        CardHeader(
            title = "Эффективность по видам",
            subtitle = "Процент вывода — по завершённым закладкам",
        )

        if (species.isEmpty()) {
            EmptyNote("Закладок пока нет.")
            return@TabCard
        }

        Spacer(Modifier.height(14.dp))
        species.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            SpeciesEfficiencyGroup(item = item, color = barColor(index))
        }
    }
}

/** Строка вида и — по нажатию — полоски его пород под ней. */
@Composable
private fun SpeciesEfficiencyGroup(item: SpeciesStats, color: Color) {
    val expandable = item.breeds.size > 1 ||
            item.breeds.singleOrNull()?.name?.let { it != NO_BREED } == true
    var expanded by rememberSaveable(item.slice.name) { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "breedsArrow",
    )

    Column(Modifier.fillMaxWidth()) {
        EfficiencyRow(
            slice = item.slice,
            color = color,
            emoji = true,
            arrowRotation = if (expandable) arrow else null,
            onClick = if (expandable) ({ expanded = !expanded }) else null,
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(start = 20.dp)) {
                item.breeds.forEach { breed ->
                    Spacer(Modifier.height(12.dp))
                    EfficiencyRow(slice = breed, color = color, emoji = false)
                }
            }
        }
    }
}

/**
 * Полоска одного разреза: имя, процент, шкала и знаменатель под ней.
 *
 * [arrowRotation] — поворот стрелки «›» в градусах, `null` — стрелки нет. Угол приходит
 * снаружи, а не считается здесь: анимация принадлежит раскрытию, а раскрыт вид или нет,
 * знает только группа. Нажимается **вся** строка, а не одна стрелка: стрелка — это
 * указатель состояния размером в шрифт, и попадать в неё пальцем на полоске во всю
 * ширину незачем.
 */
@Composable
private fun EfficiencyRow(
    slice: StatsSlice,
    color: Color,
    emoji: Boolean,
    modifier: Modifier = Modifier,
    arrowRotation: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji) {
                Text(text = speciesEmoji(slice.name), fontSize = 15.sp)
                Spacer(Modifier.size(8.dp))
            }
            Box(Modifier.weight(1f)) {
                TruncatedText(
                    text = slice.name,
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (arrowRotation != null) {
                Text(
                    text = "›",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation),
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = slice.rate?.let { "$it%" } ?: "—",
                style = DesignType.MonoAccent,
                color = if (slice.rate == null) MaterialTheme.colorScheme.onSurfaceVariant
                else color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DesignPalette.PillSurface)
        ) {
            val rate = slice.rate
            if (rate != null && rate > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(rate.coerceIn(0, 100) / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = sliceFooter(slice),
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * «3 закладки (2 заверш.) · 120 яиц · 104 птенца · 9 отбраковано».
 *
 * Вида здесь нет: у строки вида он в имени, у породы — в заголовке её группы. Число
 * завершённых приписывается только когда часть закладок ещё идёт: процент над полоской
 * считается по ним одним, и без этой оговорки он не сходится с яйцами и птенцами той же
 * строки — та же несостыковка, что и в плитках наверху.
 */
private fun sliceFooter(slice: StatsSlice): String = buildString {
    append(plural(slice.batches, "закладка", "закладки", "закладок"))
    if (slice.activeBatches > 0 && slice.finishedBatches > 0) {
        append(" (${formatCount(slice.finishedBatches)} заверш.)")
    }
    append(" · ")
    append(plural(slice.eggs, "яйцо", "яйца", "яиц"))
    append(" · ")
    append(plural(slice.hatched, "птенец", "птенца", "птенцов"))
    if (slice.rejected > 0) {
        append(" · ")
        append("${formatCount(slice.rejected)} отбраковано")
    }
}

// --- История выводов -----------------------------------------------------------------------

/**
 * Завершённые закладки, свежие сверху.
 *
 * Прерванные («Не завершено») стоят в том же списке — история, из которой их убрали,
 * показывала бы инкубатор лучше, чем он есть. Отличаются подписью и цветом процента.
 */
@Composable
private fun HatchHistoryCard(history: List<HatchRecord>) {
    var showAll by rememberSaveable { mutableStateOf(false) }

    TabCard {
        CardHeader(
            title = "История выводов",
            subtitle = "Завершённые закладки, свежие сверху",
        )

        if (history.isEmpty()) {
            EmptyNote("Завершённых закладок пока нет — здесь появится история выводов.")
            return@TabCard
        }

        // Первыми показываем пять: за годы работы закладок набирается много, а карточка
        // стоит посреди прокручиваемой страницы, и разворачивать её целиком каждый раз
        // значит отодвигать всё, что ниже.
        val shown = if (showAll) history else history.take(HistoryPreview)

        Spacer(Modifier.height(14.dp))
        shown.forEachIndexed { index, record ->
            if (index > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                Spacer(Modifier.height(10.dp))
            }
            HatchHistoryRow(record)
        }

        if (history.size > HistoryPreview) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (showAll) "Свернуть" else "Показать все (${history.size})",
                style = DesignType.MonoAccent,
                color = DesignPalette.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAll = !showAll }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

private const val HistoryPreview = 5

@Composable
private fun HatchHistoryRow(record: HatchRecord) {
    val stopped = record.status == BatchStatus.Stopped
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = speciesEmoji(record.species), fontSize = 18.sp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            // Название закладки человек пишет сам, и в строке истории его видно ровно
            // столько, сколько влезло, — а различаются соседние закладки как раз концом
            // («Куры 2», «Куры 2 весна»). Обрезанное договаривает подсказка. Вес здесь
            // на колонке, а не на самой строке: [TruncatedText] модификатор отдаёт
            // вложенному узлу — см. её описание.
            TruncatedText(
                text = record.title,
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            TruncatedText(
                text = historySubtitle(record),
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = record.rate?.let { "$it%" } ?: "—",
                style = DesignType.MonoAccent,
                color = if (stopped) DesignPalette.Expense else DesignPalette.Accent,
            )
            Text(
                text = if (stopped) "не завершено" else "${formatCount(record.hatched)} из ${formatCount(record.eggs)}",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * «Несушка БИ-2 · Ломан Браун · 12.03.2026 · 4 отбраковано» — пустые части выпадают.
 *
 * Инкубатор стоит **первым и только в «Аналитике»**. Первым — потому что в истории всего
 * хозяйства «где это было» спрашивают раньше, чем «какая порода», и потому что конец
 * строки обрезается первым, а инкубатор здесь как раз то новое, ради чего строку и
 * читают. Только в «Аналитике» — потому что имя приходит из
 * [ru.zaroslikov.incubator.domain.stats.HatchRecord.incubator], а его заполняет
 * `AnalyticsViewModel`; экран одного инкубатора карту имён не передаёт, и там строка
 * остаётся прежней: столбец из одного и того же названия ничего не различал бы, а место
 * у породы и даты отнял.
 */
private fun historySubtitle(record: HatchRecord): String = listOf(
    record.incubator,
    record.breed,
    record.dateEnd,
    if (record.rejected > 0) "${formatCount(record.rejected)} отбраковано" else "",
).filter { it.isNotBlank() }.joinToString(" · ")

// --- Вывод внизу ---------------------------------------------------------------------------

@Composable
private fun InsightCard(stats: IncubatorStats, scope: TabScope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DesignPalette.InsightSurface),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        val rate = stats.hatchRate
        val text = if (rate == null) {
            buildAnnotatedString {
                append("Завершённых закладок пока нет — эффективность вывода появится здесь после первого вывода.")
            }
        } else {
            buildAnnotatedString {
                append("Эффективность вывода " + scope.efficiencySubject + " — ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$rate%") }
                append(". ")
                append(hatchVerdict(rate))
                // Лучшую породу называем только когда сравнивать есть с чем: одна
                // порода «лучшая» всегда, и от такой подсказки никакого толку.
                bestBreed(stats)?.let { best ->
                    append(" Лучше всех выводится ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(best.name) }
                    // Вид в скобках: в карточке пород его несёт заголовок группы, а
                    // здесь порода стоит одна, вырванная из своей группы.
                    if (best.species.isNotBlank()) append(" (${best.species})")
                    append(" — ${best.rate}%.")
                }
            }
        }
        Text(
            text = text,
            style = DesignType.Body,
            color = DesignPalette.Accent,
            modifier = Modifier.padding(20.dp),
        )
    }
}

/**
 * Порода с лучшим процентом — если пород с итогом хотя бы две.
 *
 * «Без породы» из сравнения исключена: это не порода, а несколько разных закладок, у
 * которых графу не заполнили, и советовать её бессмысленно.
 */
private fun bestBreed(stats: IncubatorStats): StatsSlice? {
    val rated = stats.breeds.filter { it.rate != null && it.name != NO_BREED }
    return if (rated.size >= 2) rated.maxByOrNull { it.rate ?: 0 } else null
}

private fun hatchVerdict(rate: Int): String = when {
    rate >= 85 -> "Это отличный результат для домашнего хозяйства."
    rate >= 70 -> "Это хороший результат, но запас для роста ещё есть."
    rate >= 50 -> "Есть куда расти: проверьте температуру и влажность по дням."
    else -> "Стоит пересмотреть режим инкубации и качество яйца."
}

// --- Цвета диаграммы -----------------------------------------------------------------------

/**
 * Цвет столбца задаётся позицией в отсортированном списке — так же, как в макете.
 *
 * Композабл, потому что набор цветов теперь зависит от темы: [DesignPalette] читается
 * из композиции, и вне её никакого «текущего» списка столбцов нет.
 */
@Composable
@ReadOnlyComposable
private fun barColor(index: Int): Color =
    DesignPalette.ChartBars[index % DesignPalette.ChartBars.size]
