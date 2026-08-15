package com.arulsundaresan.arulremindme.voice

import android.content.Context

/**
 * The one voice preference this session needs. SharedPreferences rather than DataStore —
 * it is a single string and adding a dependency for it would not earn its keep.
 */
class VoiceSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: VoiceLanguage
        get() = VoiceLanguage.fromSettings(prefs.getString(KEY_LANGUAGE, null))
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.settingsValue).apply()
        }

    private companion object {
        const val PREFS_NAME = "arul_voice_settings"
        const val KEY_LANGUAGE = "voice_language"
    }
}
