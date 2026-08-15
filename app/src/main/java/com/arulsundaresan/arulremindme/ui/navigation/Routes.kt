package com.arulsundaresan.arulremindme.ui.navigation

object Routes {
    const val HOME = "home"
    const val COMPLETED = "completed"
    const val RELIABILITY = "reliability"

    /** Shared add/edit destination. reminderId = NEW_REMINDER_ID means "create". */
    const val EDITOR_ARG_ID = "reminderId"

    /** Session 5A: a recognised sentence handed to the editor's natural-language field. */
    const val EDITOR_ARG_VOICE = "voiceText"
    const val EDITOR =
        "editor?$EDITOR_ARG_ID={$EDITOR_ARG_ID}&$EDITOR_ARG_VOICE={$EDITOR_ARG_VOICE}"

    const val NEW_REMINDER_ID = -1L

    fun editor(reminderId: Long = NEW_REMINDER_ID, voiceText: String? = null): String {
        val encoded = voiceText?.let { android.net.Uri.encode(it) }.orEmpty()
        return "editor?$EDITOR_ARG_ID=$reminderId&$EDITOR_ARG_VOICE=$encoded"
    }
}
