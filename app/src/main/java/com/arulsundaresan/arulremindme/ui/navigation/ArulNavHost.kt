package com.arulsundaresan.arulremindme.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.ui.completed.CompletedScreen
import com.arulsundaresan.arulremindme.ui.editor.ReminderEditorScreen
import com.arulsundaresan.arulremindme.ui.editor.SaveResult
import com.arulsundaresan.arulremindme.ui.home.HomeScreen
import com.arulsundaresan.arulremindme.ui.reliability.ReliabilityScreen

/** Key used to hand the "saved" confirmation back to Home after the editor pops. */
private const val SAVED_MESSAGE_KEY = "saved_message_res"

/**
 * Single Activity + Compose Navigation. Every screen is a composable destination; no extra
 * Activities are needed for Session 1 (Session 3 will add one full-screen alarm Activity).
 */
@Composable
fun ArulNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** True when launched from the shortcut / App Action: open the mic straight away. */
    startWithVoice: Boolean = false,
    /** Set when an Assistant already supplied the sentence, so no mic is needed. */
    initialVoiceText: String? = null
) {
    // An Assistant-supplied sentence skips speech recognition entirely and goes straight
    // to the editor, which runs the same parser a typed sentence would.
    LaunchedEffect(initialVoiceText) {
        if (!initialVoiceText.isNullOrBlank()) {
            navController.navigate(Routes.editor(voiceText = initialVoiceText))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {

        composable(Routes.HOME) { entry ->
            val savedMessageRes by entry.savedStateHandle
                .getStateFlow<Int?>(SAVED_MESSAGE_KEY, null)
                .collectAsStateWithLifecycle()

            HomeScreen(
                onAddReminder = { navController.navigate(Routes.editor()) },
                onVoiceTranscript = { text ->
                    // Speech -> text -> the existing editor, which runs the Session 2
                    // parser and shows the existing confirmation card.
                    navController.navigate(Routes.editor(voiceText = text))
                },
                onEditReminder = { id -> navController.navigate(Routes.editor(id)) },
                onOpenCompleted = { navController.navigate(Routes.COMPLETED) },
                onOpenReliability = { navController.navigate(Routes.RELIABILITY) },
                startWithVoice = startWithVoice && initialVoiceText.isNullOrBlank(),
                savedMessageRes = savedMessageRes,
                onSavedMessageShown = { entry.savedStateHandle[SAVED_MESSAGE_KEY] = null }
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.EDITOR_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.NEW_REMINDER_ID
                },
                navArgument(Routes.EDITOR_ARG_VOICE) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) {
            ReminderEditorScreen(
                onNavigateUp = { navController.popBackStack() },
                onSaved = { result ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            SAVED_MESSAGE_KEY,
                            when (result) {
                                SaveResult.CREATED -> R.string.confirm_saved
                                SaveResult.UPDATED -> R.string.confirm_updated
                            }
                        )
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.COMPLETED) {
            CompletedScreen(onNavigateUp = { navController.popBackStack() })
        }

        // Session 4: reminder reliability / battery guidance.
        composable(Routes.RELIABILITY) {
            ReliabilityScreen(onNavigateUp = { navController.popBackStack() })
        }
    }
}
