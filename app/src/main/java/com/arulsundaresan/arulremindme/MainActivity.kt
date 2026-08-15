package com.arulsundaresan.arulremindme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.arulsundaresan.arulremindme.ui.navigation.ArulNavHost
import com.arulsundaresan.arulremindme.ui.theme.ArulRemindMeTheme

/**
 * The app's only Activity. All screens are Compose destinations inside [ArulNavHost].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Fired by the launcher shortcut and by the App Actions capability. */
        const val ACTION_VOICE_REMINDER = "com.arulsundaresan.arulremindme.action.VOICE_REMINDER"

        /** Set when an Assistant supplies the sentence itself. */
        const val EXTRA_VOICE_TEXT = "voiceText"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Session 5A: launched from the launcher shortcut or an Assistant App Action.
        val startWithVoice = intent?.action == ACTION_VOICE_REMINDER
        val spokenText = intent?.getStringExtra(EXTRA_VOICE_TEXT)?.takeIf { it.isNotBlank() }

        setContent {
            ArulRemindMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArulNavHost(
                        startWithVoice = startWithVoice,
                        initialVoiceText = spokenText
                    )
                }
            }
        }
    }
}
