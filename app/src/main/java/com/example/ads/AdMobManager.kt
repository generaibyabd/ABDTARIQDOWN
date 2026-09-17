package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdMobManager {

    // Official Google AdMob Test Ad Unit IDs
    const val BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShownTimestamp = 0L
    private const val MIN_COOLDOWN_MS = 60_000L // 60s cooldown to protect user experience

    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context) {
                preloadInterstitial(context)
            }
        } catch (_: Exception) {}
    }

    fun preloadInterstitial(context: Context) {
        if (isLoading || interstitialAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_TEST_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Shows interstitial only if preloaded AND cooldown satisfied.
     * Never delays, blocks, or degrades user flow if ad isn't ready.
     */
    fun showInterstitialIfReady(activity: Activity?, onFinished: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd

        if (activity == null || ad == null || (now - lastShownTimestamp < MIN_COOLDOWN_MS)) {
            onFinished()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastShownTimestamp = System.currentTimeMillis()
                preloadInterstitial(activity)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial(activity)
                onFinished()
            }

            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
            }
        }

        try {
            ad.show(activity)
        } catch (_: Exception) {
            onFinished()
        }
    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobManager.BANNER_TEST_ID
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
