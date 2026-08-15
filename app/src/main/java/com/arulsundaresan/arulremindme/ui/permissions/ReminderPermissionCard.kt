package com.arulsundaresan.arulremindme.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.DisposableEffect
import com.arulsundaresan.arulremindme.R

/**
 * Small status strip on Home. It only appears when something is actually wrong, so a
 * correctly configured phone never sees it.
 *
 * Session 3 keeps this deliberately minimal — the full Settings screen is Session 5.
 */
@Composable
fun ReminderPermissionCard(
    modifier: Modifier = Modifier,
    onOpenReliability: () -> Unit = {}
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderPermissions.status(context)) }

    // Permissions are changed in Settings, i.e. outside this app, so the state is re-read
    // every time Home comes back to the foreground.
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

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { status = ReminderPermissions.status(context) }

    if (status.allGood) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                )
                Text(
                    text = stringResource(R.string.perm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            StatusLine(
                label = stringResource(R.string.perm_notifications),
                ok = status.notificationsEnabled
            )
            if (status.exactAlarmPermissionApplies) {
                StatusLine(
                    label = stringResource(R.string.perm_exact_alarm),
                    ok = status.exactAlarmsAllowed
                )
            }

            if (!status.notificationsEnabled) {
                Text(
                    text = stringResource(R.string.perm_notifications_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (status.exactAlarmPermissionApplies && !status.exactAlarmsAllowed) {
                Text(
                    text = stringResource(R.string.perm_exact_alarm_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (!status.notificationsEnabled) {
                    TextButton(
                        onClick = {
                            // On Android 13+ ask once in-app; otherwise (or once denied) the
                            // OS only allows the Settings route.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !ReminderPermissions.status(context).notificationsEnabled
                            ) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            context.startActivity(
                                ReminderPermissions.notificationSettingsIntent(context)
                            )
                        }
                    ) { Text(stringResource(R.string.perm_open_settings)) }
                }
                if (status.exactAlarmPermissionApplies && !status.exactAlarmsAllowed) {
                    TextButton(
                        onClick = {
                            ReminderPermissions.exactAlarmSettingsIntent(context)
                                ?.let { context.startActivity(it) }
                        }
                    ) { Text(stringResource(R.string.perm_open_alarm_settings)) }
                }
                // Session 4: the full status list, including battery optimisation.
                TextButton(onClick = onOpenReliability) {
                    Text(stringResource(R.string.reliability_more))
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(16.dp)
        )
        Text(
            text = label + ": " + stringResource(
                if (ok) R.string.perm_state_ok else R.string.perm_state_missing
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
