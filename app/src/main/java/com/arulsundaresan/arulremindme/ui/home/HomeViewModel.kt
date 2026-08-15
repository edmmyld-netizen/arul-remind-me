package com.arulsundaresan.arulremindme.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.BuildConfig
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: List<Reminder> = emptyList(),
    val upcoming: List<Reminder> = emptyList()
) {
    val hasNothingToShow: Boolean
        get() = !isLoading && today.isEmpty() && upcoming.isEmpty()
}

/** A one-shot snackbar message, optionally with an Undo target. */
data class UiMessage(
    @param:StringRes val textRes: Int,
    val undoReminderId: Long? = null
)

class HomeViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    val uiState: StateFlow<HomeUiState> = repository.observeActive()
        .map { reminders -> reminders.split() }
        .catch { emit(HomeUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeUiState()
        )

    private fun List<Reminder>.split(
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now()
    ): HomeUiState {
        // Overdue items stay in the Today bucket so they can't silently disappear.
        val (todayBucket, upcomingBucket) = partition {
            it.scheduledDate <= today || it.scheduledAt.isBefore(now)
        }
        return HomeUiState(
            isLoading = false,
            today = todayBucket,
            upcoming = upcomingBucket
        )
    }

    fun completeReminder(id: Long) {
        viewModelScope.launch {
            repository.setCompleted(id, completed = true)
            _message.value = UiMessage(R.string.msg_completed)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _message.value = UiMessage(R.string.msg_deleted, undoReminderId = id)
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restoreDeleted(id) }
    }

    fun showSavedMessage(@StringRes textRes: Int) {
        _message.value = UiMessage(textRes)
    }

    /**
     * Debug builds only: creates a real reminder one minute out through the ordinary
     * repository path, so it goes through exactly the same AlarmManager code a normal
     * reminder does. Guarded by BuildConfig.DEBUG so it can never reach a release build.
     */
    fun createTestReminder() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            repository.add(
                Reminder(
                    title = "Test reminder",
                    description = "Session 3 alarm test",
                    scheduledAt = LocalDateTime.now().plusMinutes(1)
                )
            )
            _message.value = UiMessage(R.string.debug_test_scheduled)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
