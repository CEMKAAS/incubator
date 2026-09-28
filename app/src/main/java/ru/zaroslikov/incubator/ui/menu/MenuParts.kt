package ru.zaroslikov.incubator.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.design.components.StatusBarAppearance
import ru.zaroslikov.incubator.design.components.accentSwitchColors
import ru.zaroslikov.incubator.design.components.withoutTop
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Общие детали трёх экранов меню — «Аналитика», «Настройки», «О приложении».
 *
 * Все три построены одинаково: зелёная шапка с возвратом к списку инкубаторов, поверх
 * неё кремовая «шторка» со скруглёнными углами, внутри — белые карточки. Это ровно тот
 * же силуэт, что у экрана инкубатора, и повторён он намеренно: меню — не отдельный мир,
 * а те же данные под другим углом, и второй визуальный язык здесь сбивал бы с толку.
 *
 * Карточки берутся готовыми из `IncubatorTabParts.kt` (`TabCard`, `CardHeader`,
 * `MetricCard`): вторая их копия разошлась бы с первой на первой же правке скругления.
 * Здесь лежит только то, чего там нет, — шапка и строки списка.
 */

/** Отступ по краям, общий со всеми экранами приложения. */
internal val MenuScreenPadding = 20.dp

/** Насколько кремовая шторка наезжает на зелёную шапку — как на экране инкубатора. */
private val SheetOverlap = 17.dp

/** Скругление верхних углов шторки. */
private val SheetCorner = 24.dp

/**
 * Каркас экрана меню: шапка, кремовая шторка поверх неё, содержимое внутри шторки.
 *
 * [headerExtra] — то, что рисуется в шапке под заголовком. Сейчас им не пользуется
 * никто: «Аналитике» хватает подписи, как и остальным двум. Отдельным слотом, а не готовым рядом
 * показателей: шапка «Настроек» показывать ничего не должна, а пустой ряд оставил бы
 * под заголовком дыру ровно в его высоту.
 *
 * [scrollable] выключается ровно для «Аналитики». Её вкладки листаются пейджером, страница
 * внутри прокручивается сама, и внешний `verticalScroll` той же оси уронил бы измерение
 * — а заодно и `weight(1f)`, которым пейджер занимает остаток высоты. Выключенная
 * прокрутка снимает и общие отступы: их в этом случае ставит каждая страница у себя,
 * иначе «таблетка» переключателя вкладок оказалась бы внутри полей содержимого.
 */
@Composable
internal fun MenuScreen(
    title: String,
    subtitle: String?,
    navigateBack: () -> Unit,
    contentPadding: PaddingValues,
    scrollable: Boolean = true,
    headerExtra: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Полоса статус-бара — под цвет зелёной шапки, как и на экране инкубатора,
    // с тем же разделением труда: сама шапка на Android 15+, окно — на старых.
    StatusBarAppearance(color = DesignPalette.HeaderSurface, lightIcons = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding.withoutTop())
    ) {
        MenuHeader(
            title = title,
            subtitle = subtitle,
            navigateBack = navigateBack,
            extra = headerExtra,
            headerInset = contentPadding.calculateTopPadding(),
        )

        val sheet = Modifier
            .fillMaxWidth()
            .weight(1f)
            .offset(y = -SheetOverlap)
            .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
            .background(MaterialTheme.colorScheme.background)

        if (scrollable) {
            Column(
                modifier = sheet
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MenuScreenPadding)
                    .padding(top = 24.dp, bottom = 32.dp),
                content = content,
            )
        } else {
            Column(modifier = sheet, content = content)
        }
    }
}

@Composable
private fun MenuHeader(
    title: String,
    subtitle: String?,
    navigateBack: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit,
    /** Высота статус-бара: шапка дотягивается под него, чтобы полоса была её цвета. */
    headerInset: Dp = 0.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignPalette.HeaderSurface)
            .padding(
                start = MenuScreenPadding,
                end = MenuScreenPadding,
                top = 8.dp + headerInset,
                bottom = 32.dp,
            )
    ) {
        Row(
            modifier = Modifier.clickable(onClick = navigateBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left_design),
                contentDescription = null,
                tint = DesignPalette.HeaderIcon,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Все инкубаторы",
                style = DesignType.FieldValue.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = DesignPalette.HeaderIcon,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = DesignType.HeaderTitle,
            color = DesignPalette.HeaderTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = DesignType.Caption,
                color = DesignPalette.HeaderMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        extra()
    }
}

/**
 * Строка списка внутри карточки: заголовок, пояснение под ним и что-нибудь справа.
 *
 * [onClick] необязателен: строка с выключателем никуда не ведёт, и делать её нажимаемой
 * значило бы завести второй способ переключить то же самое — с той разницей, что по
 * строке промахиваются мимо выключателя чаще, чем попадают.
 */
@Composable
internal fun MenuRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = DesignType.ListItemTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.size(12.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Разделитель между строками одной карточки — та же линия, что и в «Финансах». */
@Composable
internal fun MenuRowDivider() {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
    Spacer(Modifier.height(14.dp))
}

/** Выключатель в фирменном зелёном — те же цвета, что и в форме инкубатора. */
@Composable
internal fun MenuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = accentSwitchColors(),
    )
}

/**
 * Круглый значок слева от строки — им размечены пункты «О приложении».
 *
 * Заливка светлая, значок зелёный: строки там не нажимаются и подсвечивать их акцентом
 * целиком было бы обещанием действия, которого нет.
 */
@Composable
internal fun MenuIcon(@DrawableRes icon: Int, tint: Color = DesignPalette.Accent) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DesignPalette.IncomeSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Строка «ключ — значение» в карточке «О приложении».
 *
 * Значение забирает всю оставшуюся ширину (`weight`) и выравнивается по правому краю,
 * а не отжимается туда одним лишь `SpaceBetween`. Разница видна на длинном значении:
 * «Заросликов Семён Николаевич» в строку не влезает, переносится — и без `TextAlign.End`
 * ложится по левому краю своего блока, то есть ни одна его строка до правого края
 * карточки не доходит, и весь ряд читается как съехавший.
 */
@Composable
internal fun MenuFactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = DesignType.MonoSmallEmphasis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
