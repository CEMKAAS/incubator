package ru.zaroslikov.incubator.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Модули QR-кода — квадратная сетка «тёмный / светлый», без тихой зоны.
 *
 * Только сетка, без картинки: рисуют её два разных места — `Canvas` в Compose на
 * экране и `android.graphics.Canvas` в файл на печать, — и обоим нужна не растровая
 * картинка одного размера, а ответ «тёмен ли модуль (x, y)», который каждый
 * масштабирует под себя. Заодно это то, что можно проверить в JVM-тесте: ZXing — чистая
 * Java, и [QrCodeTest] кодирует ссылку и распознаёт её обратно без эмулятора.
 */
class QrModules(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) { "модулей ${dark.size}, а сетка $size×$size" }
    }

    /** Тёмен ли модуль в столбце [x] и строке [y]. */
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * Строит QR-код с текстом [text].
 *
 * Уровень коррекции — H, наивысший (восстанавливает до трети площади), и это не запас
 * ради запаса: посреди кода стоит логотип приложения ([logoLayout]), закрывающий около
 * пяти процентов модулей, а наклейка на инкубаторе живёт в птичнике — её заливают,
 * царапают и заклеивают углом скотча. Из оставшихся после логотипа процентов и
 * читается всё это. Цена — на версию больше: ссылка в 25–30 знаков при H укладывается в
 * 33×33 модуля, что по-прежнему печатается на любом принтере и читается с руки. Тихую
 * зону сетка не включает (`MARGIN 0`) — её добавляет тот, кто рисует, под свой размер.
 */
fun qrModules(text: String): QrModules {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    // Нулевые размеры — «естественная величина»: один пиксель на модуль, сетка без
    // масштабирования и без полей, ровно то, что нужно перерисовать своими руками.
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    val dark = BooleanArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            dark[y * size + x] = matrix[x, y]
        }
    }
    return QrModules(size, dark)
}

/**
 * Где посреди кода стоит логотип: белая подложка и сам значок внутри неё, в долях
 * стороны кода (без тихой зоны), от 0 до 1.
 *
 * Одна геометрия на все три места, где код рисуют — экран, печать и тест, — иначе
 * логотип на экране закрывал бы одни модули, а на печати другие, и распознавание
 * проверялось бы не того кода, который наклеят. Подложка нужна: значок, положенный
 * прямо на модули, читается вместе с ними, и его края превращаются в ложные модули по
 * контуру; белое поле вокруг отделяет одно от другого так, как тихая зона отделяет код
 * от бумаги.
 *
 * Подложка — 26 % стороны, то есть около 7 % площади; уровень H восстанавливает до
 * 30 %, и на царапины и грязь остаётся больше, чем закрыл логотип. Больше нельзя без
 * ущерба для чтения, меньше — значок в 4 мм на наклейке в 3 см не узнать.
 */
data class QrLogoLayout(
    /** Сторона белой подложки, доля стороны кода. */
    val padSide: Float,
    /** Сторона значка внутри подложки, доля стороны кода. */
    val logoSide: Float,
) {
    /** Левый верхний угол подложки — она стоит по центру. */
    val padStart: Float get() = (1f - padSide) / 2f

    /** Левый верхний угол значка. */
    val logoStart: Float get() = (1f - logoSide) / 2f
}

/** Геометрия логотипа: см. [QrLogoLayout]. */
fun logoLayout(): QrLogoLayout = QrLogoLayout(padSide = 0.26f, logoSide = 0.20f)
