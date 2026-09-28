package ru.zaroslikov.incubator.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сетка QR-кода — та, которую нарисуют на экране и на печати, — читается обратно в ту
 * же ссылку. Проверяется здесь, а не на устройстве: ZXing — чистая Java, и распознать
 * собственную картинку он может в JVM-тесте без камеры и эмулятора.
 */
class QrCodeTest {

    @Test
    fun `the grid is square, odd-sized and carries the finder patterns`() {
        val modules = qrModules(QrLink.encode(12L))
        // Версии 1–40 — сетки 21×21 … 177×177, всегда нечётные.
        assertTrue(modules.size >= 21)
        assertEquals(1, modules.size % 2)
        // Три угловые метки: тёмный угол в каждой из них.
        assertTrue(modules[0, 0])
        assertTrue(modules[modules.size - 1, 0])
        assertTrue(modules[0, modules.size - 1])
    }

    @Test
    fun `a short link stays a small code`() {
        // Ссылка с шестизначным номером — не выше версии 4 при уровне H, то есть не больше
        // 33 модулей: наклейка остаётся мелкой и печатается на чём угодно.
        val modules = qrModules(QrLink.encode(123_456L))
        assertTrue("сетка ${modules.size} модулей", modules.size <= 33)
    }

    @Test
    fun `the grid decodes back to the link it was made from`() {
        val link = QrLink.encode(4242L)
        val modules = qrModules(link)
        assertEquals(link, decode(modules))
        assertEquals(4242L, QrLink.parse(decode(modules)))
    }

    @Test
    fun `the grid still decodes with the logo pad blanked out`() {
        // Логотип на печати и на экране закрывает подложку по [logoLayout]; здесь она
        // выбеливается по той же геометрии, и код обязан прочитаться из того, что осталось.
        val link = QrLink.encode(123_456L)
        val modules = qrModules(link)
        assertEquals(link, decode(modules, blankLogo = true))
    }

    /** Растр из сетки с тихой зоной в четыре модуля, как на печати, и его распознавание. */
    private fun decode(modules: QrModules, blankLogo: Boolean = false): String {
        val scale = 4
        val quiet = 4 * scale
        val side = modules.size * scale + quiet * 2
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        val layout = logoLayout()
        val codePx = modules.size * scale
        val padFrom = quiet + (codePx * layout.padStart).toInt()
        val padTo = padFrom + (codePx * layout.padSide).toInt()
        for (y in 0 until modules.size) {
            for (x in 0 until modules.size) {
                if (!modules[x, y]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val px = quiet + x * scale + dx
                        val py = quiet + y * scale + dy
                        if (blankLogo && px in padFrom until padTo && py in padFrom until padTo) continue
                        pixels[py * side + px] = 0xFF000000.toInt()
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }
}
