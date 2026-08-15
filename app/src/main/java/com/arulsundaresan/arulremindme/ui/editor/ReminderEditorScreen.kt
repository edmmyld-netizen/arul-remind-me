package com.arulsundaresan.arulremindme.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.ui.AppViewModelProvider
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import com.arulsundaresan.arulremindme.nlp.AmbiguousTime
import com.arulsundaresan.arulremindme.ui.components.ReminderConfirmationDialog
import com.arulsundaresan.arulremindme.util.DateTimeFormatters
import com.arulsundaresan.arulremindme.voice.VoiceInputSheet
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(
    onNavigateUp: () -> Unit,
    onSaved: (SaveResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReminderEditorViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showVoiceSheet by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.saveResult) {
        val result = uiState.saveResult
        if (result != null) {
            viewModel.onSaveResultHandled()
            onSaved(result)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) {
                            stringResource(R.string.editor_edit_title)
                        } else {
                            stringResource(R.string.editor_add_title)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ---- Session 2: natural-language entry (added, nothing replaced) ----
            NaturalLanguageCard(
                value = uiState.naturalInput,
                onValueChange = viewModel::onNaturalInputChange,
                onContinue = viewModel::onParseNaturalInput,
                canParse = uiState.canParse,
                feedback = uiState.parseFeedback,
                onSpeak = { showVoiceSheet = true },
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.editor_or),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ---- Session 1 manual entry, unchanged ----
            Text(
                text = stringResource(R.string.editor_manual_label),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.editor_what_hint)) },
                isError = uiState.showTitleError,
                supportingText = if (uiState.showTitleError) {
                    { Text(stringResource(R.string.editor_error_empty_title)) }
                } else {
                    null
                },
                minLines = 2,
                shape = MaterialTheme.shapes.small
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_note_label)) },
                placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                shape = MaterialTheme.shapes.small
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.editor_when_label),
                style = MaterialTheme.typography.titleMedium
            )

            PickerRow(
                icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                label = stringResource(R.string.editor_pick_date),
                value = DateTimeFormatters.longDate(uiState.date),
                onClick = { showDatePicker = true }
            )
            PickerRow(
                icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                label = stringResource(R.string.editor_pick_time),
                value = DateTimeFormatters.time(uiState.time),
                onClick = { showTimePicker = true }
            )

            // ---- Session 4: repeat ----
            RepeatSelector(
                selected = uiState.repeatMode,
                date = uiState.date,
                onSelect = viewModel::onRepeatModeChange
            )

            if (uiState.isInPast) {
                Text(
                    text = stringResource(R.string.editor_warn_past),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = viewModel::onSaveRequested,
                enabled = uiState.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(R.string.editor_save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.editor_cancel))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showVoiceSheet) {
        VoiceInputSheet(
            onDismiss = { showVoiceSheet = false },
            onTranscript = { text ->
                showVoiceSheet = false
                viewModel.onVoiceTranscript(text)
            },
            onTypeInstead = { showVoiceSheet = false }
        )
    }

    if (showDatePicker) {
        ReminderDatePicker(
            initial = uiState.date,
            onDismiss = { showDatePicker = false },
            onPicked = {
                viewModel.onDateChange(it)
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        ReminderTimePicker(
            initial = uiState.time,
            onDismiss = { showTimePicker = false },
            onPicked = {
                viewModel.onTimeChange(it)
                showTimePicker = false
            }
        )
    }

    val pendingMeridiem = uiState.pendingMeridiem
    if (pendingMeridiem != null) {
        MeridiemClarificationDialog(
            ambiguous = pendingMeridiem,
            onPick = viewModel::onMeridiemChosen,
            onDismiss = viewModel::onMeridiemDismissed
        )
    }

    if (uiState.showConfirmation) {
        ReminderConfirmationDialog(
            title = uiState.title.trim(),
            description = uiState.description.trim().ifBlank { null },
            scheduledAt = uiState.scheduledAt,
            onConfirm = viewModel::onConfirmSave,
            onEdit = viewModel::onConfirmationDismissed
        )
    }
}

@Composable
private fun PickerRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.size(24.dp)) { icon() }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit
) {
    // DatePicker works in UTC-midnight millis; convert both ways explicitly rather than
    // guessing with the device offset.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPicked(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    } else {
                        onDismiss()
                    }
                }
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dialog_time_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.editor_cancel))
                    }
                    TextButton(
                        onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }
    }
}

/**
 * Session 2 entry point: one sentence in, a parsed reminder out. The manual fields below it
 * stay fully usable — a partly-understood sentence just pre-fills them.
 */
@Composable
private fun NaturalLanguageCard(
    value: String,
    onValueChange: (String) -> Unit,
    onContinue: () -> Unit,
    canParse: Boolean,
    feedback: ParseFeedback?,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                )
                Text(
                    text = stringResource(R.string.editor_what_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                // Session 5A: "speak again" without leaving the editor.
                FilledTonalIconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.voice_speak_reminder),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                placeholder = { Text(stringResource(R.string.editor_nl_hint)) },
                minLines = 2,
                shape = MaterialTheme.shapes.small
            )

            if (feedback != null) {
                Text(
                    text = stringResource(feedback.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            FilledTonalButton(
                onClick = onContinue,
                enabled = canParse,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.editor_continue),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** Maps a parse outcome onto the message shown under the sentence field. */
private fun ParseFeedback.messageRes(): Int = when (this) {
    ParseFeedback.ASK_WHEN -> R.string.parse_ask_when
    ParseFeedback.ASK_DATE -> R.string.parse_ask_date
    ParseFeedback.ASK_TIME -> R.string.parse_ask_time
    ParseFeedback.EMPTY_INPUT -> R.string.parse_error_empty_input
    ParseFeedback.NO_REMINDER_TEXT -> R.string.parse_error_no_text
}

/**
 * "5 மணி" could be either half of the day. The parser refuses to guess, so the user picks.
 */
@Composable
private fun MeridiemClarificationDialog(
    ambiguous: AmbiguousTime,
    onPick: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val amLabel = DateTimeFormatters.time(ambiguous.asAm())
    val pmLabel = DateTimeFormatters.time(ambiguous.asPm())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.clarify_time_title, ambiguous.hour12.toString()),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = stringResource(R.string.clarify_time_question, amLabel, pmLabel),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = { onPick(true) }) { Text(pmLabel) }
        },
        dismissButton = {
            TextButton(onClick = { onPick(false) }) { Text(amLabel) }
        }
    )
}

/**
 * Session 4 repeat picker. Deliberately four chips and one explanatory line rather than a
 * separate weekday/day-of-month screen: the weekday for "every week" and the day for
 * "every month" both come from the date already chosen above, so there is nothing extra
 * for the user to keep in sync.
 */
@Composable
private fun RepeatSelector(
    selected: RepeatMode,
    date: LocalDate,
    onSelect: (RepeatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.editor_repeat_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RepeatMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = {
                        Text(
                            text = stringResource(mode.labelRes()),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val detail = when (selected) {
            RepeatMode.NONE -> null
            RepeatMode.DAILY -> stringResource(R.string.editor_repeat_daily_detail)
            RepeatMode.WEEKLY -> stringResource(
                R.string.editor_repeat_weekly_detail,
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            )

            RepeatMode.MONTHLY -> stringResource(
                R.string.editor_repeat_monthly_detail,
                date.dayOfMonth
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

private fun RepeatMode.labelRes(): Int = when (this) {
    RepeatMode.NONE -> R.string.repeat_none
    RepeatMode.DAILY -> R.string.repeat_daily
    RepeatMode.WEEKLY -> R.string.repeat_weekly
    RepeatMode.MONTHLY -> R.string.repeat_monthly
}
