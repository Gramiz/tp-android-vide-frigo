package fr.mathieu.videfrigo

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import fr.mathieu.videfrigo.data.Preferences
import kotlinx.coroutines.flow.first

class UsageAnalytics(context: Context, private val preferences: Preferences) {
    private val sdk by lazy { if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAnalytics.getInstance(context) else null }

    fun applyConsent(allowed: Boolean) {
        val status = if (allowed) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        sdk?.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status,
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
        ))
        sdk?.setAnalyticsCollectionEnabled(allowed)
    }

    suspend fun firstFoodAdded() {
        if (sdk == null || !preferences.settings.first().analytics) return
        applyConsent(true)
        if (preferences.claimActivation()) sdk?.logEvent("first_food_added", null)
    }

    suspend fun screenViewed(name: String): Boolean {
        if (sdk == null || !preferences.settings.first().analytics) return false
        applyConsent(true)
        sdk?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })
        return true
    }

    suspend fun opened(source: String, available: Boolean) {
        if (sdk == null || !preferences.settings.first().analytics) return
        applyConsent(true)
        when (source) {
            "reminder" -> sdk?.logEvent("reminder_opened", null)
            "widget" -> sdk?.logEvent("widget_food_opened", Bundle().apply { putString("result", if (available) "success" else "unavailable") })
        }
    }
}
