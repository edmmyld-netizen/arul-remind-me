package com.arulsundaresan.arulremindme.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arulsundaresan.arulremindme.R
import kotlinx.coroutines.flow.catch

/** Where the sheet currently is. */
private sealed interface SheetStep {
    data object RequestingPermission : SheetStep
    data object Listening : SheetStep
    data class Result(val transcript: String) : SheetStep
    data class Error(val error: VoiceError) : SheetStep
}

/**
 * The voice entry sheet, shared by Home and the editor.
 *
 * The microphone starts only after this sheet is opened by an explicit tap, and stops the
 * moment the sheet closes — the recogniser flow is scoped to this composable, so there is
 * nothing left listening when it goes away.
 *
 * @param onTranscript the recognised text, handed straight to the existing Session 2 parser
 *   by the caller. No parsing happens in here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputSheet(
    onDismiss: () -> Unit,
    onTranscript: (String) -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val service = remember { SpeechRecognitionService(context) }
    val settings = remember { VoiceSettings(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var language by remember { mutableStateOf(settings.language) }
    var step by remember {
        mutableStateOf<SheetStep>(
            if (service.hasMicrophonePermission()) SheetStep.Listening
            else SheetStep.RequestingPermission
        )
    }
    var partial by remember { mutableStateOf("") }
    // Bumped to restart listening for "Speak again" without recreating the sheet.
    var attempt by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        step = if (granted) SheetStep.Listening else SheetStep.Error(VoiceError.PERMISSION_DENIED)
    }

    // RECORD_AUDIO is requested here — on first mic tap — never at app startup.
    LaunchedEffect(Unit) {
        if (!service.hasMicrophonePermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // One listening session per attempt. Leaving this composition cancels the flow, which
    // stops the recogniser and releases the microphone.
    LaunchedEffect(step, attempt, language) {
        if (step !is SheetStep.Listening) return@LaunchedEffect
        partial = ""
        service.listen(language)
            .catch { step = SheetStep.Error(VoiceError.UNKNOWN) }
            .collect { event ->
                when (event) {
                    VoiceRecognitionEvent.Listening, VoiceRecognitionEvent.Processing -> Unit
                    is VoiceRecognitionEvent.PartialTranscript -> partial = event.text
                    is VoiceRecognitionEvent.Transcript -> step = SheetStep.Result(event.text)
                    is VoiceRecognitionEvent.Failed -> step = SheetStep.Error(event.error)
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val current = step) {
                SheetStep.RequestingPermission -> ListeningBody(
                    listening = false,
                    partial = "",
                    label = stringResource(R.string.voice_permission_checking)
                )

                SheetStep.Listening -> {
                    ListeningBody(
                        listening = true,
                        partial = partial,
                        label = stringResource(R.string.voice_listening)
                    )
                    LanguageChips(selected = language) {
                        language = it
                        settings.language = it
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.voice_cancel))
                    }
                }

                is SheetStep.Result -> ResultBody(
                    transcript = current.transcript,
                    onContinue = { onTranscript(current.transcript) },
                    onSpeakAgain = {
                        attempt += 1
                        step = SheetStep.Listening
                    },
                    onTypeInstead = onTypeInstead
                )

                is SheetStep.Error -> ErrorBody(
                    error = current.error,
                    canRetry = current.error != VoiceError.PERMISSION_DENIED &&
                        current.error != VoiceError.NO_MICROPHONE,
                    onRetry = {
                        attempt += 1
                        step = SheetStep.Listening
                    },
                    onTypeInstead = onTypeInstead
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ListeningBody(listening: Boolean, partial: String, label: String) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.16f else 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(96.dp)
            .scale(pulse)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
    }

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 18.dp)
    )

    Text(
        text = partial.ifBlank { stringResource(R.string.voice_listening_hint) },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun ResultBody(
    transcript: String,
    onContinue: () -> Unit,
    onSpeakAgain: () -> Unit,
    onTypeInstead: () -> Unit
) {
    Text(
        text = stringResource(R.string.voice_you_said),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp)
        )
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(R.string.voice_continue))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onSpeakAgain, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(R.string.voice_speak_again))
        }
        OutlinedButton(onClick = onTypeInstead, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(R.string.voice_type_instead))
        }
    }
}

@Composable
private fun ErrorBody(
    error: VoiceError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onTypeInstead: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(88.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp)
        )
    }

    Text(
        text = stringResource(error.messageRes()),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 18.dp, bottom = 14.dp)
    )

    if (error == VoiceError.PERMISSION_DENIED) {
        VoicePermissionHelp()
    }

    if (canRetry) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.voice_try_again))
        }
        Spacer(Modifier.height(6.dp))
    }

    OutlinedButton(
        onClick = onTypeInstead,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(R.string.voice_type_instead))
    }
}

@Composable
private fun VoicePermissionHelp() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(
                    com.arulsundaresan.arulremindme.ui.permissions.ReminderPermissions
                        .appDetailsIntent(context)
                )
            }
        }
    ) { Text(stringResource(R.string.voice_open_settings)) }
}

@Composable
private fun LanguageChips(selected: VoiceLanguage, onSelect: (VoiceLanguage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        VoiceLanguage.entries.forEach { option ->
            TextButton(onClick = { onSelect(option) }) {
                Text(
                    text = stringResource(option.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (option == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

internal fun VoiceLanguage.labelRes(): Int = when (this) {
    VoiceLanguage.AUTO -> R.string.voice_lang_auto
    VoiceLanguage.TAMIL -> R.string.voice_lang_tamil
    VoiceLanguage.ENGLISH -> R.string.voice_lang_english
}

private fun VoiceError.messageRes(): Int = when (this) {
    VoiceError.PERMISSION_DENIED -> R.string.voice_error_permission
    VoiceError.NO_MICROPHONE -> R.string.voice_error_no_mic
    VoiceError.RECOGNIZER_UNAVAILABLE -> R.string.voice_error_unavailable
    VoiceError.NETWORK_UNAVAILABLE -> R.string.voice_error_network
    VoiceError.NO_SPEECH_HEARD, VoiceError.EMPTY_RESULT -> R.string.voice_error_not_understood
    VoiceError.TIMEOUT -> R.string.voice_error_timeout
    VoiceError.BUSY -> R.string.voice_error_busy
    VoiceError.UNKNOWN -> R.string.voice_error_not_understood
}
