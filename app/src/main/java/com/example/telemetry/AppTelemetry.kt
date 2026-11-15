package com.example.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Records only product events. Do not add names, search queries, or other personal data here.
 *
 * The implementation intentionally becomes a no-op until google-services.json is supplied by the
 * Firebase project. This keeps local and open-source builds usable without production credentials.
 */
interface AppTelemetry {
    fun appOpened()
    fun screenViewed(screen: String)
    fun practiceStarted(wordCount: Int)
    fun reviewStarted(wordCount: Int, isEarlyReview: Boolean)
    fun sessionCompleted(mode: String, correctCount: Int, totalCount: Int)
    fun progressReset()
    fun adShown()
    fun premiumOpened()
    fun premiumPurchased()
    fun premiumVerified(restored: Boolean, active: Boolean) = Unit
    fun recordException(throwable: Throwable)
    fun syncFinished(durationMs: Long, pendingCount: Int, success: Boolean) = Unit
}

object NoOpTelemetry : AppTelemetry {
    override fun appOpened() = Unit
    override fun screenViewed(screen: String) = Unit
    override fun practiceStarted(wordCount: Int) = Unit
    override fun reviewStarted(wordCount: Int, isEarlyReview: Boolean) = Unit
    override fun sessionCompleted(mode: String, correctCount: Int, totalCount: Int) = Unit
    override fun progressReset() = Unit
    override fun adShown() = Unit
    override fun premiumOpened() = Unit
    override fun premiumPurchased() = Unit
    override fun recordException(throwable: Throwable) = Unit
}

class FirebaseTelemetry(context: Context) : AppTelemetry {
    @Volatile private var collectionEnabled = false
    private val analytics: FirebaseAnalytics?
    private val crashlytics: FirebaseCrashlytics?

    init {
        val appContext = context.applicationContext
        val hasFirebaseConfiguration = appContext.resources.getIdentifier(
            "google_app_id",
            "string",
            appContext.packageName
        ) != 0

        if (hasFirebaseConfiguration) {
            val initialized = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull() != null
            analytics = if (initialized) runCatching { FirebaseAnalytics.getInstance(appContext) }.getOrNull() else null
            crashlytics = if (initialized) runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() else null
        } else {
            analytics = null
            crashlytics = null
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        analytics?.setAnalyticsCollectionEnabled(enabled)
        crashlytics?.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) {
            analytics?.resetAnalyticsData()
            crashlytics?.deleteUnsentReports()
        }
    }

    override fun appOpened() = log("app_opened")

    override fun screenViewed(screen: String) = log("screen_viewed") {
        putString("screen", screen)
    }

    override fun practiceStarted(wordCount: Int) = log("practice_started") {
        putLong("word_count", wordCount.toLong())
    }

    override fun reviewStarted(wordCount: Int, isEarlyReview: Boolean) = log("review_started") {
        putLong("word_count", wordCount.toLong())
        putLong("is_early_review", if (isEarlyReview) 1 else 0)
    }

    override fun sessionCompleted(mode: String, correctCount: Int, totalCount: Int) = log("learning_session_completed") {
        putString("mode", mode)
        putLong("correct_count", correctCount.toLong())
        putLong("total_count", totalCount.toLong())
    }

    override fun progressReset() = log("progress_reset")

    override fun adShown() = log("ad_shown")

    override fun premiumOpened() = log("premium_screen_opened")

    override fun premiumPurchased() = log("premium_purchased")

    override fun premiumVerified(restored: Boolean, active: Boolean) = log("premium_entitlement_verified") {
        putLong("restored", if (restored) 1L else 0L)
        putLong("active", if (active) 1L else 0L)
    }

    override fun recordException(throwable: Throwable) {
        if (collectionEnabled) crashlytics?.recordException(throwable)
    }

    override fun syncFinished(durationMs: Long, pendingCount: Int, success: Boolean) = log("progress_sync_finished") {
        putLong("duration_ms", durationMs)
        putLong("pending_count", pendingCount.toLong())
        putLong("success", if (success) 1L else 0L)
    }

    private inline fun log(eventName: String, parameters: Bundle.() -> Unit = {}) {
        if (!collectionEnabled) return
        val bundle = Bundle().apply(parameters)
        analytics?.logEvent(eventName, bundle)
        crashlytics?.log("event=$eventName")
    }
}
