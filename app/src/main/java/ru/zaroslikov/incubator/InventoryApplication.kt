package ru.zaroslikov.incubator


import android.app.Application
import androidx.work.Configuration
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.di.AppContainer
import ru.zaroslikov.incubator.di.AppDataContainer
import ru.zaroslikov.incubator.rustore.IS_RUSTORE_BUILD
import ru.zaroslikov.incubator.rustore.RuStorePush

class InventoryApplication: Application(), Configuration.Provider  {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
        // Аналитика включается после контейнера, а не до него: профиль AppMetrica
        // подписывается идентификатором установки, а тот живёт в настройках
        // (`AppSettings.installationId`). Порядок важен только здесь — событий до этой
        // строки всё равно нет.
        Analytics.activate(this, container.appSettings)
        // Ночной режим системы для этого приложения — под выбранную тему, до первого окна:
        // иначе стартовое окно красится по теме телефона, а не по выбору человека.
        container.appSettings.syncNightMode()
        // Пуши RuStore. Именно здесь, потому что SDK требует `Application.onCreate`, и
        // здесь же — в каждом процессе приложения, включая поднятый WorkManager ради
        // напоминания: пуш приходит в свой процесс, и подниматься он должен в любом.
        // При пустом идентификаторе проекта вызов молча ничего не делает, см. RuStorePush.
        // В сборке не для RuStore пушей нет вовсе: доставляет их приложение магазина,
        // которого у такого пользователя может не быть, — см. IS_RUSTORE_BUILD.
        if (IS_RUSTORE_BUILD) {
            RuStorePush.init(this, BuildConfig.RUSTORE_PUSH_PROJECT_ID)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
