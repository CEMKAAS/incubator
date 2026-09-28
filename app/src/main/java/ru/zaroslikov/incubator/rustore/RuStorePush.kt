package ru.zaroslikov.incubator.rustore

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import ru.rustore.sdk.core.tasks.OnFailureListener
import ru.rustore.sdk.core.tasks.OnSuccessListener
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.common.logger.DefaultLogger

private const val TAG = "RuStorePush"

/**
 * Канал, в котором показываются пуши.
 *
 * **Повторён дословно в `AndroidManifest.xml`** — в `meta-data` с именем
 * `ru.rustore.sdk.pushclient.default_notification_channel_id`: SDK рисует уведомление
 * сам и берёт канал оттуда, а манифест константы из кода не видит. Правило то же, что у
 * имени файла `device_prefs` в правилах резервной копии: поменяешь здесь — поменяй там,
 * иначе на Android 8 и новее уведомление не покажется вовсе (канала с таким
 * идентификатором просто не будет).
 */
const val PUSH_CHANNEL_ID = "Новости"

private val PUSH_CHANNEL_NAME: CharSequence = "Новости приложения"

private const val PUSH_CHANNEL_DESCRIPTION =
    "Сообщения о новых возможностях и важных изменениях"

/**
 * Пуш-уведомления RuStore — то немногое, чем офлайновое приложение может сказать
 * что-нибудь своим пользователям.
 *
 * **Отдельный канал, а не тот же, что у напоминаний.** Напоминание проверить инкубатор
 * — это то, ради чего приложение ставили, и оно звонит громко (`IMPORTANCE_HIGH`,
 * канал `Инкубатор` в `work/Constants.kt`). Новость о новой возможности звонить громко
 * не должна и, главное, должна выключаться отдельно: одним каналом на двоих человек,
 * которому надоели новости, выключил бы себе и напоминания — то есть само приложение.
 *
 * **Поднимается только при заполненном идентификаторе проекта.** Пустой
 * `BuildConfig.RUSTORE_PUSH_PROJECT_ID` — рабочее состояние сборки, у которой своего
 * проекта в консоли RuStore нет (см. `rustorePushProjectId` в `app/build.gradle.kts`):
 * приложение тогда просто не знает про пуши и ведёт себя в точности как до них.
 *
 * **Инициализация в `Application.onCreate` — требование SDK**, и оно неудобно тем, что
 * этот метод выполняется в каждом процессе приложения, в том числе в поднятом
 * WorkManager ради напоминания. Поэтому здесь нет ничего тяжёлого: создание канала —
 * проверка и, изредка, одна системная запись, а сам `init` у SDK асинхронный.
 * [RuStorePushClient.isInitialized] сторожит повторный вызов: он бросает, а падение в
 * `onCreate` — это падение приложения.
 */
object RuStorePush {

    fun init(application: Application, projectId: String) {
        if (projectId.isBlank()) {
            Log.d(TAG, "Идентификатор проекта не задан — пуши выключены")
            return
        }
        createChannel(application)
        if (RuStorePushClient.isInitialized) return
        runCatching {
            RuStorePushClient.init(application, projectId, DefaultLogger(TAG))
        }.onFailure {
            // Ошибка здесь означает «пушей не будет», и только: ни одна часть
            // приложения на них не опирается.
            Log.w(TAG, "Пуши не поднялись: ${it.message}")
        }
    }

    /**
     * Токен этой установки — им консоль RuStore адресует сообщение конкретному телефону.
     *
     * Нужен для проверки на живом устройстве: отправить пуш «себе» иначе не во что.
     * В логе, а не на экране, потому что ни одному пользователю он не нужен.
     */
    fun logToken() {
        if (!RuStorePushClient.isInitialized) return
        RuStorePushClient.getToken()
            .addOnSuccessListener(object : OnSuccessListener<String> {
                override fun onSuccess(result: String) {
                    Log.i(TAG, "Токен: $result")
                }
            })
            .addOnFailureListener(object : OnFailureListener {
                override fun onFailure(throwable: Throwable) {
                    Log.d(TAG, "Токен не получен: ${throwable.message}")
                }
            })
    }

    /**
     * Канал новостей. Создаётся заранее, а не при первом пуше: уведомление рисует SDK, и
     * к этому моменту канал уже должен существовать — иначе система его молча не покажет.
     *
     * Повторное создание канала с теми же данными система игнорирует, но проверка стоит
     * здесь по той же причине, что и в напоминаниях: пересоздание сбросило бы то, что
     * человек в канале поменял руками, будь оно когда-нибудь пересозданием.
     */
    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(PUSH_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            PUSH_CHANNEL_ID,
            PUSH_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = PUSH_CHANNEL_DESCRIPTION }
        manager.createNotificationChannel(channel)
    }
}
