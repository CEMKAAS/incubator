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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.ads.AdBanner
import ru.zaroslikov.incubator.ads.BannerAdHost
import ru.zaroslikov.incubator.ui.batch.stageTitle
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
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
import ru.zaroslikov.incubator.ui.start.SpeciesGlyph
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Вкладка «Статистика»
 * ([узел 11:3144](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=11-3144)).
 *
 * Счёт живёт в `:domain` (`stats/IncubatorStats.kt`), экран раскладывает числа по карточкам.
 * Принимает [IncubatorStats], а не состояние экрана инкубатора: те же карточки показывает
 * «Аналитика» по всем закладкам; от разреза зависит одна фраза внизу — [scope].
 *
 * Порядок — от общего к частному: четыре итога, диаграмма видов, эффективность, история выводов.
 * Карточка, заголовок, плитка и склонение лежат в `IncubatorTabParts.kt` — общие с «Финансами».
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
 * **Все четыре — только по завершённым закладкам, и «Отбраковано» — это остаток**:
 * всего яиц минус вылупившиеся, так что «Всего = Вылупилось + Отбраковано» сходится
 * всегда. Яйца, что дожили до вывода и не вылупились, тоже брак. Подписей под
 * числами нет — по просьбе владельца.
 */
@Composable
private fun SummaryGrid(stats: IncubatorStats) {
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = formatCount(stats.total.finishedEggs),
            label = "Всего яиц",
            highlighted = true,
            modifier = tileWeight(),
        )
        MetricCard(
            value = stats.hatchRate?.let { "$it%" } ?: "—",
            label = "Эффективность",
            modifier = tileWeight(),
        )
    }
    Spacer(Modifier.height(12.dp))
    TileRow(spacing = 12.dp) {
        MetricCard(
            value = formatCount(stats.total.finishedHatched),
            label = "Вылупилось",
            valueColor = DesignPalette.Accent,
            modifier = tileWeight(),
        )
        MetricCard(
            // Всё, что заложено в завершённые закладки и не вылупилось: брак вручную, на
            // овоскопировании и яйца, оставшиеся в лотке без птенца, — тогда плитки сходятся.
            value = formatCount((stats.total.finishedEggs - stats.total.finishedHatched).coerceAtLeast(0)),
            label = "Отбраковано",
            // Красный тот же, что у расхода и у прерванной закладки: потеря есть потеря,
            // и второго красного в приложении заводить незачем.
            valueColor = DesignPalette.Expense,
            modifier = tileWeight(),
        )
    }
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
 * Столбцы по видам птицы, а под ними — раскрываемая легенда с породами (раскрытое в
 * `rememberSaveable`).
 *
 * **Ряд столбцов ездит вбок, когда виды не помещаются** — на «Аналитике» их пять встроенных плюс
 * свои; мерка включения прокрутки — в [MinBarGap].
 *
 * [BoxWithConstraints] обнимает заголовок и график, но **не** легенду: она разворачивается
 * анимацией по высоте, и внутри подкомпозиции её измеряли бы заново на каждом кадре.
 */
@Composable
private fun SpeciesChartCard(species: List<SpeciesStats>) {
    TabCard {
        val withEggs = species.filter { it.slice.eggs > 0 }
        if (withEggs.isEmpty()) {
            CardHeader(title = "Яйца по видам птицы")
            EmptyNote("Завершённых закладок пока нет — график появится после первого вывода.")
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

            Column {
                CardHeader(title = "Яйца по видам птицы")

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
                            SpeciesGlyph(bird = item.slice.name, fontSize = 18.sp)
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
            SpeciesGlyph(bird = item.slice.name, fontSize = 15.sp)
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
 * Виды полосками, а породы вида — под его строкой, по нажатию. Приходят только завершённые закладки
 * (см. `SpeciesStats`); прочерк вместо процента — у разреза, где завершённые были без яиц
 * ([StatsSlice.rate] == null).
 *
 * Породы и виды — одна карточка, породы за нажатием: у вида их бывает пять-шесть, и развёрнутыми они
 * скрывают соотношение видов. Складка та же, что у легенды диаграммы (`SpeciesLegendRow`): стрелка
 * «›», `rememberSaveable` по имени вида, а вид с единственной «породой» [NO_BREED] не раскрывается.
 *
 * Цвет пород — цвет их вида (`barColor`). Отступ вложенных полосок 8 dp, чтобы не читались как
 * продолжение списка видов; он и промежутки между строками уменьшены по просьбе владельца
 * (2026-10-02).
 */
@Composable
private fun SpeciesEfficiencyCard(species: List<SpeciesStats>) {
    TabCard {
        CardHeader(title = "Эффективность по видам")

        if (species.isEmpty()) {
            EmptyNote("Завершённых закладок пока нет — эффективность появится после первого вывода.")
            return@TabCard
        }

        Spacer(Modifier.height(14.dp))
        species.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(8.dp))
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
            Column(Modifier.padding(start = 8.dp)) {
                item.breeds.forEach { breed ->
                    Spacer(Modifier.height(8.dp))
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
                SpeciesGlyph(bird = slice.name, fontSize = 15.sp)
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
        CardHeader(title = "История выводов")

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

/**
 * Строка истории; нажатие раскрывает под ней, сколько отбраковано и на каком
 * овоскопировании.
 *
 * Отбраковка в самой строке не печатается: она нужна, когда разбираются, что пошло не
 * так, а не при каждом взгляде на список, — свёрнутая строка держит только «что, где,
 * когда и сколько вывелось». Раскрыть можно, только когда есть что показать.
 */
@Composable
private fun HatchHistoryRow(record: HatchRecord) {
    val stopped = record.status == BatchStatus.Stopped
    val expandable = record.rejected > 0 || record.candlingCulls.isNotEmpty()
    var expanded by rememberSaveable(record.batchId) { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "cullArrow",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        HatchHistoryHead(record, stopped, arrowRotation = if (expandable) arrow else null)
        AnimatedVisibility(
            visible = expanded && expandable,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CullBreakdown(record)
        }
    }
}

@Composable
private fun HatchHistoryHead(record: HatchRecord, stopped: Boolean, arrowRotation: Float?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesGlyph(bird = record.species, fontSize = 18.sp)
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
        if (arrowRotation != null) {
            Text(
                text = "›",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation),
            )
            Spacer(Modifier.size(8.dp))
        }
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
 * Раскрытая отбраковка закладки: ручная часть, каждое записанное овоскопирование и итог.
 *
 * Во всю ширину строки, без отступа (просьба владельца, 2026-10-02): цифры встают в
 * один столбец с процентом над ними. Овоскопирование с нулём тоже в списке — «проверили,
 * всё целое» такой же ответ на «на каком», как и «убрали пять».
 */
@Composable
private fun CullBreakdown(record: HatchRecord) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        if (record.manualRejected > 0) {
            CullLine("Отбраковано вручную", record.manualRejected)
        }
        record.candlingCulls.forEach { cull ->
            CullLine("${stageTitle(cull.stage)} · день ${cull.day}", cull.rejected)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
        Spacer(Modifier.height(4.dp))
        CullLine("Всего отбраковано", record.rejected, emphasis = true)
    }
}

@Composable
private fun CullLine(label: String, count: Int, emphasis: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Text(
                text = label,
                style = DesignType.Caption,
                color = if (emphasis) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = plural(count, "яйцо", "яйца", "яиц"),
            style = DesignType.Caption,
            fontWeight = if (emphasis) FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * «Несушка БИ-2 · Ломан Браун · 12.03.2026» — пустые части выпадают. Отбраковка раскрывается по
 * нажатию на строку ([CullBreakdown]).
 *
 * Инкубатор стоит первым и **только в «Аналитике»**: имя приходит из
 * [ru.zaroslikov.incubator.domain.stats.HatchRecord.incubator], а его заполняет `AnalyticsViewModel`.
 *
 * **Свой вид назван словом перед породой** (просьба владельца, 2026-10-02): у своего вида вместо
 * эмодзи общее 🥚. «Свой» — не из пяти встроенных (у удалённого эмодзи то же). Если название
 * закладки совпадает с видом, второй раз его не пишем.
 */
private fun historySubtitle(record: HatchRecord): String = listOf(
    record.incubator,
    record.species.takeIf { it !in SpeciesCatalog.BUILT_IN && it != record.title }.orEmpty(),
    record.breed,
    record.dateEnd,
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
