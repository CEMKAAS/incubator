package ru.zaroslikov.incubator.design.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import kotlin.math.abs

/** Зазор между вкладками в дорожке переключателя. */
private val TabGap = 4.dp

/**
 * Высота дорожки за вычетом её внутренних 4 dp — из макета.
 *
 * Минимум, а не точное значение: подпись набрана 13 sp и на крупном системном кегле
 * в одну строку не помещается — «Статистика» кончалась на «Статис…», то есть вкладка
 * переставала называть свою страницу. Теперь подпись берёт вторую строку, а дорожка
 * вырастает под неё.
 */
private val TabTrackHeight = 39.5.dp

/**
 * Насколько мельче значок с подписью на вкладке, чья страница ушла с экрана. Меньше
 * трогать нельзя: подпись набрана 13 sp, и заметное уменьшение выглядело бы уже не
 * глубиной, а вторым кеглем.
 */
private const val TabIdleScale = 0.92f

/** Сжатие вкладки под пальцем. */
private const val TabPressScale = 0.94f

/** Одна вкладка переключателя: подпись и значок из `res/drawable/ic_*_design*.xml`. */
data class SlidingTab(val title: String, @param:DrawableRes val icon: Int)

/**
 * Сегментированный переключатель страниц: белая «таблетка» по кремовой дорожке. Он не
 * столько орган управления, сколько указатель — без него о том, что содержимое листается
 * вбок, никто бы не догадался.
 *
 * «Таблетка» не перекрашивает вкладку, а ездит: она нарисована один раз под подписями и
 * смещается по [position] — дробной странице пейджера. Поэтому она следует за пальцем во
 * время свайпа и доезжает вместе со страницей при нажатии на вкладку; подписи в это же
 * время перекрашиваются и меняют размер не рывком, а долей близости к своей странице.
 * Перекраска рывком на полпути свайпа и была тем, что выдавало в переключателе не
 * указатель положения, а несколько отдельных кнопок.
 *
 * [position] — лямбда, а не значение: доля прокрутки меняется каждый кадр, и читать её
 * надо здесь, иначе каждый кадр перерисовывался бы весь экран вместе с пейджером.
 * [onSelect] получает номер вкладки в [tabs] — он же номер страницы пейджера.
 *
 * Внешние отступы задаёт вызывающий через [modifier]: в шторке закладки дорожка идёт от
 * края до края её собственных полей, а на экране инкубатора отбивается сверху и снизу.
 */
@Composable
fun SlidingTabSwitcher(
    tabs: List<SlidingTab>,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = position().coerceIn(0f, (tabs.size - 1).toFloat())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(DesignPalette.TabTrack)
            .padding(4.dp)
            .heightIn(min = TabTrackHeight),
    ) {
        // «Таблетка» меряется по дорожке, а не наоборот: `matchParentSize` выносит её из
        // измерения, поэтому высоту задаёт ряд подписей под ней. Пока высота была
        // константой, это было неважно, и `BoxWithConstraints` стоял снаружи; с `heightIn`
        // он стал спрашивать высоту у детей, а дети — `fillMaxHeight` — у него, и
        // переключатель разворачивался на весь экран. Вложенный внутрь `matchParentSize`,
        // он получает готовые размеры и никого ни о чём не спрашивает.
        BoxWithConstraints(Modifier.matchParentSize()) {
            val tabWidth = (maxWidth - TabGap * (tabs.size - 1)) / tabs.size
            Box(
                modifier = Modifier
                    .offset(x = (tabWidth + TabGap) * current)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(DesignPalette.TabPill)
            )
        }

        Row(
            // `IntrinsicSize.Min` — то же, что делает `TileRow`: высота ряда по самой
            // высокой вкладке, и она же достаётся остальным через `fillMaxHeight`. Без
            // него вкладка на всю высоту ряда просит высоту у ряда, а ряд — у неё.
            //
            // И `align(Center)` — потому что ряд больше не растягивается на всю дорожку:
            // когда подписи ниже её минимальной высоты (обычный кегль — это всегда так),
            // `Box` прижал бы их к верхнему краю, и подписи встали бы выше середины
            // «таблетки», которая идёт во всю высоту.
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TabGap),
        ) {
            tabs.forEachIndexed { index, tab ->
                // 1 — страница вкладки на экране целиком, 0 — ушла за соседнюю.
                val selection = (1f - abs(current - index)).coerceIn(0f, 1f)
                val tint = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.onSurface,
                    selection,
                )
                // Нажатие отзывается сжатием подписи, а не рябью: рябь заливает круг
                // вкладки целиком и рядом с едущей «таблеткой» читается как второй,
                // спорящий с ней индикатор. Пружина — чтобы палец отпускался с отдачей.
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val press by animateFloatAsState(
                    targetValue = if (pressed) TabPressScale else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "tabPress",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Масштабируется содержимое, а не вкладка: её ширина держит раскладку
                    // и площадь нажатия, а расти и убывать должны значок с подписью.
                    // Ушедшая вкладка чуть мельче — вместе с приглушённым цветом это и
                    // читается как «кнопка отошла назад», а не просто побледнела. Доля
                    // берётся из той же [selection], что и цвет, поэтому размер едет за
                    // пальцем вместе с «таблеткой», а не доскакивает после свайпа.
                    Row(
                        modifier = Modifier.graphicsLayer {
                            val scale =
                                press * (TabIdleScale + (1f - TabIdleScale) * selection)
                            scaleX = scale
                            scaleY = scale
                        },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = tab.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = tab.title,
                            // Перенос по слогам, как у подписей плиток: одним словом
                            // «Статистика» и «Расписание» ломались бы по букве.
                            style = DesignType.TabLabel.copy(hyphens = Hyphens.Auto),
                            color = tint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
