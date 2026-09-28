package ru.zaroslikov.incubator.design.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Снимает фокус с поля ввода по нажатию на свободное место — вместе с фокусом
 * уходит и клавиатура. Иначе набранное число остаётся под курсором, а половину
 * формы закрывает клавиатура, которую нечем убрать, кроме системной кнопки
 * «Назад».
 *
 * Жест ловится в основном проходе и только неперехваченный, поэтому нажатие на
 * само поле, кнопку или карточку сюда не доходит: касание по соседнему полю
 * переводит фокус в него, а не гасит его на полпути.
 *
 * Модификатор нужен каждому окну отдельно: шторки и диалоги живут в собственных
 * окнах, и `Scaffold` в [ru.zaroslikov.incubator.InventoryApp] до их касаний не
 * достаёт.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
