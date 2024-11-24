package ru.zaroslikov.incubator

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.yandex.mobile.ads.appopenad.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import ru.zaroslikov.incubator.ui.theme.IncubatorTheme


class MainActivity : ComponentActivity() {

    companion object {
        private var TAG = "MainActivity"
        const val REQUEST_CODE_NOTIFICATION_PERMISSIONS = 228
    }

    private var appOpenAd: AppOpenAd? = null
    private var isAdShownOnColdStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firstLaunch = isFirstLaunch(this)
        if (firstLaunch) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getNotificationPermissions()
        setContent {
            IncubatorTheme {

                MobileAds.initialize(this) {
                    loadAppOpenAd()
                    val processLifecycleObserver =
                        DefaultProcessLifecycleObserver(onProcessCaneForeground = ::showAppOpenAd)
                    ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
                }

                InventoryApp(firstLaunch = firstLaunch)
            }
        }
    }


    private fun getNotificationPermissions() {
        try {
            val hasAccessNotificationPolicyPermission =
                checkSelfPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY) == PackageManager.PERMISSION_GRANTED
            val hasPostNotificationsPermission =
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            when {
                !hasAccessNotificationPolicyPermission || !hasPostNotificationsPermission -> {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.ACCESS_NOTIFICATION_POLICY,
                            Manifest.permission.POST_NOTIFICATIONS
                        ), REQUEST_CODE_NOTIFICATION_PERMISSIONS
                    )
                }

                else -> Log.d(TAG, "Notification Permissions : previously granted successfully")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_NOTIFICATION_PERMISSIONS -> {
                val hasAccessNotificationPolicyPermission =
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                val hasPostNotificationsPermission =
                    grantResults[1] == PackageManager.PERMISSION_GRANTED
                when {
                    !hasAccessNotificationPolicyPermission || !hasPostNotificationsPermission -> {
                        getNotificationPermissions()
                    }

                    else -> {
                        Log.d(TAG, "Notification Permissions : Granted successfully")
                    }
                }
            }
        }
    }


    //Реклама при запуске приложения
    private fun showAppOpenAd() {
        val appOpenAdEventListener = AdEventListener()
        appOpenAd?.setAdEventListener(appOpenAdEventListener)
        appOpenAd?.show(this@MainActivity)
    }

    private inner class AdEventListener : AppOpenAdEventListener {
        override fun onAdShown() {}
        override fun onAdFailedToShow(adError: AdError) {
            clearAppOpenAd()
            loadAppOpenAd()
        }

        override fun onAdDismissed() {
            clearAppOpenAd()
            loadAppOpenAd()
        }

        override fun onAdClicked() {}
        override fun onAdImpression(impressionData: ImpressionData?) {}
    }


    private fun clearAppOpenAd() {
        appOpenAd?.setAdEventListener(null)
        appOpenAd = null
    }

    private fun loadAppOpenAd() {
        val appOpenAdLoader = AppOpenAdLoader(application)
        val appOpenAdLoadListener = object : AppOpenAdLoadListener {
            override fun onAdLoaded(appOpenAd: AppOpenAd) {
                this@MainActivity.appOpenAd = appOpenAd
                if (!isAdShownOnColdStart) {
                    showAppOpenAd()
                    isAdShownOnColdStart = true
                }
            }

            override fun onAdFailedToLoad(adRequestError: AdRequestError) {}
        }
        appOpenAdLoader.setAdLoadListener(appOpenAdLoadListener)

        val AD_UNIT_ID = "R-M-12856457-2"
        val adRequestConfiguration = AdRequestConfiguration.Builder(AD_UNIT_ID).build()
        appOpenAdLoader.loadAd(adRequestConfiguration)
        appOpenAdLoader.loadAd(adRequestConfiguration)
    }
}


// Функция для проверки первого запуска приложения
fun isFirstLaunch(context: Context): Boolean {
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val isFirstLaunch = sharedPreferences.getBoolean("is_first_launch", true)

    if (isFirstLaunch) sharedPreferences.edit().putBoolean("is_first_launch", false).apply()

    return isFirstLaunch
}
