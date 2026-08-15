package com.arulsundaresan.arulremindme.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import com.arulsundaresan.arulremindme.nlp.AmbiguousTime
import com.arulsundaresan.arulremindme.nlp.MissingInfo
import com.arulsundaresan.arulremindme.nlp.ParseFailure
import com.arulsundaresan.arulremindme.nlp.ParsedReminderInput
import com.arulsundaresan.arulremindme.nlp.ParserResult
import com.arulsundaresan.arulremindme.nlp.ReminderParser
import com.arulsundaresan.arulremindme.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ReminderEditorUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = defaultTime(),
    val showTitleError: Boolean = false,
    val showConfirmation: Boolean = false,
    val saveResult: SaveResult? = null,
    // ---- Session 2: natural language ----
    /** The free-form sentence the user typed into the natural-language field. */
    val naturalInput: String = "",
    /** Message under the natural-language field after a parse attempt. */
    val parseFeedback: ParseFeedback? = null,
    /** Non-null while the AM/PM clarification dialog is open. */
    val pendingMeridiem: AmbiguousTime? = null,
    /** Kept verbatim for `Reminder.originalInput` when the reminder came from a sentence. */
    val originalInput: String? = null,
    /** True once a parse filled the fields below, so the UI can point the user at them. */
    val scheduleFromParser: Boolean = false,
    /** Session 4: repeat selection. Weekday and day-of-month come from [date]. */
    val repeatMode: RepeatMode = RepeatMode.NONE
) {
    val scheduledAt: LocalDateTime get() = LocalDateTime.of(date, time)
    val isInPast: Boolean get() = scheduledAt.isBefore(LocalDateTime.now())
    val canContinue: Boolean get() = title.isNotBlank()
    val canParse: Boolean get() = naturalInput.isNotBlank()
}

enum class SaveResult { CREATED, UPDATED }

/**
 * What to tell the user after a parse attempt. The ViewModel picks the case; the Compose
 * layer picks the string resource. No parsing logic and no user-facing text lives here.
 */
enum class ParseFeedback {
    /** Neither a date nor a time was found. */
    ASK_WHEN,

    /** A time was found but no date. */
    ASK_DATE,

    /** A date was found but no time. */
    ASK_TIME,

    /** The sentence was empty. */
    EMPTY_INPUT,

    /** A schedule was found but nothing was left to be reminded about. */
    NO_REMINDER_TEXT
}

/** Default suggestion: the next full hour, so the picker never opens on a past time. */
private fun defaultTime(): LocalTime =
    LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)

class ReminderEditorViewModel(
    private val repository: ReminderRepository,
    private val parser: ReminderParser,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val reminderId: Long =
        savedStateHandle.get<Long>(Routes.EDITOR_ARG_ID) ?: Routes.NEW_REMINDER_ID

    /** Session 5A: text that arrived from speech recognition, if any. */
    private val voiceText: String? =
        savedStateHandle.get<String>(Routes.EDITOR_ARG_VOICE)?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(
        ReminderEditorUiState(
            isEditMode = reminderId != Routes.NEW_REMINDER_ID,
            isLoading = reminderId != Routes.NEW_REMINDER_ID
        )
    )
    val uiState: StateFlow<ReminderEditorUiState> = _uiState.asStateFlow()

    /** Kept so an edit does not lose the original creation metadata. */
    private var loaded: Reminder? = null

    init {
        // A spoken sentence goes straight into the same natural-language field a typed one
        // would, and through the same parser — voice adds no second path.
        if (voiceText != null) {
            _uiState.update { it.copy(naturalInput = voiceText) }
            onParseNaturalInput()
        }
        if (reminderId != Routes.NEW_REMINDER_ID) {
            viewModelScope.launch {
                val existing = repository.getById(reminderId)
                loaded = existing
                _uiState.update { state ->
                    if (existing == null) {
                        state.copy(isLoading = false)
                    } else {
                        state.copy(
                            isLoading = false,
                            title = existing.title,
                            description = existing.description.orEmpty(),
                            date = existing.scheduledDate,
                            time = existing.scheduledTime,
                            repeatMode = existing.repeatMode
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, showTitleError = false) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onDateChange(value: LocalDate) {
        _uiState.update { it.copy(date = value) }
    }

    fun onTimeChange(value: LocalTime) {
        _uiState.update { it.copy(time = value) }
    }

    /**
     * Session 4. The weekday for WEEKLY and the day of month for MONTHLY are taken from the
     * chosen date, so no extra pickers are needed — changing the date changes both.
     */
    fun onRepeatModeChange(value: RepeatMode) {
        _uiState.update { it.copy(repeatMode = value) }
    }

    // ---- Session 2: natural-language entry ---------------------------------

    /** Session 5A: "Speak again" from inside the editor. */
    fun onVoiceTranscript(text: String) {
        _uiState.update { it.copy(naturalInput = text, parseFeedback = null) }
        onParseNaturalInput()
    }

    fun onNaturalInputChange(value: String) {
        _uiState.update { it.copy(naturalInput = value, parseFeedback = null) }
    }

    /**
     * Runs the sentence through [ReminderParser] and maps the outcome onto UI state.
     *
     * Whatever the parser did understand is applied to the same fields the manual pickers
     * write to, so the two entry paths converge before anything is saved.
     */
    fun onParseNaturalInput() {
        val input = _uiState.value.naturalInput
        when (val result = parser.parse(input)) {
            is ParserResult.Complete -> {
                applyParsed(result.parsed)
                _uiState.update { it.copy(parseFeedback = null, showConfirmation = true) }
            }

            is ParserResult.Incomplete -> {
                applyParsed(result.parsed)
                when (result.missing) {
                    MissingInfo.TIME_MERIDIEM -> _uiState.update {
                        it.copy(
                            parseFeedback = null,
                            pendingMeridiem = result.parsed.ambiguousTime
                        )
                    }

                    MissingInfo.DATE_AND_TIME ->
                        _uiState.update { it.copy(parseFeedback = ParseFeedback.ASK_WHEN) }

                    MissingInfo.DATE ->
                        _uiState.update { it.copy(parseFeedback = ParseFeedback.ASK_DATE) }

                    MissingInfo.TIME ->
                        _uiState.update { it.copy(parseFeedback = ParseFeedback.ASK_TIME) }
                }
            }

            is ParserResult.Failure -> {
                val feedback = when (result.reason) {
                    ParseFailure.EMPTY_INPUT -> ParseFeedback.EMPTY_INPUT
                    ParseFailure.NO_REMINDER_TEXT -> ParseFeedback.NO_REMINDER_TEXT
                }
                _uiState.update { it.copy(parseFeedback = feedback) }
            }
        }
    }

    /** The user picked AM or PM in the clarification dialog. */
    fun onMeridiemChosen(isPm: Boolean) {
        val pending = _uiState.value.pendingMeridiem ?: return
        val resolved = pending.resolve(isPm)
        _uiState.update { state ->
            state.copy(
                time = resolved,
                pendingMeridiem = null,
                // Everything is known now unless the sentence never carried a date.
                showConfirmation = state.scheduleFromParser,
                parseFeedback = if (state.scheduleFromParser) null else ParseFeedback.ASK_DATE
            )
        }
    }

    fun onMeridiemDismissed() {
        _uiState.update { it.copy(pendingMeridiem = null) }
    }

    fun onParseFeedbackShown() {
        _uiState.update { it.copy(parseFeedback = null) }
    }

    /**
     * Copies whatever the parser understood into the shared editor fields. A field the
     * parser could not fill keeps the value already on screen, so a half-understood
     * sentence still leaves the manual pickers usable.
     */
    private fun applyParsed(parsed: ParsedReminderInput) {
        _uiState.update { state ->
            state.copy(
                title = parsed.reminderText.ifBlank { state.title },
                date = parsed.date ?: state.date,
                time = parsed.time ?: state.time,
                originalInput = parsed.originalInput,
                repeatMode = parsed.repeatMode,
                showTitleError = false,
                scheduleFromParser = parsed.date != null
            )
        }
    }

    /** Step 1 of saving: validate, then ask "இந்த Reminder சரியா?". */
    fun onSaveRequested() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(showTitleError = true) }
            return
        }
        _uiState.update { it.copy(showConfirmation = true) }
    }

    fun onConfirmationDismissed() {
        _uiState.update { it.copy(showConfirmation = false) }
    }

    /** Step 2: the user tapped Save on the confirmation card. */
    fun onConfirmSave() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        viewModelScope.launch {
            val existing = loaded
            if (existing != null) {
                repository.update(
                    existing.copy(
                        title = state.title.trim(),
                        description = state.description.trim().ifBlank { null },
                        scheduledAt = state.scheduledAt,
                        repeatMode = state.repeatMode,
                        zoneId = ZoneId.systemDefault()
                    )
                )
                _uiState.update {
                    it.copy(showConfirmation = false, saveResult = SaveResult.UPDATED)
                }
            } else {
                repository.add(
                    Reminder(
                        title = state.title.trim(),
                        description = state.description.trim().ifBlank { null },
                        originalInput = state.originalInput ?: state.title.trim(),
                        scheduledAt = state.scheduledAt,
                        repeatMode = state.repeatMode,
                        zoneId = ZoneId.systemDefault()
                    )
                )
                _uiState.update {
                    it.copy(showConfirmation = false, saveResult = SaveResult.CREATED)
                }
            }
        }
    }

    fun onSaveResultHandled() {
        _uiState.update { it.copy(saveResult = null) }
    }
}
