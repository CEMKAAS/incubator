package ru.zaroslikov.incubator.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Аватар профиля: откуда бы ни пришло фото — из VK или из галереи, — в базу ложится
 * квадратный JPEG не больше [AVATAR_SIZE] точек.
 *
 * Квадрат и потолок — потому что фото хранится байтами в строке `User` и едет вместе с
 * базой: снимок камеры на двенадцать мегапикселей раздул бы файл экспорта в десятки раз,
 * а показывается он всё равно кружком в 72 dp. Обрезка по центру, а не вписывание: в
 * кружке вписанный прямоугольник оставил бы пустые поля сверху и снизу.
 *
 * Картинка читается дважды — сначала только размеры, потом уменьшенная копия
 * (`inSampleSize`), — и оба раза из источника, а не из памяти: снимок современной камеры
 * весит десять-пятнадцать мегабайт, и держать его целиком ради картинки в 256 точек
 * незачем. Размеры же проверяются до декодирования: пять мегабайт сжатого PNG могут
 * оказаться сотнями мегабайт пикселей.
 */
object AvatarImage {

    /** Сторона готового аватара — с запасом на 72 dp при плотности xxxhdpi (288 px). */
    const val AVATAR_SIZE = 256

    /** Потолок скачиваемого файла: фото VK весит десятки килобайт, больше — не фото. */
    private const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024

    /** Больше — отказ до декодирования: так выглядит «бомба», а не фотография. */
    private const val MAX_SOURCE_PIXELS = 200_000_000L

    /** Полоса шире 8:1 в кружке — это не портрет, а декодировать её дорого. */
    private const val MAX_ASPECT = 8

    private const val TIMEOUT_MS = 10_000

    /**
     * Скачивает фото VK по адресу из профиля и готовит аватар. `null` — не вышло: сети
     * нет, адрес протух, адрес не https. Вход от этого не ломается — профиль просто
     * остаётся без фото.
     */
    suspend fun fromUrl(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = URL(url)
            // Адрес приходит из ответа VK, но проверить схему ничего не стоит: фото
            // профиля по открытому http — это и подмена по дороге, и утечка того, что
            // человек только что вошёл через VK.
            if (parsed.protocol != "https") return@runCatching null
            val connection = parsed.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode !in 200..299) return@runCatching null
                val bytes = connection.inputStream.use { it.readCapped() } ?: return@runCatching null
                normalize { ByteArrayInputStream(bytes) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Фото из системного выбора. `null` — файл не открылся или это не картинка. */
    suspend fun fromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            normalize { context.contentResolver.openInputStream(uri) }
        }.getOrNull()
    }

    /**
     * Приводит картинку к аватару: поворот по EXIF, квадрат по центру, [AVATAR_SIZE].
     *
     * [open] вызывается до трёх раз — размеры, пиксели, EXIF, — каждый раз за новым
     * потоком: `BitmapFactory` и `ExifInterface` читают поток до конца.
     */
    fun normalize(open: () -> InputStream?): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Не `open()?.use { … } ?: return null`: в режиме inJustDecodeBounds decodeStream
        // всегда возвращает null — ответ лежит в `bounds`, — и такой элвис отвергал бы
        // любую картинку. Отказом считается только поток, который не открылся.
        val boundsStream = open() ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height > MAX_SOURCE_PIXELS) return null
        if (maxOf(width, height) > MAX_ASPECT * minOf(width, height)) return null

        // По короткой стороне: после обрезки в квадрат останется именно она, и её должно
        // хватить на AVATAR_SIZE. Длинная ограничена MAX_ASPECT, так что копия в памяти —
        // не больше 2·256 × 16·256 точек.
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= AVATAR_SIZE) sample *= 2
        val decoded = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotated = rotateByExif(decoded, open)
        val side = minOf(rotated.width, rotated.height)
        val square = Bitmap.createBitmap(
            rotated,
            (rotated.width - side) / 2,
            (rotated.height - side) / 2,
            side,
            side,
        )
        val scaled = if (side > AVATAR_SIZE) {
            Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        } else {
            square
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    /** Снимок камеры хранит поворот в EXIF, а не в пикселях — без этого аватар лежит на боку. */
    private fun rotateByExif(bitmap: Bitmap, open: () -> InputStream?): Bitmap {
        // Платформенный ExifInterface, а не androidx: читать из потока он умеет с API 24,
        // minSdk выше, и ради четырёх строк не нужна ещё одна библиотека.
        val degrees = runCatching {
            open()?.use { stream ->
                when (
                    ExifInterface(stream)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun InputStream.readCapped(): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_DOWNLOAD_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
