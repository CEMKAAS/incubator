package ru.zaroslikov.incubator.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import java.io.ByteArrayOutputStream

/**
 * Картинка QR-кода на печать: код с тихой зоной и подписью под ним, PNG.
 *
 * Посреди кода — логотип приложения на белой подложке, по той же геометрии
 * ([logoLayout]), что и на экране: код закрыт в одних и тех же модулях везде, где его
 * рисуют, и то, что читается с экрана, читается и с бумаги.
 *
 * Чёрным по белому и никак иначе — тема приложения к делу не относится: это не
 * элемент экрана, а то, что напечатают и наклеят на прибор, и читаться оно должно
 * любой камерой при любом свете. Подпись — название инкубатора: у хозяйства с
 * четырьмя приборами четыре одинаковых с виду кода, и без подписи их не разложить по
 * инкубаторам после печати. В сам код название не входит (см. [QrLink]).
 *
 * Тихая зона — четыре модуля, как требует стандарт: код, распечатанный впритык к
 * краю бумаги или к тексту, читается заметно хуже.
 */
object QrBitmap {
    /** Сколько пикселей на модуль: 33 модуля × 32 = 1056 px, хватает на любую печать. */
    private const val MODULE_PX = 32

    /** Тихая зона по стандарту QR — четыре модуля с каждой стороны. */
    private const val QUIET_ZONE_MODULES = 4

    private const val LABEL_TEXT_PX = 56f
    private const val LABEL_GAP_PX = 8

    fun render(modules: QrModules, label: String, logo: Bitmap?): Bitmap {
        val codePx = modules.size * MODULE_PX
        val quiet = QUIET_ZONE_MODULES * MODULE_PX
        val width = codePx + quiet * 2
        val text = label.trim()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = LABEL_TEXT_PX
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val labelHeight = if (text.isEmpty()) 0 else {
            val bounds = Rect()
            labelPaint.getTextBounds("Йр", 0, 2, bounds)
            bounds.height() + LABEL_GAP_PX
        }
        // Снизу — та же тихая зона, что и сверху, целиком, и подпись стоит **под** ней, а
        // не внутри: текст в тихой зоне съедает её у нижней угловой метки, и хотя ZXing
        // такое прощает, штатные камеры телефонов — не все. Подпись получает своё поле
        // той же высоты снизу, чтобы не упираться в край бумаги.
        val height = codePx + quiet * 2 + labelHeight + (if (text.isEmpty()) 0 else quiet)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val dark = Paint().apply { color = Color.BLACK }
        for (y in 0 until modules.size) {
            for (x in 0 until modules.size) {
                if (!modules[x, y]) continue
                val left = quiet + x * MODULE_PX
                val top = quiet + y * MODULE_PX
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + MODULE_PX).toFloat(),
                    (top + MODULE_PX).toFloat(),
                    dark,
                )
            }
        }
        if (logo != null) {
            drawLogo(canvas, logo, offset = quiet.toFloat(), codePx = codePx.toFloat())
        }
        if (text.isNotEmpty()) {
            // Название длиннее поля обрезается многоточием, а не уезжает за край:
            // на печати не бывает подсказки по нажатию.
            val fitted = TextUtils.ellipsize(
                text, android.text.TextPaint(labelPaint), (width - quiet).toFloat(),
                TextUtils.TruncateAt.END,
            ).toString()
            val baseline = codePx + quiet * 2 + LABEL_GAP_PX + LABEL_TEXT_PX
            canvas.drawText(fitted, width / 2f, baseline, labelPaint)
        }
        return bitmap
    }

    /**
     * Логотип по [logoLayout]: белая подложка со скруглением, значок по центру неё.
     * [offset] — где начинается код (после тихой зоны), [codePx] — его сторона.
     */
    private fun drawLogo(canvas: Canvas, logo: Bitmap, offset: Float, codePx: Float) {
        val layout = logoLayout()
        val padSide = codePx * layout.padSide
        val padStart = offset + codePx * layout.padStart
        val pad = RectF(padStart, padStart, padStart + padSide, padStart + padSide)
        canvas.drawRoundRect(pad, padSide * 0.18f, padSide * 0.18f, Paint().apply { color = Color.WHITE })
        val logoSide = codePx * layout.logoSide
        val logoStart = offset + codePx * layout.logoStart
        val dst = RectF(logoStart, logoStart, logoStart + logoSide, logoStart + logoSide)
        canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Сторона значка в пикселях картинки на печать — под [render] с его модулем в [MODULE_PX]. */
    fun logoSizePx(modules: QrModules): Int =
        (modules.size * MODULE_PX * logoLayout().logoSide).toInt()

    /** Те же модули и подпись, но сразу байтами PNG — для записи в файл. */
    fun png(modules: QrModules, label: String, logo: Bitmap?): ByteArray {
        val bitmap = render(modules, label, logo)
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
