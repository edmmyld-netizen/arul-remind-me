package com.arulsundaresan.arulremindme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arulsundaresan.arulremindme.BuildConfig
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.ui.AppViewModelProvider
import com.arulsundaresan.arulremindme.ui.components.ArulLogo
import com.arulsundaresan.arulremindme.ui.components.EmptyLine
import com.arulsundaresan.arulremindme.ui.components.EmptyState
import com.arulsundaresan.arulremindme.ui.components.ReminderCard
import com.arulsundaresan.arulremindme.ui.components.SectionHeader
import com.arulsundaresan.arulremindme.ui.permissions.ReminderPermissionCard
import com.arulsundaresan.arulremindme.voice.VoiceInputSheet
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenReliability: () -> Unit = {},
    onVoiceTranscript: (String) -> Unit = {},
    startWithVoice: Boolean = false,
    modifier: Modifier = Modifier,
    savedMessageRes: Int? = null,
    onSavedMessageShown: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Session 5A: the mic sheet. It only exists while true, so the recogniser cannot
    // outlive it.
    var showVoiceSheet by remember { mutableStateOf(startWithVoice) }

    // A save/update that happened on the editor screen is reported back through nav state.
    LaunchedEffect(savedMessageRes) {
        if (savedMessageRes != null) {
            viewModel.showSavedMessage(savedMessageRes)
            onSavedMessageShown()
        }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message, messageText) {
        val current = message ?: return@LaunchedEffect
        val text = messageText ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = if (current.undoReminderId != null) undoLabel else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && current.undoReminderId != null) {
            viewModel.undoDelete(current.undoReminderId)
        }
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    ArulLogo(modifier = Modifier.padding(start = 14.dp, end = 2.dp))
                },
                actions = {
                    IconButton(onClick = onOpenReliability) {
                        Icon(
                            imageVector = Icons.Outlined.HealthAndSafety,
                            contentDescription = stringResource(R.string.reliability_title)
                        )
                    }
                    IconButton(onClick = onOpenCompleted) {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = stringResource(R.string.home_open_completed)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Session 3: only renders when notifications or exact alarms are blocked.
            item { ReminderPermissionCard(onOpenReliability = onOpenReliability) }

            item { VoiceCard(onSpeak = { showVoiceSheet = true }) }

            if (BuildConfig.DEBUG) {
                item {
                    OutlinedButton(
                        onClick = viewModel::createTestReminder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(stringResource(R.string.debug_test_reminder))
                    }
                }
            }

            item {
                Button(
                    onClick = onAddReminder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_add_reminder),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                return@LazyColumn
            }

            if (uiState.hasNothingToShow) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_subtitle),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
                return@LazyColumn
            }

            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(
                    title = stringResource(R.string.home_today),
                    trailing = uiState.today.size.toString()
                )
            }
            if (uiState.today.isEmpty()) {
                item { EmptyLine(R.string.home_no_today) }
            } else {
                items(items = uiState.today, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        isOverdue = reminder.isOverdue(LocalDateTime.now()),
                        onEdit = { onEditReminder(reminder.id) },
                        onComplete = { viewModel.completeReminder(reminder.id) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                SectionHeader(
                    title = stringResource(R.string.home_upcoming),
                    trailing = uiState.upcoming.size.toString()
                )
            }
            if (uiState.upcoming.isEmpty()) {
                item { EmptyLine(R.string.home_no_upcoming) }
            } else {
                items(items = uiState.upcoming, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onEdit = { onEditReminder(reminder.id) },
                        onComplete = { viewModel.completeReminder(reminder.id) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }

        if (showVoiceSheet) {
            VoiceInputSheet(
                onDismiss = { showVoiceSheet = false },
                onTranscript = { text ->
                    showVoiceSheet = false
                    onVoiceTranscript(text)
                },
                onTypeInstead = {
                    showVoiceSheet = false
                    onAddReminder()
                }
            )
        }
    }
}

/**
 * Session 5A: the working microphone entry point. The sheet is what starts the recogniser,
 * and it only appears after this explicit tap.
 */
@Composable
private fun VoiceCard(onSpeak: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalIconButton(
                onClick = onSpeak,
                modifier = Modifier.size(76.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.voice_speak_reminder),
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(R.string.voice_speak_reminder),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.voice_speak_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}
