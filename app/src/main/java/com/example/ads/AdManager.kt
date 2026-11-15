package com.example.ads

import com.example.BuildConfig
import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages AdMob interstitial ads lifecycle.
 *
 * AppContainer calls [setAllowed] only after consent and Premium state are known.
 * Ad unit IDs come from variant configuration; debug always uses Google's test unit.
 */
class AdManager(private val context: Context, private val onImpression: () -> Unit = {}) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var initialized = false
    private var initializing = false
    private var allowed = false
    private var generation = 0

    fun setAllowed(value: Boolean) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (allowed == value) return
        allowed = value
        generation++
        interstitialAd = null
        isLoading = false
        if (value) { initialize(); preload() }
    }

    companion object {
        // Google's official test interstitial unit ID — replace with real ID after AdMob account is set up
        private val AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID
    }

    /**
     * Starts initialization after eligibility has been confirmed.
     */
    fun initialize(onComplete: (() -> Unit)? = null) {
        if (!allowed || initialized || initializing || AD_UNIT_ID.isBlank()) return
        initializing = true
        MobileAds.initialize(context) {
            mainHandler.post {
                initializing = false
                initialized = true
                onComplete?.invoke()
                preload()
            }
        }
    }

    /**
     * Loads an interstitial ad in the background so it's ready to show instantly.
     * Called automatically after [initialize] and after each successful show.
     */
    fun preload() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { preload() }
            return
        }
        if (!allowed || !initialized || isLoading || interstitialAd != null) return
        isLoading = true
        val requestedGeneration = generation

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (!allowed || requestedGeneration != generation) return
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (requestedGeneration != generation) return
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Shows the interstitial ad if it's loaded.
     * If the ad is not ready — calls [onDismissed] immediately so the user flow continues.
     *
     * @param activity The current Activity (required by AdMob to show full-screen ad)
     * @param onDismissed Called when the ad is closed or if no ad is available
     */
    fun showIfLoaded(activity: Activity, onDismissed: () -> Unit) {
        if (!allowed || activity.isFinishing || activity.isDestroyed) { onDismissed(); return }
        val ad = interstitialAd
        if (ad == null) {
            // No ad ready — proceed without showing one
            onDismissed()
            preload() // try loading for next time
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdImpression() { onImpression() }
            override fun onAdDismissedFullScreenContent() {
                preload() // immediately load the next one
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload()
                onDismissed()
            }
        }

        interstitialAd = null
        ad.show(activity)
    }

    /**
     * Whether an ad is currently loaded and ready to be shown.
     */
    val isReady: Boolean get() = allowed && interstitialAd != null
}
