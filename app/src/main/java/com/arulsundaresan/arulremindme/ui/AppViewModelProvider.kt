package com.arulsundaresan.arulremindme.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arulsundaresan.arulremindme.ArulRemindMeApp
import com.arulsundaresan.arulremindme.ui.completed.CompletedViewModel
import com.arulsundaresan.arulremindme.ui.editor.ReminderEditorViewModel
import com.arulsundaresan.arulremindme.ui.home.HomeViewModel

/**
 * One factory for every ViewModel in the app. Keeps construction off the Composables and
 * gives each ViewModel its dependencies explicitly.
 */
object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(arulApplication().container.reminderRepository)
        }
        initializer {
            CompletedViewModel(arulApplication().container.reminderRepository)
        }
        initializer {
            ReminderEditorViewModel(
                repository = arulApplication().container.reminderRepository,
                parser = arulApplication().container.reminderParser,
                savedStateHandle = createSavedStateHandle()
            )
        }
    }
}

private fun CreationExtras.arulApplication(): ArulRemindMeApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ArulRemindMeApp
