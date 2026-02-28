package com.fitgpt.app.data

import android.content.Context
import android.content.SharedPreferences
import com.fitgpt.app.data.model.UserPreferences

class PreferencesManager(context: Context, private val userId: String? = null) {

    private val prefName = if (userId != null) "fitgpt_prefs_$userId" else "fitgpt_prefs"
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun savePreferences(preferences: UserPreferences) {
        prefs.edit()
            .putString(KEY_BODY_TYPE, preferences.bodyType)
            .putString(KEY_STYLE, preferences.stylePreference)
            .putInt(KEY_COMFORT, preferences.comfortPreference)
            .putStringSet(KEY_SEASONS, preferences.preferredSeasons.toSet())
            .putBoolean(KEY_ACCESSIBILITY, preferences.accessibilityModeEnabled)
            .apply()
    }

    fun loadPreferences(): UserPreferences {
        return UserPreferences(
            bodyType = prefs.getString(KEY_BODY_TYPE, "Average") ?: "Average",
            stylePreference = prefs.getString(KEY_STYLE, "Casual") ?: "Casual",
            comfortPreference = prefs.getInt(KEY_COMFORT, 3),
            preferredSeasons = prefs.getStringSet(KEY_SEASONS, null)
                ?.toList()
                ?.ifEmpty { null }
                ?: listOf("Spring", "Summer", "Fall", "Winter"),
            accessibilityModeEnabled = prefs.getBoolean(KEY_ACCESSIBILITY, false)
        )
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_BODY_TYPE = "body_type"
        const val KEY_STYLE = "style_preference"
        const val KEY_COMFORT = "comfort_preference"
        const val KEY_SEASONS = "preferred_seasons"
        const val KEY_ACCESSIBILITY = "accessibility_mode"
    }
}
