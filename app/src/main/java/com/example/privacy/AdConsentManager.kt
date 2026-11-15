package com.example.privacy

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

/** UMP is the sole authority for ad consent; SDK callbacks are delivered on the main thread. */
class AdConsentManager(context: Context) {
    private val information = UserMessagingPlatform.getConsentInformation(context.applicationContext)
    private val mutableState = MutableStateFlow(AdConsentState())
    val state = mutableState.asStateFlow()
    private var requestGeneration = 0

    fun gather(activity: Activity) {
        val generation = ++requestGeneration
        mutableState.value = mutableState.value.copy(canRequestAds = false, busy = true, error = null)
        information.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if (generation != requestGeneration) return@requestConsentInfoUpdate
            if (activity.isFinishing || activity.isDestroyed) { publish(); return@requestConsentInfoUpdate }
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (generation == requestGeneration) publish(error?.let { "Не вдалося оновити рекламні налаштування. Спробуйте пізніше." })
            }
        }, {
            if (generation == requestGeneration) publish("Не вдалося перевірити рекламні налаштування. Навчання доступне.")
        })
    }

    fun showPrivacyOptions(activity: Activity) {
        if (mutableState.value.busy || !mutableState.value.privacyOptionsRequired) return
        val generation = ++requestGeneration
        // Discard previously loaded ads while the user changes their choice.
        mutableState.value = mutableState.value.copy(canRequestAds = false, busy = true, error = null)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (generation == requestGeneration) publish(error?.let { "Не вдалося відкрити рекламні налаштування. Спробуйте ще раз." })
        }
    }

    private fun publish(error: String? = null) {
        mutableState.value = AdConsentState(
            canRequestAds = information.canRequestAds(),
            privacyOptionsRequired = information.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            error = error
        )
    }
}

fun canRequestAdvertisements(consent: Boolean, premium: Boolean?): Boolean = consent && premium == false
