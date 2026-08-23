package ru.zaroslikov.incubator


import android.app.Application
import androidx.work.Configuration
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import ru.zaroslikov.incubator.di.AppContainer
import ru.zaroslikov.incubator.di.AppDataContainer

class InventoryApplication: Application(), Configuration.Provider  {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val config =
            AppMetricaConfig.newConfigBuilder("cdc3cb4b-19cf-4469-aff8-5347d45faf69").build()
        AppMetrica.activate(this, config)
        container = AppDataContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
