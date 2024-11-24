package ru.zaroslikov.incubator.ui


import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData


@Composable
fun Banner(modifier: Modifier = Modifier) {

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                BannerAdView(context).apply {
                    setAdUnitId("R-M-12856457-1")
                    setAdSize(BannerAdSize.stickySize(context, Int.MAX_VALUE))
                    val adRequesr = AdRequest.Builder().build()
                    setBannerAdEventListener(object : BannerAdEventListener {
                        override fun onAdClicked() {}
                        override fun onAdFailedToLoad(error: AdRequestError) {}
                        override fun onAdLoaded() {}
                        override fun onImpression(impressionData: ImpressionData?) {}
                        override fun onLeftApplication() {}
                        override fun onReturnedToApplication() {}
                    })
                    loadAd(adRequesr)
                }
            }
        )
    }
}




