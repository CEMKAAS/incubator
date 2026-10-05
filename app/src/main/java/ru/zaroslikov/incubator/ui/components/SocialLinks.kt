package ru.zaroslikov.incubator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.menu.TELEGRAM_LABEL
import ru.zaroslikov.incubator.ui.menu.TELEGRAM_URL
import ru.zaroslikov.incubator.ui.menu.VK_GROUP_LABEL
import ru.zaroslikov.incubator.ui.menu.VK_GROUP_URL

/**
 * Соцсети, в которые приложение зовёт: группа ВКонтакте и канал в Telegram.
 *
 * Адреса, значки и события аналитики — те же, что у строк «Контакты» в «О приложении»,
 * чтобы переход из приветствия или из инструкции попадал в ту же серию.
 */
internal enum class Social(
    val title: String,
    val shortName: String,
    val label: String,
    val url: String,
    val event: String,
    @DrawableRes val icon: Int,
) {
    VkGroup(
        title = "Группа ВКонтакте",
        shortName = "ВКонтакте",
        label = VK_GROUP_LABEL,
        url = VK_GROUP_URL,
        event = Events.OPEN_VK_GROUP,
        icon = R.drawable.baseline_cottage_24,
    ),
    Telegram(
        title = "Канал в Telegram",
        shortName = "Telegram",
        label = TELEGRAM_LABEL,
        url = TELEGRAM_URL,
        event = Events.OPEN_TELEGRAM,
        icon = R.drawable.baseline_send_24,
    ),
}

/** Отчитаться и открыть ссылку; браузера может не оказаться — тогда просто ничего. */
internal fun UriHandler.openSocial(social: Social) {
    Analytics.report(social.event)
    runCatching { openUri(social.url) }
}

/**
 * «Присоединяйтесь к нашим соцсетям: ВКонтакте · Telegram» — последняя строка
 * приветствия на пустом стартовом экране.
 */
@Composable
internal fun SocialLinks(
    modifier: Modifier = Modifier,
    style: TextStyle = DesignType.Body,
    textAlign: TextAlign = TextAlign.Center,
) {
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = DesignPalette.Accent, fontWeight = FontWeight.SemiBold),
    )
    Text(
        text = buildAnnotatedString {
            append("Присоединяйтесь к нашим соцсетям:\n")
            Social.entries.forEachIndexed { index, social ->
                if (index > 0) append("  ·  ")
                val link = LinkAnnotation.Url(
                    url = social.url,
                    styles = linkStyle,
                    linkInteractionListener = { uriHandler.openSocial(social) },
                )
                withLink(link) { append(social.shortName) }
            }
        },
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}
