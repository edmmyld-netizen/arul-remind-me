package com.arulsundaresan.arulremindme.voice

/**
 * Which language Android's speech recogniser is asked for.
 *
 * Pure Kotlin — no Android types — so the tag mapping is unit-testable.
 *
 * A word on [AUTO]: Android's `SpeechRecognizer` recognises **one** language per session.
 * There is no supported "detect Tamil or English automatically" mode, so AUTO simply hands
 * the recogniser the device's own locale and lets it do what it normally does. It is not a
 * bilingual mode and the app does not pretend otherwise.
 */
enum class VoiceLanguage(val settingsValue: String) {

    /** Follow the device locale. */
    AUTO("auto"),

    TAMIL("ta"),

    ENGLISH("en");

    /**
     * BCP-47 tag handed to the recogniser, or null for [AUTO] — null means "no
     * EXTRA_LANGUAGE", which is how the platform is told to use the device default.
     */
    fun languageTag(): String? = when (this) {
        AUTO -> null
        TAMIL -> TAMIL_INDIA
        ENGLISH -> ENGLISH_INDIA
    }

    companion object {
        const val TAMIL_INDIA = "ta-IN"
        const val ENGLISH_INDIA = "en-IN"

        fun fromSettings(value: String?): VoiceLanguage =
            entries.firstOrNull { it.settingsValue.equals(value, ignoreCase = true) } ?: AUTO
    }
}
