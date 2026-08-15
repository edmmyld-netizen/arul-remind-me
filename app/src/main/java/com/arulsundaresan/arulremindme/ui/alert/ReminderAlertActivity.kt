package com.arulsundaresan.arulremindme.ui.alert

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arulsundaresan.arulremindme.ArulRemindMeApp
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.alarm.AlarmScheduleRules
import com.arulsundaresan.arulremindme.alarm.ReminderIntents
import com.arulsundaresan.arulremindme.alarm.SnoozeOption
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.notification.ReminderNotifier
import com.arulsundaresan.arulremindme.ui.components.ArulLogo
import com.arulsundaresan.arulremindme.ui.theme.ArulRemindMeTheme
import com.arulsundaresan.arulremindme.util.DateTimeFormatters
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The alarm-style screen launched by the notification's full-screen intent.
 *
 * It uses only the documented `setShowWhenLocked` / `setTurnScreenOn` APIs, which ask the
 * system to show this one screen over the keyguard. It does **not** dismiss or bypass the
 * lock screen — the device stays locked and the system decides what is shown.
 *
 * Android may also decline the full-screen intent entirely (it is increasingly restricted).
 * That is fine: the lock-screen notification remains the primary alert and this screen is an
 * enhancement, never the only path.
 */
class ReminderAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverKeyguard()

        val reminderId = intent.getLongExtra(ReminderIntents.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) {
            finish()
            return
        }

        val container = (application as ArulRemindMeApp).container
        val repository = container.reminderRepository
        val notifier = ReminderNotifier(applicationContext)

        setContent {
            ArulRemindMeTheme {
                val reminderFlow = remember { repository.observeById(reminderId) }
                val reminder by reminderFlow.collectAsStateWithLifecycle(initialValue = null)

                // If the reminder is completed or deleted from anywhere else (a notification
                // action, another screen), this Activity closes itself.
                LaunchedEffect(reminder?.isCompleted, reminder?.isDeleted) {
                    val current = reminder
                    if (current != null && (current.isCompleted || current.isDeleted)) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val current = reminder
                    if (current != null) {
                        AlertContent(
                            reminder = current,
                            onDone = {
                                lifecycleScope.launch {
                                    repository.completeOccurrence(reminderId)
                                    notifier.cancel(reminderId)
                                    finish()
                                }
                            },
                            onSnooze = { option ->
                                lifecycleScope.launch {
                                    val untilMillis = AlarmScheduleRules.snoozeUntilMillis(
                                        System.currentTimeMillis(),
                                        option
                                    )
                                    val zone = current.zoneId
                                    notifier.cancel(reminderId)
                                    repository.snooze(
                                        reminderId,
                                        LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(untilMillis),
                                            zone
                                        )
                                    )
                                    finish()
                                }
                            },
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Documented APIs only. On API 27+ these replace the deprecated window flags; below that
     * the flags are the supported route.
     */
    @Suppress("DEPRECATION")
    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Asks the system to dismiss the keyguard only if it is *not* secure. On a
        // PIN/pattern/biometric device this does nothing and the phone stays locked.
        getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
    }
}

@Composable
private fun AlertContent(
    reminder: Reminder,
    onDone: () -> Unit,
    onSnooze: (SnoozeOption) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ArulLogo(size = 26.dp)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = reminder.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (!reminder.description.isNullOrBlank()) {
            Text(
                text = reminder.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        AlertMeta(
            Icons.Filled.CalendarMonth,
            DateTimeFormatters.longDate(reminder.scheduledDate)
        )
        Spacer(Modifier.height(6.dp))
        AlertMeta(
            Icons.Filled.Schedule,
            DateTimeFormatters.time(reminder.scheduledTime)
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(R.string.alert_done),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnoozeOption.entries.forEach { option ->
                OutlinedButton(
                    onClick = { onSnooze(option) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(
                            R.string.alert_snooze_minutes,
                            option.minutes
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.alert_dismiss))
        }
    }
}

@Composable
private fun AlertMeta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
