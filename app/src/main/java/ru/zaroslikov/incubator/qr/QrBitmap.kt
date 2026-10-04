package ru.zaroslikov.incubator.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/**
 * Подробная подпись под кодом — режим «Подробно» шторки QR-кода: бренд с моделью и
 * примечание инкубатора. Пустые части не печатаются вовсе, как и на экране.
 */
data class QrDetails(val subtitle: String, val note: String)

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
 * В режиме «Подробно» ([QrDetails]) под названием идут бренд с моделью и примечание
 * инкубатора — абзацем, с переносами по словам: такой лист читают глазами, а не камерой,
 * и обрезанное примечание на бумаге уже не дочитать.
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

    /** Бренд и модель — мельче названия и серым, как строка под ним на экране. */
    private const val SUBTITLE_TEXT_PX = 40f

    /** Примечание — текст, который читают с листа глазами, а не камерой. */
    private const val NOTE_TEXT_PX = 40f

    /** Сколько строк примечания помещается на картинку; дальше — многоточие. */
    private const val NOTE_MAX_LINES = 12
    private const val NOTE_CAPTION_PX = 30f
    private const val BLOCK_GAP_PX = 24

    fun render(modules: QrModules, label: String, logo: Bitmap?, details: QrDetails? = null): Bitmap {
        val codePx = modules.size * MODULE_PX
        val quiet = QUIET_ZONE_MODULES * MODULE_PX
        val width = codePx + quiet * 2
        // Поле под текст — по полтихой зоны от каждого края: шире кода, но не впритык к бумаге.
        val textWidth = width - quiet
        val text = label.trim()
        val subtitle = details?.subtitle?.trim().orEmpty()
        val note = details?.note?.trim().orEmpty()

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = LABEL_TEXT_PX
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = SUBTITLE_TEXT_PX
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = NOTE_CAPTION_PX
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = NOTE_TEXT_PX
        }

        // Высота каждой части считается заранее: размер картинки зависит от того, что в неё
        // попадёт, и пустая часть не оставляет после себя ни строки, ни отступа.
        val labelHeight = if (text.isEmpty()) 0 else LABEL_GAP_PX + lineHeight(labelPaint)
        val subtitleHeight = if (subtitle.isEmpty()) 0 else LABEL_GAP_PX + lineHeight(subtitlePaint)
        // Примечание переносится по словам: его пишет человек, и бывает оно в абзац длиной,
        // а на бумаге, в отличие от экрана, нет подсказки по нажатию — значит, целиком.
        val noteLayout = if (note.isEmpty()) null else {
            StaticLayout.Builder.obtain(note, 0, note.length, notePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                // Целиком — но не бесконечно: примечание длиной в несколько тысяч знаков
                // дало бы картинку в тысячи пикселей высотой и десятки мегабайт памяти.
                .setMaxLines(NOTE_MAX_LINES)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
        }
        val noteHeight = if (noteLayout == null) 0 else
            BLOCK_GAP_PX + lineHeight(captionPaint) + LABEL_GAP_PX + noteLayout.height
        val textHeight = labelHeight + subtitleHeight + noteHeight

        // Снизу — та же тихая зона, что и сверху, целиком, и подпись стоит **под** ней, а
        // не внутри: текст в тихой зоне съедает её у нижней угловой метки, и хотя ZXing
        // такое прощает, штатные камеры телефонов — не все. Подпись получает своё поле
        // той же высоты снизу, чтобы не упираться в край бумаги.
        val height = codePx + quiet * 2 + textHeight + (if (textHeight == 0) 0 else quiet)
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
        var top = (codePx + quiet * 2).toFloat()
        if (text.isNotEmpty()) {
            // Название и модель длиннее поля обрезаются многоточием, а не уезжают за край:
            // это одна строка-ярлык, и переносить её незачем.
            top += LABEL_GAP_PX
            drawCentered(canvas, text, labelPaint, textWidth, width, top)
            top += lineHeight(labelPaint)
        }
        if (subtitle.isNotEmpty()) {
            top += LABEL_GAP_PX
            drawCentered(canvas, subtitle, subtitlePaint, textWidth, width, top)
            top += lineHeight(subtitlePaint)
        }
        if (noteLayout != null) {
            val left = (width - textWidth) / 2f
            top += BLOCK_GAP_PX
            canvas.drawText("ПРИМЕЧАНИЕ", left, top - captionPaint.fontMetrics.ascent, captionPaint)
            top += lineHeight(captionPaint) + LABEL_GAP_PX
            canvas.save()
            canvas.translate(left, top)
            noteLayout.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    /** Высота строки по метрикам шрифта — от верха выносных до низа подстрочных. */
    private fun lineHeight(paint: Paint): Int {
        val metrics = paint.fontMetrics
        return ceil(metrics.descent - metrics.ascent).toInt()
    }

    /** Одна строка по центру, с многоточием, если не влезла; [top] — верх строки. */
    private fun drawCentered(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        maxWidth: Int,
        width: Int,
        top: Float,
    ) {
        val fitted = TextUtils.ellipsize(text, paint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
        canvas.drawText(fitted.toString(), width / 2f, top - paint.fontMetrics.ascent, paint)
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
    fun png(modules: QrModules, label: String, logo: Bitmap?, details: QrDetails? = null): ByteArray {
        val bitmap = render(modules, label, logo, details)
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
