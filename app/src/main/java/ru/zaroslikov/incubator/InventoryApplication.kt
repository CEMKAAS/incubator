package ru.zaroslikov.incubator


import android.app.Application
import androidx.work.Configuration
import com.yandex.mobile.ads.common.MobileAds
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import ru.zaroslikov.incubator.data.AppContainer
import ru.zaroslikov.incubator.data.AppDataContainer

class InventoryApplication: Application(), Configuration.Provider  {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val config =
            AppMetricaConfig.newConfigBuilder("cdc3cb4b-19cf-4469-aff8-5347d45faf69").build()
        AppMetrica.activate(this, config)
        MobileAds.initialize(this) { }
        container = AppDataContainer(this)
    }

    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.INFO)
        .build()
    }

