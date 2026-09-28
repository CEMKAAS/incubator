package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Ширина подсказки с полным текстом.
 *
 * Material даёт `PlainTooltip` 200 dp — размер ярлыка в два слова у иконки. Здесь в
 * подсказке лежит либо фраза, которую человек написал сам, либо длинная сумма, и на
 * 200 dp она разбивается на десяток строк по три слова, хотя экран и на 360 dp шире.
 * 280 dp оставляет поля с обеих сторон на самом узком телефоне и читается как абзац, а
 * не как столбик.
 */
private val TooltipWidth = 280.dp

/**
 * Текст, обрезанный по [maxLines] и показывающий себя целиком во всплывающей подсказке.
 *
 * Два разных повода привели сюда одно и то же решение, и поэтому композабл общий.
 *
 * Название инкубатора и строка «бренд · модель» — это то, что человек написал сам, и
 * длина у них любая: «Инкубатор в летней кухне у бабушки» уезжал на пять строк и
 * отодвигал вниз всё, ради чего карточку и смотрят — виды, занятость, ближайший вывод.
 * Две строки — потолок, дальше многоточие.
 *
 * Число в плитке — второй повод, и там потолок в **одну** строку. Плитки стоят по две в
 * ряд и держат одну высоту на ряд ([TileRow]): перенос «1 234 567 ₽» на вторую строку
 * поднимал бы подпись и знаменатель у обеих плиток ряда, то есть одно крупное хозяйство
 * перекашивало бы всю страницу. Одна строка — это ровный ряд плиток при любых суммах, а
 * цена — многоточие, которое договаривает подсказка.
 *
 * **Обрезанный текст нажимается, и полный показывает подсказка** ([PlainTooltip]),
 * потому что обрезано как раз то, чем два инкубатора и различаются: у «Инкубатора на
 * веранде (левый)» и «…(правый)» совпадают ровно первые две строки, — а у суммы
 * обрезаны самые мелкие разряды, то есть точность. Подсказка, а не раскрытие на месте:
 * карточка от неё не меняет высоты, соседние не разъезжаются, и ответ не нужно убирать
 * обратно — он гаснет сам по нажатию мимо. `isPersistent = true` ровно за этим:
 * полторы секунды, после которых обычная подсказка исчезает, — это про ярлык у иконки,
 * а не про длинное название, которое читают.
 *
 * Нажатие ставится **только на переполненный текст** ([hasVisualOverflow]) — на карточке
 * инкубатора висит свой `onClick`, открывающий её, и дочернее нажатие его перехватывает;
 * забирать тап у коротких названий, которые и так видны целиком, было бы отнятой
 * половиной карточки. Ряби у нажатия нет: это не кнопка внутри карточки, а сам текст,
 * который договаривает.
 *
 * **`enableUserInput = false`, и это не отключение, а замена.** Своя обработка жестов у
 * [TooltipBox] сделана под ярлык у иконки: подсказка появляется по долгому нажатию и
 * гаснет, как только палец отпустили. На названии, которое читают, это и выглядело
 * поломкой — задержал палец, текст мелькнул и исчез, — тем более что быстрый тап рядом
 * показывал ту же подсказку насовсем. Жест здесь один, обычное нажатие, и ведёт он себя
 * одинаково при любой длине.
 *
 * **Нажатие переключает: показать — убрать.** И вот почему это не `onClick { show() }`,
 * а разбор жеста вручную. Попап не перехватывает касания вне себя — второй тап по тому
 * же тексту приходит сразу в два окна: подсказка получает `ACTION_OUTSIDE` и закрывается
 * сама, а до текста доходит обычное нажатие. Кто из них успеет первым, не определено, и
 * `onClick`, спрашивающий «видно ли сейчас», отвечал бы себе то «да», то «нет» — то есть
 * переключатель срабатывал бы через раз. Поэтому видимость снимается в момент **нажатия**
 * ([wasVisible]), когда подсказка заведомо ещё на экране: закрыться до касания, которое
 * её закрывает, она не может. Дальше ответ уже не зависит от гонки — было видно, значит
 * убираем; не было — показываем. `up.consume()` на `Initial`-проходе нужен, чтобы тот же
 * тап не открыл заодно инкубатор: карточка разбирает жест на `Main`, после нас.
 *
 * **Цвета — из палитры приложения, а не из материаловской пары `inverseSurface`.** Та по
 * замыслу инвертирует тему: на светлой подсказка тёмная, а на тёмной — светлая, то есть
 * ровно там, где всё остальное потемнело, выскакивает белый прямоугольник. Здесь это та
 * же поверхность и та же рамка, что у карточки под ней, поэтому подсказка следует теме,
 * а не спорит с ней, а отделяет её от карточки тень.
 *
 * **`Modifier.weight()` сюда передавать нельзя — он потеряется молча.** [TooltipBox]
 * рисует `Box { WrappedAnchor(modifier = modifier …) }`: внешний `Box` модификатора не
 * получает, так что `weight` достаётся вложенному узлу, где `RowScope.weight` не значит
 * ничего, и строка забирает себе всю ширину, какую просит её текст, — то есть ровно то
 * поведение, ради отмены которого её сюда и поставили. В ряду это лечится обёрткой:
 * `Box(Modifier.weight(1f)) { TruncatedText(…) }`. Та же грабля описана у заголовка
 * таблицы расписания, где на ней уже стояли.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruncatedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    // Выравнивание нужно там, где текст стоит по центру плитки. Заданное, оно растягивает
    // сам текст на всю ширину: [modifier] достаётся обёртке [TooltipBox], а `Text` внутри
    // неё без этого остаётся шириной в свои буквы и стоит у левого края — и никакой
    // `textAlign` тогда не виден, потому что выравнивать не в чем.
    textAlign: TextAlign? = null,
) {
    // Ключ по тексту: карточки в ленивом списке переиспользуются, и без него ответ
    // «не поместилось» от одного инкубатора достался бы другому при прокрутке.
    var overflowed by remember(text) { mutableStateOf(false) }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                maxWidth = TooltipWidth,
                shape = shape,
                containerColor = DesignPalette.Surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.border(0.8.dp, DesignPalette.CardBorder, shape),
            ) {
                Text(text = text, style = DesignType.Body)
            }
        },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        val tap = if (overflowed) {
            Modifier
                .semantics {
                    onClick(label = "Показать полностью") {
                        scope.launch { tooltipState.show() }
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val wasVisible = tooltipState.isVisible
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            ?: return@awaitEachGesture
                        up.consume()
                        scope.launch {
                            if (wasVisible) tooltipState.dismiss() else tooltipState.show()
                        }
                    }
                }
        } else {
            Modifier
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            onTextLayout = { overflowed = it.hasVisualOverflow },
            modifier = (if (textAlign != null) Modifier.fillMaxWidth() else Modifier).then(tap),
        )
    }
}
