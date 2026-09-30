package ru.zaroslikov.incubator.vkid

import android.content.Context
import android.util.Log
import com.vk.id.AccessToken
import com.vk.id.VKID
import com.vk.id.VKIDAuthFail
import com.vk.id.auth.VKIDAuthCallback
import com.vk.id.logout.VKIDLogoutCallback
import com.vk.id.logout.VKIDLogoutFail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.BuildConfig

/**
 * Что вернул вход через VK ID.
 *
 * Три исхода, а не «успех / ошибка»: отказ человека — не сбой, о нём не пишут красным и
 * его не считают в аналитике провалом входа.
 */
sealed interface VkLoginResult {
    data class Success(val account: VkAccount) : VkLoginResult
    data object Cancelled : VkLoginResult
    data class Failed(val reason: String) : VkLoginResult
}

/**
 * Пользователь VK в том виде, в каком он нужен аккаунту: токен для сервера, идентификатор,
 * имя и адрес фото.
 *
 * [accessToken] уходит ровно в одно место — `POST /auth/vk` сервера аккаунтов, который
 * проверяет его у VK сам и выдаёт свои токены. Больше приложение с ним ничего не делает и
 * нигде его не хранит: сам токен держит SDK в своём зашифрованном хранилище.
 */
data class VkAccount(
    val accessToken: String,
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val photoUrl: String?,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    // Сгенерированный toString напечатал бы токен в любой лог, куда попадёт результат входа.
    override fun toString() = "VkAccount(userId=$userId)"
}

/**
 * Вход через VK ID — единственное место в приложении, которое знает про `com.vk.id`.
 *
 * Как `analytics/` для AppMetrica и `rustore/` для RuStore: всё, что импортирует SDK,
 * лежит в этом пакете, и `grep -rn "com.vk.id" app/src/main --include=*.kt` обязан
 * находить только его. Экран профиля видит три функции и два типа выше.
 *
 * **Пустой `vkidClientId` — рабочее состояние.** Тогда [isAvailable] ложно, SDK не
 * поднимается вовсе, и экран профиля не рисует ни одной кнопки VK: регистрация остаётся
 * ручной. Это то, что получает свежий клон репозитория, — ключи приложения в кабинете
 * VK ID выдаются под подпись APK и в git не лежат (см. `app/build.gradle.kts`).
 */
object VkId {

    private const val TAG = "VkId"

    /** Настроено ли приложение в кабинете VK ID — есть ли в сборке ключи. */
    private val isConfigured: Boolean get() = BuildConfig.VKID_CLIENT_ID.isNotBlank()

    /**
     * Можно ли входить: ключи есть **и** SDK поднялся. Без второго кнопка входа
     * показывалась бы там, где [init] упал, и нажатие на неё только сообщало бы об ошибке.
     */
    val isAvailable: Boolean get() = initialized

    @Volatile
    private var initialized = false

    /**
     * Поднимает SDK. Вызывается из `InventoryApplication.onCreate`.
     *
     * SDK требует ровно одной инициализации на процесс (второй вызов в релизной сборке
     * бросает), поэтому флаг. Сбой инициализации глотается: вход через VK — удобство, и
     * приложение, упавшее на старте из-за него, наказывало бы всех, кто им не пользуется.
     */
    fun init(context: Context) {
        if (!isConfigured || initialized) return
        try {
            VKID.init(context.applicationContext)
            initialized = true
        } catch (e: Exception) {
            Log.w(TAG, "VK ID не инициализирован", e)
        }
    }

    /**
     * Входит через VK ID: приложение VK, если оно стоит, иначе браузер.
     *
     * Окно входа SDK открывает сам — отдельной активностью поверх приложения, — поэтому
     * функция просто ждёт исхода. Отменённая корутина исход не отменяет (окно уже
     * открыто), но и ждать его больше некому: результат, пришедший после, теряется, а
     * токен остаётся у SDK до следующего входа или выхода.
     */
    suspend fun login(): VkLoginResult {
        if (!initialized) return VkLoginResult.Failed("VK ID не настроен")
        return try {
            val outcome = CompletableDeferred<VkLoginResult>()
            val callback = object : VKIDAuthCallback {
                override fun onAuth(accessToken: AccessToken) {
                    outcome.complete(VkLoginResult.Success(accessToken.toAccount()))
                }

                override fun onFail(fail: VKIDAuthFail) {
                    outcome.complete(
                        if (fail is VKIDAuthFail.Canceled) {
                            VkLoginResult.Cancelled
                        } else {
                            VkLoginResult.Failed(fail.description)
                        }
                    )
                }
            }
            // `authorize` возвращается, как только окно входа открыто, а исход приходит
            // в колбэк позже — в контексте этой же корутины. Поэтому ждать приходится
            // отдельно, на `outcome`.
            VKID.instance.authorize(callback)
            // Потолок — на случай, если SDK так и не позовёт колбэк (его сбой внутри
            // `authorize` глотается собственным обработчиком): кнопка входа иначе осталась
            // бы погашенной до перезапуска. Десять минут — с запасом на ввод пароля и кода.
            withTimeoutOrNull(LOGIN_TIMEOUT_MS) { outcome.await() }
                ?: VkLoginResult.Failed("VK ID не ответил")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Вход через VK ID не удался", e)
            VkLoginResult.Failed(e.message.orEmpty())
        }
    }

    /**
     * Выходит из VK ID: SDK отзывает токен и забывает его.
     *
     * Ошибка выхода — сеть, уже отозванный токен — не мешает отвязке: профиль забывает
     * VK в любом случае, а оставшийся у SDK токен перепишет следующий вход.
     */
    suspend fun logout() {
        if (!initialized) return
        try {
            // Выход — сетевой запрос; без сети он висит до таймаута SDK. Ждать его
            // дольше незачем: профиль к этому моменту уже отвязан.
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                val done = CompletableDeferred<Unit>()
                VKID.instance.logout(
                    callback = object : VKIDLogoutCallback {
                        override fun onSuccess() {
                            done.complete(Unit)
                        }

                        override fun onFail(fail: VKIDLogoutFail) {
                            Log.w(TAG, "Выход из VK ID: ${fail.description}")
                            done.complete(Unit)
                        }
                    },
                )
                done.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Выход из VK ID не удался", e)
        }
    }

    private const val LOGIN_TIMEOUT_MS = 10 * 60 * 1000L
    private const val LOGOUT_TIMEOUT_MS = 15_000L

    private fun AccessToken.toAccount() = VkAccount(
        accessToken = token,
        userId = userID,
        firstName = userData.firstName,
        lastName = userData.lastName,
        photoUrl = userData.photo200 ?: userData.photo100,
    )
}
