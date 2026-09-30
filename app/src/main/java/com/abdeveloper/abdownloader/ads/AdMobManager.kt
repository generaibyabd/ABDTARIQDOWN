package com.abdeveloper.abdownloader.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object AdMobManager {
    // Production Google AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-6044304548635745/1572196371"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-6044304548635745/6354196870"

    private const val TAG = "AdMobManager"

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    fun initialize(context: Context) {
        // Test device safeguard: Automatically registers emulator ID and the current device's
        // MD5-hashed ANDROID_ID (which matches AdMob's exact test device formula).
        val initialTestDevices = mutableListOf<String>()
        initialTestDevices.add(AdRequest.DEVICE_ID_EMULATOR)
        getHashedDeviceId(context)?.let {
            initialTestDevices.add(it.uppercase())
            initialTestDevices.add(it.lowercase())
        }

        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(initialTestDevices)
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
                // Fetch Advertising ID asynchronously to register GAID hash if available
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val adInfo = com.google.android.gms.ads.identifier.AdvertisingIdClient.getAdvertisingIdInfo(context)
                        val gaid = adInfo.id
                        if (!gaid.isNullOrBlank()) {
                            val md5Gaid = md5Hex(gaid)
                            val updated = MobileAds.getRequestConfiguration().testDeviceIds.toMutableList()
                            if (!updated.contains(md5Gaid.uppercase())) updated.add(md5Gaid.uppercase())
                            if (!updated.contains(md5Gaid.lowercase())) updated.add(md5Gaid.lowercase())
                            MobileAds.setRequestConfiguration(
                                RequestConfiguration.Builder()
                                    .setTestDeviceIds(updated)
                                    .build()
                            )
                        }
                    } catch (_: Throwable) {}
                    withContext(Dispatchers.Main) {
                        loadInterstitial(context)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob: ${e.message}")
        }
    }

    private fun getHashedDeviceId(context: Context): String? {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId.isNullOrBlank()) return null
            md5Hex(androidId)
        } catch (_: Exception) {
            null
        }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in hash) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    fun loadInterstitial(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                loadInterstitial(context)
            }
            return
        }
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true

        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                        Log.d(TAG, "Interstitial Ad Loaded successfully")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                        Log.w(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isLoadingInterstitial = false
            Log.e(TAG, "Failed to load interstitial ad: ${e.message}")
        }
    }

    fun showInterstitialIfReady(activity: Activity?, onDismissedOrSkipped: () -> Unit) {
        val ad = interstitialAd
        if (activity != null && ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismissedOrSkipped()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismissedOrSkipped()
                }
            }
            ad.show(activity)
        } else {
            onDismissedOrSkipped()
        }
    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobManager.BANNER_AD_UNIT_ID
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdMobBanner", "Banner failed to load: ${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
