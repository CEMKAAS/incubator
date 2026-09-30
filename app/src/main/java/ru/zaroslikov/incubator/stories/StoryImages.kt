package ru.zaroslikov.incubator.stories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Картинки историй: скачать, уменьшить до нужного, держать под рукой.
 *
 * Своя маленькая загрузка, а не Coil: в приложении это единственные картинки из сети, и
 * тянуть ради двух десятков кружков и слайдов библиотеку с собственным конвейером, как уже
 * решено для кнопки VK ID, незачем. Всё, что от неё здесь нужно, — три вещи.
 *
 * **Дисковый кэш — HTTP-кэш OkHttp** в `cacheDir/stories`: лента открывается при каждом
 * запуске, и одна и та же картинка не должна каждый раз ехать по сети заново. Ответы
 * помечаются годными на неделю сетевым перехватчиком, потому что сервер загрузок заголовков
 * кэша не ставит, а без них OkHttp не хранит ничего: файл истории по своему адресу не
 * меняется — новая картинка в админке это новый адрес.
 *
 * **Уменьшение при декодировании** (`inSampleSize`) до [maxSide]: превью в кружке 64 dp не
 * нужны 1080×1920 точек, а слайду — больше, чем экран. Размеры читаются до пикселей.
 *
 * **Память — `LruCache` на восьмую часть кучи**, ключом адрес и размер: листая истории
 * назад, человек не должен ждать то, что только что видел.
 *
 * Ни один вызов не бросает: сбой — это `null`, и просмотрщик показывает «не загрузилось»,
 * а не падает.
 */
class StoryImages(context: Context) {

    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, CACHE_DIR), DISK_CACHE_BYTES))
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "public, max-age=$CACHE_SECONDS")
                .build()
        }
        .build()

    private val memory = object : LruCache<String, Bitmap>(memoryKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Уже декодированная картинка, если она в памяти, — чтобы не мигать загрузкой. */
    fun cached(url: String, maxSide: Int): Bitmap? = memory.get(key(url, maxSide))

    /** Картинка по [url], уменьшенная так, чтобы большая сторона была не больше [maxSide]. */
    suspend fun load(url: String, maxSide: Int): Bitmap? {
        cached(url, maxSide)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = download(url) ?: return@withContext null
                decode(bytes, maxSide)?.also { memory.put(key(url, maxSide), it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }

    private fun download(url: String): ByteArray? {
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) return null
            val bytes = body.bytes()
            return bytes.takeIf { it.size <= MAX_DOWNLOAD_BYTES }
        }
    }

    private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun key(url: String, maxSide: Int) = "$maxSide|$url"

    private companion object {
        const val CACHE_DIR = "stories"
        const val DISK_CACHE_BYTES = 50L * 1024 * 1024
        const val CACHE_SECONDS = 7 * 24 * 60 * 60
        const val TIMEOUT_SECONDS = 15L
        const val CALL_TIMEOUT_SECONDS = 30L

        /** Слайд — картинка на экран; больше — это не картинка, а ошибка в админке. */
        const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024

        /** Больше — отказ до декодирования, как у аватара (`AvatarImage`). */
        const val MAX_SOURCE_PIXELS = 100_000_000L

        fun memoryKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}
