package ru.zaroslikov.incubator.stories

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * История с сервера — то, что главный экран показывает кружком, а просмотрщик листает
 * по слайдам. [viewed] — видел ли её этот человек (или эта установка, если он не вошёл):
 * ответ сервера, дополненный теми просмотрами, о которых сервер ещё не узнал
 * ([withLocalViews]).
 */
data class Story(
    val id: String,
    val title: String,
    val previewUrl: String?,
    val slides: List<StorySlide>,
    val viewed: Boolean,
) {
    /** Картинка кружка: превью, а без него — первый слайд-картинка. */
    val coverUrl: String?
        get() = previewUrl ?: slides.firstOrNull { it.type == SlideType.IMAGE }?.mediaUrl
}

enum class SlideType { IMAGE, VIDEO }

/**
 * Слайд истории. [buttonUrl] — уже проверенная ссылка: веб-страница или deep link
 * «Инкубатора»; чужой схемы здесь не бывает, см. [isAllowedButtonUrl]. [buttonText] есть
 * всегда, когда есть ссылка: сервер разрешает ссылку без подписи, а кнопка без слов —
 * это непонятно что.
 */
data class StorySlide(
    val type: SlideType,
    val mediaUrl: String,
    val durationMs: Long,
    val text: String?,
    val buttonText: String?,
    val buttonUrl: String?,
)

/**
 * Разбор ответа `GET /stories` и адреса медиа — чистые функции, их проверяет JVM-тест
 * (`StoryParseTest`). Всё непонятное отбрасывается молча, а не роняет ленту: слайд
 * неизвестного типа или без адреса выпадает, история без слайдов выпадает целиком. Лента
 * историй — не то, ради чего открыто приложение, и одна кривая запись в админке не
 * должна стоить людям всех остальных.
 */
object StoryParser {

    /** Сколько показывается слайд, если сервер не сказал; и границы, за которые не пускаем. */
    const val DEFAULT_DURATION_MS = 5_000L
    private const val MIN_DURATION_MS = 1_000L
    private const val MAX_DURATION_MS = 60_000L

    /** Подпись кнопки, у которой сервер прислал ссылку, но не текст. */
    const val DEFAULT_BUTTON_TEXT = "Подробнее"

    /** Схема deep link «Инкубатора» — та же, что у QR-кодов (`QrLink`) и в манифесте. */
    const val APP_SCHEME = "incubator"

    /**
     * Весь ответ. [serverBase] — адрес сервера из сборки: по нему достраиваются
     * относительные адреса и переписываются «локальные» (см. [resolveMediaUrl]).
     */
    fun parseStories(json: JSONArray, serverBase: String): List<Story> =
        (0 until json.length()).mapNotNull { i ->
            json.optJSONObject(i)?.let { parseStory(it, serverBase) }
        }

    fun parseStory(json: JSONObject, serverBase: String): Story? {
        val id = json.stringOrNull("id") ?: return null
        val slides = json.optJSONArray("slides")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { parseSlide(it, serverBase) }
            }
        }.orEmpty()
        if (slides.isEmpty()) return null
        return Story(
            id = id,
            title = json.stringOrNull("title").orEmpty(),
            previewUrl = json.stringOrNull("previewUrl")?.let { resolveMediaUrl(it, serverBase) },
            slides = slides,
            viewed = json.optBoolean("viewed", false),
        )
    }

    fun parseSlide(json: JSONObject, serverBase: String): StorySlide? {
        val type = when (json.stringOrNull("type")) {
            "image" -> SlideType.IMAGE
            "video" -> SlideType.VIDEO
            else -> return null
        }
        val media = json.stringOrNull("mediaUrl")?.let { resolveMediaUrl(it, serverBase) } ?: return null
        val duration = if (json.has("durationMs")) json.optLong("durationMs", DEFAULT_DURATION_MS) else DEFAULT_DURATION_MS
        val url = json.stringOrNull("buttonUrl")?.takeIf(::isAllowedButtonUrl)
        return StorySlide(
            type = type,
            mediaUrl = media,
            durationMs = duration.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
            text = json.stringOrNull("text"),
            buttonText = url?.let { json.stringOrNull("buttonText") ?: DEFAULT_BUTTON_TEXT },
            buttonUrl = url,
        )
    }

    /**
     * Ссылка кнопки, которую можно открыть: веб-страница или deep link самого
     * «Инкубатора». Сервер проверяет то же самое, но кнопка открывает `ACTION_VIEW`, и
     * проверить её ещё раз здесь ничего не стоит: `intent://` или `file://` из ответа
     * сервера открывать нельзя ни при каком сервере.
     */
    fun isAllowedButtonUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme == "http" || scheme == "https" || scheme == APP_SCHEME
    }

    /**
     * Адрес картинки или видео, по которому телефон их действительно получит.
     *
     * Относительный `/uploads/…` достраивается адресом сервера. А «локальный» —
     * `localhost` или `127.0.0.1` — переписывается на него же: так сервер отдаёт загрузки,
     * когда его `PUBLIC_BASE_URL` не задан (разработка), и с телефона такой адрес не
     * ведёт никуда — `localhost` телефона это сам телефон. Адрес сервера в сборке при
     * этом рабочий по построению: по нему только что пришёл сам список.
     */
    fun resolveMediaUrl(url: String, serverBase: String): String {
        val base = serverBase.trimEnd('/')
        if (url.startsWith("/")) return if (base.isEmpty()) url else base + url
        if (base.isEmpty()) return url
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        val host = parsed.host?.lowercase() ?: return url
        if (host != "localhost" && host != "127.0.0.1") return url
        val tail = buildString {
            append(parsed.rawPath.orEmpty())
            parsed.rawQuery?.let { append('?').append(it) }
        }
        return base + tail
    }

    /**
     * Просмотры, о которых сервер ещё не знает (запрос не дошёл — нет сети), и порядок:
     * непросмотренные первыми. Сервер сортирует так же, но свои просмотры он о нас не
     * знает, а порядок по двум источникам должен быть один. Сортировка устойчивая:
     * внутри каждой половины остаётся порядок сервера.
     */
    fun withLocalViews(stories: List<Story>, viewedLocally: Set<String>): List<Story> =
        stories
            .map { if (!it.viewed && it.id in viewedLocally) it.copy(viewed = true) else it }
            .sortedBy { it.viewed }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().ifBlank { null }
}
