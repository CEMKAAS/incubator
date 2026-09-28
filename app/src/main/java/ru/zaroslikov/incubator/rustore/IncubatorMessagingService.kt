package ru.zaroslikov.incubator.rustore

import android.util.Log
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events

private const val TAG = "RuStorePush"

/**
 * Служба, через которую RuStore отдаёт приложению пуши.
 *
 * **Уведомление она не рисует, и это не упущение.** Сообщение, отправленное из консоли
 * RuStore с заголовком и текстом, показывает сам SDK — со значком и каналом из
 * `meta-data` в манифесте (см. [PUSH_CHANNEL_ID]). Нарисовать его ещё раз здесь значило
 * бы показать два уведомления на один пуш. Служба нужна затем, чтобы приложение знало,
 * что пуш дошёл, и затем, что без объявленной службы SDK доставлять некуда.
 *
 * **Отчёт о доставке — половина ответа на вопрос, работают ли пуши вообще.** У
 * офлайнового приложения нет сервера, который увидел бы, что сообщение получено;
 * `Events.PUSH_RECEIVED` — единственное место, где это видно, и знаменатель для
 * «сколько людей по нему пришло». Событие шлётся в своём процессе: `Application.onCreate`
 * выполняется и здесь, а значит AppMetrica уже поднята.
 *
 * Заголовок в параметрах — тот же, что в консоли, и это сознательно: он нужен, чтобы
 * различить рассылки между собой. Ничего из данных пользователя пуш не несёт и нести не
 * может — он приходит снаружи.
 */
class IncubatorMessagingService : RuStoreMessagingService() {

    override fun onNewToken(token: String) {
        // В лог, а не в аналитику: токен адресует конкретный телефон, и в отчётах ему
        // делать нечего. Нужен он ровно для одного — отправить себе пробный пуш.
        Log.i(TAG, "Новый токен: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Analytics.report(
            Events.PUSH_RECEIVED,
            mapOf("Заголовок" to message.notification?.title),
        )
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        // Сюда приходит в том числе «на телефоне нет RuStore» — обычное положение дел
        // для приложения, поставленного из другого магазина, а не поломка.
        errors.forEach { Log.d(TAG, "Пуши недоступны: ${it.message}") }
    }
}
