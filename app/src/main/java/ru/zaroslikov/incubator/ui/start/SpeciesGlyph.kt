package ru.zaroslikov.incubator.ui.start

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import ru.zaroslikov.incubator.R

/**
 * Своя картинка вида — там, где подходящего эмодзи нет. Перепела в Юникоде нет, а «🐦»
 * на Android — синяя птичка, и закладка перепелов читалась как закладка неизвестно
 * кого. У остальных видов `null`: их эмодзи похожи на них самих.
 */
@DrawableRes
private fun speciesDrawable(bird: String): Int? = when (bird) {
    "Перепела" -> R.drawable.ic_species_quail
    else -> null
}

/** Сторона картинки в кеглях: столько же занимает на экране цветной эмодзи. */
private const val GlyphEm = 1.2f

private const val GlyphInlineId = "species"

/**
 * Значок вида — эмодзи или своя картинка того же размера ([speciesDrawable]).
 *
 * Размер задаётся кеглем, как у текста, чтобы вызывающему было всё равно, чем вид
 * нарисован: картинка растёт вместе с системным шрифтом, как рос бы эмодзи. [lineHeight]
 * — для мест, где высота строки держит раскладку (плитка вида в форме закладки).
 */
@Composable
fun SpeciesGlyph(
    bird: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val drawable = speciesDrawable(bird)
    if (drawable == null) {
        Text(
            text = speciesEmoji(bird),
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier,
        )
        return
    }
    val density = LocalDensity.current
    val side = with(density) { (fontSize * GlyphEm).toDp() }
    val line = if (lineHeight.isSpecified) with(density) { lineHeight.toDp() } else side
    Box(modifier = modifier.height(line), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(side),
        )
    }
}

/**
 * «Значок вида + текст» одной строкой — там, где они переносятся и обрезаются вместе.
 * Картинка встаёт в текст как его часть, так что `maxLines` и многоточие работают так
 * же, как с эмодзи.
 */
@Composable
fun SpeciesText(
    bird: String,
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val drawable = speciesDrawable(bird)
    if (drawable == null) {
        Text(
            text = "${speciesEmoji(bird)} $text",
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
        return
    }
    val inline = remember(drawable) {
        mapOf(
            GlyphInlineId to InlineTextContent(
                Placeholder(GlyphEm.em, GlyphEm.em, PlaceholderVerticalAlign.TextCenter),
            ) {
                Image(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
    Text(
        text = buildAnnotatedString {
            appendInlineContent(GlyphInlineId, speciesEmoji(bird))
            append(" ")
            append(text)
        },
        inlineContent = inline,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}
