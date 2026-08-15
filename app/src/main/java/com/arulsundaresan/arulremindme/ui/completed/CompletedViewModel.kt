package com.arulsundaresan.arulremindme.ui.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arulsundaresan.arulremindme.R
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

data class CompletedUiState(
    val isLoading: Boolean = true,
    val reminders: List<Reminder> = emptyList()
)

class CompletedViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    val uiState: StateFlow<CompletedUiState> = repository.observeCompleted()
        .map { CompletedUiState(isLoading = false, reminders = it) }
        .catch { emit(CompletedUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CompletedUiState()
        )

    fun restore(id: Long) {
        viewModelScope.launch {
            repository.setCompleted(id, completed = false)
            _message.value = R.string.completed_restored
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _message.value = R.string.msg_deleted
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
