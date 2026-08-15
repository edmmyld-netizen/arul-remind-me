package com.arulsundaresan.arulremindme.ui.reliability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.ui.permissions.ReminderPermissions
import com.arulsundaresan.arulremindme.voice.SpeechRecognitionService
import com.arulsundaresan.arulremindme.voice.VoiceLanguage
import com.arulsundaresan.arulremindme.voice.VoiceSettings
import com.arulsundaresan.arulremindme.voice.labelRes
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

/**
 * Session 4: "Reminder Reliability".
 *
 * It reports what Android currently allows and links to the official Settings screens.
 * It makes no claim to work around OEM power management — the app cannot, and saying so
 * plainly is more useful than a button that quietly does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderPermissions.status(context)) }
    val speech = remember { SpeechRecognitionService(context) }
    val voiceSettings = remember { VoiceSettings(context) }
    var voiceLanguage by remember { mutableStateOf(voiceSettings.language) }

    // Permissions change outside the app, so the state is re-read on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = ReminderPermissions.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.reliability_title),
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusCard(
                    label = stringResource(R.string.perm_notifications),
                    valueRes = if (status.notificationsEnabled) {
                        R.string.perm_state_ok
                    } else {
                        R.string.reliability_state_disabled
                    },
                    ok = status.notificationsEnabled,
                    explanation = stringResource(R.string.reliability_notifications_help),
                    actionLabel = stringResource(R.string.perm_open_settings),
                    onAction = {
                        context.safeStart(ReminderPermissions.notificationSettingsIntent(context))
                    }
                )
            }

            if (status.exactAlarmPermissionApplies) {
                item {
                    StatusCard(
                        label = stringResource(R.string.perm_exact_alarm),
                        valueRes = if (status.exactAlarmsAllowed) {
                            R.string.perm_state_ok
                        } else {
                            R.string.perm_state_missing
                        },
                        ok = status.exactAlarmsAllowed,
                        explanation = stringResource(R.string.reliability_exact_alarm_help),
                        actionLabel = stringResource(R.string.perm_open_alarm_settings),
                        onAction = {
                            ReminderPermissions.exactAlarmSettingsIntent(context)
                                ?.let { context.safeStart(it) }
                        }
                    )
                }
            }

            item {
                StatusCard(
                    label = stringResource(R.string.reliability_battery),
                    valueRes = if (status.batteryUnrestricted) {
                        R.string.reliability_state_unrestricted
                    } else {
                        R.string.reliability_state_optimized
                    },
                    ok = status.batteryUnrestricted,
                    explanation = stringResource(R.string.reliability_battery_help),
                    actionLabel = stringResource(R.string.reliability_open_battery),
                    onAction = {
                        context.safeStart(
                            ReminderPermissions.batteryOptimizationSettingsIntent(),
                            fallback = ReminderPermissions.appDetailsIntent(context)
                        )
                    }
                )
            }

            // Session 5A: the minimum voice settings.
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.voice_settings_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(
                                if (speech.isRecognitionAvailable()) {
                                    R.string.voice_settings_available
                                } else {
                                    R.string.voice_settings_unavailable
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text(
                            text = stringResource(R.string.voice_settings_language),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VoiceLanguage.entries.forEach { option ->
                                FilterChip(
                                    selected = voiceLanguage == option,
                                    onClick = {
                                        voiceLanguage = option
                                        voiceSettings.language = option
                                    },
                                    label = { Text(stringResource(option.labelRes())) },
                                    shape = MaterialTheme.shapes.small,
                                    colors = FilterChipDefaults.filterChipColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.voice_settings_auto_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.reliability_oem_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text(
                            text = stringResource(R.string.reliability_oem_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.reliability_reboot_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    label: String,
    valueRes: Int,
    ok: Boolean,
    explanation: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (ok) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(valueRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Some OEM ROMs drop these Settings screens; failing softly beats crashing. */
private fun Context.safeStart(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        if (fallback != null) {
            runCatching { startActivity(fallback) }
        }
    }
}
