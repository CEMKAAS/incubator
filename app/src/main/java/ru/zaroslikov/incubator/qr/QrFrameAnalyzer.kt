package ru.zaroslikov.incubator.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Ищет QR-код в кадрах камеры и отдаёт его текст.
 *
 * Работает на потоке анализатора CameraX, не на главном: распознавание — это проход
 * по мегапикселю яркостей, и на главном потоке оно съедало бы кадры превью. Поэтому
 * [onDecoded] приходит с чужого потока, и тот, кто его принимает, сам переносит ответ
 * куда надо (экран — в `rememberCoroutineScope`).
 *
 * Кадр берётся только по яркости — плоскость Y формата YUV_420_888: QR-код чёрно-белый,
 * и цвет ему не нужен, а одна плоскость вместо трёх — это треть работы. `rowStride`
 * учитывается отдельно: у многих камер строка в буфере шире картинки, и без него
 * картинка читалась бы со сдвигом на каждую строку, то есть не читалась бы вовсе.
 * Поворот кадра не разбирается намеренно: QR-код читается в любой ориентации по
 * трём своим угловым меткам.
 *
 * Читатель настроен на один формат — QR: штрихкоды и прочее здесь никому не нужны, а
 * каждый лишний формат — лишний проход по кадру. `TRY_HARDER` включён: наклейка
 * бывает мятой и тусклой, а лишние миллисекунды на кадр незаметны.
 *
 * Кадр закрывается в `finally`, что бы ни случилось: незакрытый кадр останавливает
 * поток анализа насовсем — CameraX больше не отдаст ни одного.
 */
class QrFrameAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            decode(image)?.let(onDecoded)
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val source = PlanarYUVLuminanceSource(
            bytes,
            plane.rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (e: NotFoundException) {
            // Кода в кадре нет — обычный ответ для большинства кадров.
            null
        } catch (e: Exception) {
            // Повреждённый или не дочитанный код: следующий кадр может быть лучше.
            null
        } finally {
            reader.reset()
        }
    }
}
