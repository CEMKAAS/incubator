package ru.zaroslikov.incubator.ui.stories

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.InventoryApplication
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.stories.Story
import ru.zaroslikov.incubator.stories.StoryImages

/** Диаметр кружка вместе с кольцом. */
private val CircleSize = 68.dp

/** Толщина кольца и зазор между ним и картинкой. */
private val RingWidth = 2.5.dp
private val RingGap = 3.dp

/** Ширина ячейки: кружок плюс воздух под подпись, которая шире кружка. */
private val CellWidth = 76.dp

/**
 * Лента историй: кружки превью над списком инкубаторов.
 *
 * **Непросмотренная — в цветном кольце, просмотренная — в сером**, и сервер уже ставит
 * непросмотренные первыми. Цвет кольца — зелёный акцент, уходящий в коричневый акцент дат:
 * своя пара приложения, а не чужой «инстаграмный» градиент — кружок стоит на кремовом фоне
 * рядом с карточками, и радуга над ними читалась бы рекламой.
 *
 * Строка прокручивается вбок до краёв экрана ([bleed]), а первый кружок стоит на тех же
 * 20 dp, что и карточки: обрезанный полями экрана ряд выглядит недорисованным, а
 * прижатый к краю — съехавшим.
 */
@Composable
fun StoriesRow(
    stories: List<Story>,
    onOpen: (Story) -> Unit,
    modifier: Modifier = Modifier,
    /** Поля экрана, которые строка перекрывает, чтобы прокручиваться от края до края. */
    screenPadding: Dp = 20.dp,
) {
    val images = storyImages()
    LazyRow(
        modifier = modifier.bleed(screenPadding),
        contentPadding = PaddingValues(horizontal = screenPadding - (CellWidth - CircleSize) / 2),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(stories, key = { _, story -> story.id }) { _, story ->
            StoryCircle(story = story, images = images, onClick = { onOpen(story) })
        }
    }
}

@Composable
private fun StoryCircle(story: Story, images: StoryImages, onClick: () -> Unit) {
    val ring = if (story.viewed) {
        Brush.linearGradient(listOf(DesignPalette.CardBorder, DesignPalette.CardBorder))
    } else {
        Brush.linearGradient(listOf(DesignPalette.Accent, DesignPalette.DateEmphasis))
    }
    Column(
        modifier = Modifier
            .width(CellWidth)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = story.title + if (story.viewed) ", просмотрена" else ", новая история"
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .border(RingWidth, ring, CircleShape)
                .padding(RingWidth + RingGap)
                .clip(CircleShape)
                .background(DesignPalette.PillSurface),
            contentAlignment = Alignment.Center,
        ) {
            val side = with(LocalDensity.current) { CircleSize.roundToPx() }
            val bitmap = rememberStoryBitmap(images, story.coverUrl, side)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Пока картинка едет или её нет — первая буква заголовка: кружок не пустой
                // и уже отличим от соседа.
                Text(
                    text = story.title.take(1).uppercase(),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = story.title,
            // Перенос по слогам: длинное слово иначе рвётся где придётся — «Овоскопиров / ание».
            style = DesignType.Micro.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
            color = if (story.viewed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Загрузчик картинок историй из контейнера — один на процесс, с общим кэшем. */
@Composable
internal fun storyImages(): StoryImages {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as InventoryApplication).container.stories.images
    }
}

/**
 * Картинка по адресу, уменьшенная до [maxSide]; `null`, пока едет или если не приехала.
 * Из памяти — сразу, в том же кадре, иначе кружок мигал бы буквой при каждом возвращении.
 */
@Composable
internal fun rememberStoryBitmap(images: StoryImages, url: String?, maxSide: Int): Bitmap? {
    var bitmap by remember(url, maxSide) {
        mutableStateOf(url?.let { images.cached(it, maxSide) })
    }
    LaunchedEffect(url, maxSide) {
        if (url != null && bitmap == null) bitmap = images.load(url, maxSide)
    }
    return bitmap
}

/**
 * Растягивает элемент на [horizontal] за поля родителя с каждой стороны: строке,
 * стоящей в списке с полями, так достаётся вся ширина экрана.
 */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth.takeIf { constraints.hasBoundedWidth } ?: placeable.width, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}
