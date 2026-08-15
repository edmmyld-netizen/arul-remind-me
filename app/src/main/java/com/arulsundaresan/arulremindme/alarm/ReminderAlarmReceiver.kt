package com.arulsundaresan.arulremindme.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arulsundaresan.arulremindme.ArulRemindMeApp
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import com.arulsundaresan.arulremindme.notification.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Receives the alarm and the notification actions.
 *
 * A BroadcastReceiver is used rather than a service because it runs even when no Activity
 * exists and the process was not running — which is the whole Session 3 requirement.
 *
 * `goAsync()` holds the broadcast open while Room is read on a background thread. The work
 * is deliberately short; anything longer would need WorkManager.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderIntents.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) {
            Log.w(TAG, "Ignoring intent with no reminder id: ${intent.action}")
            return
        }

        val app = context.applicationContext as? ArulRemindMeApp ?: return
        val repository = app.container.reminderRepository
        val notifier = ReminderNotifier(context.applicationContext)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    ReminderIntents.ACTION_FIRE -> fire(repository, notifier, reminderId)
                    ReminderIntents.ACTION_DONE -> done(repository, notifier, reminderId)
                    ReminderIntents.ACTION_SNOOZE -> snooze(
                        repository = repository,
                        notifier = notifier,
                        reminderId = reminderId,
                        minutes = intent.getIntExtra(
                            ReminderIntents.EXTRA_SNOOZE_MINUTES,
                            SnoozeOption.DEFAULT.minutes
                        )
                    )

                    else -> Log.w(TAG, "Unknown action ${intent.action}")
                }
            } catch (e: Exception) {
                // A crash here would happen with no UI on screen and no way for the user to
                // recover, so failures are logged and swallowed.
                Log.e(TAG, "Failed handling ${intent.action} for #$reminderId", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * An alarm is a message from the past. Between arming it and it landing, the reminder
     * may have been completed, deleted, edited or snoozed again — so the row is re-read and
     * re-checked instead of trusted.
     */
    private suspend fun fire(
        repository: ReminderRepository,
        notifier: ReminderNotifier,
        reminderId: Long
    ) {
        val reminder = repository.getById(reminderId)
        val now = System.currentTimeMillis()
        if (!AlarmScheduleRules.shouldNotify(reminder, now)) {
            Log.i(TAG, "Alarm for #$reminderId is stale, not notifying")
            return
        }
        requireNotNull(reminder)
        val posted = notifier.notify(reminder)
        if (!posted) {
            Log.w(TAG, "Notification for #$reminderId suppressed (permission or channel off)")
        }

        // Session 4: keep the chain alive. The series moves on here — not when the user
        // taps Done — so a recurring reminder still fires tomorrow even if this one is
        // ignored completely.
        when (val outcome = RecurrenceRules.onFire(reminder, LocalDateTime.now(reminder.zoneId))) {
            is RecurrenceRules.FireOutcome.NotifyAndAdvance ->
                repository.advanceRecurring(reminderId, outcome.nextScheduledAt)

            // A snooze alarm landing: clear the marker so the regular occurrence takes over
            // again. Re-arming happens inside advanceRecurring/schedule as usual.
            RecurrenceRules.FireOutcome.NotifyAndClearSnooze ->
                repository.advanceRecurring(reminderId, reminder.scheduledAt)

            RecurrenceRules.FireOutcome.NotifyOnly -> Unit
        }
    }

    private suspend fun done(
        repository: ReminderRepository,
        notifier: ReminderNotifier,
        reminderId: Long
    ) {
        // completeOccurrence knows the difference: a one-time reminder is completed, a
        // recurring one has already been advanced by the firing alarm and is only dismissed.
        repository.completeOccurrence(reminderId)
        notifier.cancel(reminderId)
    }

    private suspend fun snooze(
        repository: ReminderRepository,
        notifier: ReminderNotifier,
        reminderId: Long,
        minutes: Int
    ) {
        val option = SnoozeOption.fromMinutes(minutes)
        val untilMillis = AlarmScheduleRules.snoozeUntilMillis(System.currentTimeMillis(), option)
        val zone = repository.getById(reminderId)?.zoneId ?: ZoneId.systemDefault()
        val until = LocalDateTime.ofInstant(Instant.ofEpochMilli(untilMillis), zone)

        notifier.cancel(reminderId)
        // Writes snoozedUntilEpochMillis and re-arms exactly one alarm for the new instant.
        repository.snooze(reminderId, until)
    }

    private companion object {
        const val TAG = "ArulAlarmReceiver"
    }
}
