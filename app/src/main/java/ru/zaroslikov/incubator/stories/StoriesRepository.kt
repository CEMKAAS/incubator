package ru.zaroslikov.incubator.stories

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.account.AccountApi
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.account.AccountState
import ru.zaroslikov.incubator.settings.AppSettings
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Лента историй для главного экрана. [loaded] — сервер ответил хотя бы раз за процесс. */
data class StoriesFeed(
    val stories: List<Story> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Истории главного экрана — с `server_ferma`, того же сервера, что и аккаунт.
 *
 * **Вход не нужен.** Истории видят все; вошедший предъявляет свой токен, и сервер помнит
 * просмотры за аккаунтом, а не вошедший — `X-Install-Id`, идентификатор этой установки
 * ([AppSettings.installationId]), и просмотры помнятся за телефоном. Идентификатор уходит
 * всегда: токен может не добыться (нет сети для refresh), и тогда запрос всё равно должен
 * знать, чьи это просмотры.
 *
 * **Нет сети — нет новой ленты, но прежняя остаётся.** Приложение офлайновое, и ни одна
 * его функция от историй не зависит: сбой запроса не очищает то, что уже показано, и не
 * показывает ошибок. Нет адреса сервера в сборке — историй нет вовсе ([isAvailable]).
 *
 * **Просмотр записывается дважды**: на сервер (`POST /stories/{id}/view`) и в
 * `device_prefs` ([AppSettings.viewedStories]). Второе — ради телефона без сети: иначе
 * история, досмотренная в подвале, при следующем запуске снова стояла бы в цветном кольце.
 * Локальный список не растёт без конца — после каждого ответа сервера в нём остаются только
 * те истории, которые сервер ещё считает непросмотренными ([prune]).
 *
 * Живёт в `AppContainer`, как аккаунт: лента одна на процесс, и возвращение на главный
 * экран не должно перезапрашивать её каждый раз — только если она старше [MAX_AGE_MS] или
 * сменился вошедший (у другого человека другие просмотры).
 */
class StoriesRepository(
    context: Context,
    private val account: AccountRepository,
    private val settings: AppSettings,
) {

    private val baseUrl = BuildConfig.ACCOUNT_SERVER_URL.trim().trimEnd('/')
    private val api = "$baseUrl/api/v1"

    val isAvailable: Boolean get() = baseUrl.isNotEmpty()

    val images = StoryImages(context.applicationContext)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "Истории: фоновая работа не удалась", e) }
    )

    private val refreshLock = Mutex()
    private var fetchedAt = 0L
    /** Чьи просмотры в текущей ленте: id вошедшего, `""` — установки. */
    private var fetchedFor: String? = null

    private val _feed = MutableStateFlow(StoriesFeed())
    val feed: StateFlow<StoriesFeed> = _feed.asStateFlow()

    /**
     * Перечитывает ленту, если она устарела или сменился вошедший; [force] — в любом
     * случае. Два одновременных вызова не делают двух запросов: второй дождётся первого и
     * увидит свежую ленту.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!isAvailable) return
        refreshLock.withLock {
            val viewer = when (val state = account.currentState()) {
                is AccountState.SignedIn -> state.userId
                else -> ""
            }
            val fresh = System.currentTimeMillis() - fetchedAt < MAX_AGE_MS
            if (!force && fresh && viewer == fetchedFor) return
            val stories = fetch() ?: return
            fetchedAt = System.currentTimeMillis()
            fetchedFor = viewer
            val local = prune(stories)
            _feed.value = StoriesFeed(StoryParser.withLocalViews(stories, local), loaded = true)
        }
    }

    /**
     * История досмотрена до конца. Кольцо гаснет сразу, а порядок в ленте не меняется до
     * следующего ответа сервера: кружок, уехавший в конец строки, пока человек ещё смотрит
     * на неё, читался бы как пропавший.
     */
    fun markViewed(storyId: String) {
        _feed.update { feed ->
            feed.copy(stories = feed.stories.map { if (it.id == storyId) it.copy(viewed = true) else it })
        }
        settings.rememberStoryViewed(storyId)
        background.launch { sendView(storyId) }
    }

    /** Оставляет в локальном списке только то, что сервер ещё не знает просмотренным. */
    private fun prune(stories: List<Story>): Set<String> {
        val unviewedOnServer = stories.filterNot { it.viewed }.map { it.id }.toSet()
        val kept = settings.viewedStories.intersect(unviewedOnServer)
        settings.setViewedStories(kept)
        return kept
    }

    private suspend fun fetch(): List<Story>? {
        val token = account.optionalAccessToken()
        return when (val first = get(token)) {
            is Fetched.Ok -> first.stories
            // Токен отвергнут (сессию закрыли в «Моём хозяйстве» секунду назад) — истории
            // от этого не перестают быть видны: тот же запрос без него, от имени установки.
            Fetched.Unauthorized -> if (token != null) (get(null) as? Fetched.Ok)?.stories else null
            Fetched.Failed -> null
        }
    }

    private sealed interface Fetched {
        data class Ok(val stories: List<Story>) : Fetched
        data object Unauthorized : Fetched
        data object Failed : Fetched
    }

    private suspend fun get(token: String?): Fetched = withContext(Dispatchers.IO) {
        try {
            client.newCall(request("/stories", token).get().build()).execute().use { response ->
                when {
                    response.code == 401 -> Fetched.Unauthorized
                    !response.isSuccessful -> Fetched.Failed
                    else -> Fetched.Ok(
                        StoryParser.parseStories(JSONArray(response.body?.string().orEmpty()), baseUrl)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Fetched.Failed
        } catch (e: org.json.JSONException) {
            // Непонятный ответ (прокси вернул HTML) — лента остаётся прежней.
            Fetched.Failed
        } catch (e: RuntimeException) {
            Fetched.Failed
        }
    }

    private suspend fun sendView(storyId: String) {
        val token = account.optionalAccessToken()
        val path = "/stories/${java.net.URLEncoder.encode(storyId, "UTF-8").replace("+", "%20")}/view"
        val code = post(path, token)
        if (code == 401 && token != null) post(path, null)
    }

    private fun post(path: String, token: String?): Int = try {
        client.newCall(request(path, token).post(ByteArray(0).toRequestBody()).build())
            .execute().use { it.code }
    } catch (e: IOException) {
        0
    } catch (e: RuntimeException) {
        0
    }

    private fun request(path: String, token: String?): Request.Builder =
        Request.Builder()
            .url(api + path)
            .header("Accept", "application/json")
            .header(AccountApi.APP_ID_HEADER, AccountApi.APP_ID)
            .header(INSTALL_ID_HEADER, settings.installationId)
            .apply { token?.let { header("Authorization", "Bearer $it") } }

    private companion object {
        const val TAG = "StoriesRepository"
        const val INSTALL_ID_HEADER = "X-Install-Id"
        const val TIMEOUT_SECONDS = 15L
        const val CALL_TIMEOUT_SECONDS = 20L

        /** Лента на главном экране живёт столько, прежде чем возврат на экран её обновит. */
        const val MAX_AGE_MS = 5 * 60 * 1000L

    }
}
