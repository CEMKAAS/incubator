package ru.zaroslikov.incubator.qr

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Логотип посреди QR-кода — иконка приложения, та же, что на рабочем столе.
 *
 * Берётся у системы через `getApplicationIcon`, а не через `painterResource`
 * (`mipmap/ic_launcher` при `minSdk = 26` — это `<adaptive-icon>`, и Compose на нём
 * падает; подробности у `AppIcon` в «О приложении»). Система же складывает фон с
 * передним планом и обрезает по маске прошивки — на наклейке будет ровно тот значок,
 * который человек знает по рабочему столу, и по нему код узнаётся как код этого
 * приложения раньше, чем его отсканируют.
 *
 * [sizePx] — сторона растра: 192 для экрана (xxxhdpi-размер лаунчер-иконки), для
 * печати — сторона значка в пикселях картинки, чтобы не масштабировать вверх.
 */
fun appIconBitmap(context: Context, sizePx: Int): Bitmap =
    context.packageManager.getApplicationIcon(context.packageName)
        .toBitmap(width = sizePx, height = sizePx)
