package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ряд плиток одной высоты — тех самых карточек, что стоят на экране по две-четыре в строке.
 *
 * Обычный `Row` меряет каждую плитку по её собственному содержимому, и стоит подписи одной
 * из них перенестись на вторую строку, как соседка рядом оказывается ниже: у одной карточки
 * дно на десять пикселей ниже другой, и ряд читается как съехавший. Подгонять это подписями
 * («ровно одна строка, иначе поедет») бесполезно — длина текста зависит от чисел, от
 * системного кегля и от ширины телефона, а не от того, что видно в макете.
 *
 * `IntrinsicSize.Min` заранее спрашивает у ряда, какая высота нужна самой высокой плитке, и
 * задаёт её всему ряду; [tileWeight] раздаёт эту высоту детям. Одно без другого не работает:
 * без `fillMaxHeight` плитка остаётся своего роста внутри высокого ряда, и снизу под ней
 * просто появляется пустота.
 *
 * Ряд — это единственное место, где такое выравнивание можно сделать: плитка о соседках
 * ничего не знает, и `heightIn(min = …)` у неё самой — это подпорка под одну известную
 * длину текста, а не ответ.
 */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/**
 * Доля ширины ряда — и вся его высота: то, чем плитка [TileRow] крепится к ряду.
 *
 * Отдельной функцией, а не двумя модификаторами на месте, потому что забыть тут можно
 * только `fillMaxHeight`, и забытый он не ломает ничего заметного — ряд просто перестаёт
 * выравниваться ровно в тех случаях, когда выравнивание и нужно.
 */
fun RowScope.tileWeight(weight: Float = 1f): Modifier =
    Modifier
        .weight(weight)
        .fillMaxHeight()
