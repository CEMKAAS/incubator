package ru.zaroslikov.incubator.design.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Цвет полосы статус-бара и светлые ли на ней значки. */
@Immutable
data class StatusBarStyle(val color: Color, val lightIcons: Boolean)

/**
 * Полоса «по умолчанию» для текущей темы — фон экрана с тёмными значками на светлой
 * теме и светлыми на тёмной. Ставит `IncubatorTheme`; экран с цветной шапкой красит
 * полосу под себя, а уходя, возвращает вот это.
 */
val LocalStatusBarDefault = compositionLocalOf { StatusBarStyle(Color.Unspecified, false) }

/**
 * Полоса статус-бара под цвет шапки экрана: [color] — её фон, [lightIcons] — белые часы
 * и значки поверх него.
 *
 * Красить приходится с двух сторон, потому что систем-бар достаётся приложению
 * по-разному в зависимости от версии. До Android 15 окно не пускает содержимое под
 * полосу, и единственный рычаг — `window.statusBarColor`. С `targetSdk` 37 на Android 15
 * и новее edge-to-edge включён принудительно, это поле игнорируется, зато шапка сама
 * дотягивается до верхнего края экрана — её фон и есть фон полосы (см. верхний отступ
 * шапок из `contentPadding`). Один вызов закрывает оба случая: на старых устройствах
 * работает первая половина, на новых — вторая, и лишняя ничему не мешает.
 *
 * Именно `DisposableEffect`, а не `SideEffect`: экран, покрасивший полосу в зелёный,
 * обязан вернуть её как было, когда с него ушли. Вложенный вызов побеждает внешний —
 * эффекты применяются в порядке композиции, от родителя к ребёнку.
 *
 * **Возвращает он не «как было», а [LocalStatusBarDefault] — то, что тема считает
 * полосой по умолчанию сейчас.** Первая версия запоминала прежние значения окна и
 * ставила их обратно, и это ломалось ровно в одном месте — при смене темы на экране с
 * зелёной шапкой: эффекты темы и экрана перезапускались в одной рекомпозиции, каждый
 * «запоминал» то, что успел поставить другой, и после ухода с экрана полоса получала
 * значки светлой темы поверх тёмного фона. Дефолт из композиции от порядка не зависит:
 * уходящий экран ставит то, что тема просит *сейчас*, а не то, что видел при входе.
 * Сам дефолт стоит среди ключей эффекта, чтобы при смене темы экран перекрасил полосу
 * заново уже после того, как тема поставила своё.
 */
@Composable
fun StatusBarAppearance(color: Color, lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val fallback = LocalStatusBarDefault.current
    DisposableEffect(view, color, lightIcons, fallback) {
        applyStatusBar(view, color, lightIcons)
        onDispose {
            if (fallback.color.isSpecified) applyStatusBar(view, fallback.color, fallback.lightIcons)
        }
    }
}

/**
 * Полоса по умолчанию — вызывается темой. Ничего не восстанавливает, потому что
 * восстанавливать не к чему: ниже темы окна нет. Тема же и кладёт эти значения в
 * [LocalStatusBarDefault], к которым возвращаются экраны.
 */
@Composable
fun DefaultStatusBar(style: StatusBarStyle) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, style) {
        applyStatusBar(view, style.color, style.lightIcons)
        onDispose { }
    }
}

private fun applyStatusBar(view: View, color: Color, lightIcons: Boolean) {
    val window = view.context.findActivity().window
    val controller = WindowCompat.getInsetsController(window, view)
    @Suppress("DEPRECATION")
    window.statusBarColor = color.toArgb()
    // Светлый фон требует ТЁМНЫХ значков, и наоборот, — отсюда отрицание.
    controller.isAppearanceLightStatusBars = !lightIcons
}

/**
 * Те же системные отступы, но без верхнего.
 *
 * Экран с цветной шапкой отдаёт верх ей самой: полосу должен закрывать её фон, а не
 * пустое поле над ним, — поэтому высота статус-бара уезжает во внутренний отступ шапки,
 * а наружу остаются только края и низ.
 */
@Composable
fun PaddingValues.withoutTop(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

/**
 * Активность за контекстом вью. Прямой каст `context as Activity` держится, пока тема
 * стоит в самой активности; контекст из диалога или шторки — обёртка, и каст упал бы.
 * Разворачиваем обёртки до первой активности; если её нет вовсе, это ошибка вызова.
 */
private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("StatusBarAppearance вызван вне активности")
}

/**
 * Полоса статус-бара для окна диалога на весь экран.
 *
 * [StatusBarAppearance] красит окно активности, а диалог, раскрытый на весь экран с
 * `decorFitsSystemWindows = false`, лежит под полосой своим окном — и значки на ней
 * берутся из его флагов, а не из флагов активности. Без этого диалог, открытый с экрана
 * с зелёной шапкой, получал белые часы на кремовом фоне. Ставится по [LocalStatusBarDefault]:
 * фон диалога — фон темы, значки — какие тема считает для него правильными. Ничего не
 * восстанавливает: окно диалога умирает вместе с ним.
 */
@Composable
fun DialogStatusBar() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val style = LocalStatusBarDefault.current
    DisposableEffect(view, style) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !style.lightIcons
            // И полоса навигации: с трёхкнопочной навигацией окно тоже лежит под ней.
            controller.isAppearanceLightNavigationBars = !style.lightIcons
        }
        onDispose { }
    }
}
