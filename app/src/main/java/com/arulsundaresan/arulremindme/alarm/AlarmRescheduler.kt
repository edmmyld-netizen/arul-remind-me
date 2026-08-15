package com.arulsundaresan.arulremindme.alarm

import android.util.Log
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import java.time.LocalDateTime

/** What one rescheduling sweep did. Returned so the caller can log it honestly. */
data class RescheduleReport(
    val rescheduled: Int = 0,
    val advanced: Int = 0,
    val leftOverdue: Int = 0,
    val ignored: Int = 0
) {
    val total: Int get() = rescheduled + advanced + leftOverdue + ignored
}

/**
 * Re-arms every pending alarm from the database.
 *
 * AlarmManager keeps nothing across a reboot, and `isAlarmScheduled` in the database is only
 * a record of what *was* asked for — it is deliberately not trusted here. The database rows
 * are the source of truth and every live reminder gets its alarm rebuilt from them.
 *
 * Request codes are derived from the reminder id, so re-arming replaces the previous
 * PendingIntent instead of adding a second one. Running this sweep twice is harmless.
 */
class AlarmRescheduler(
    private val repository: ReminderRepository,
    private val scheduler: com.arulsundaresan.arulremindme.domain.scheduler.ReminderScheduler
) {

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()): RescheduleReport {
        // Epoch 0 so nothing is filtered out by time here — RecurrenceRules decides.
        val pending = repository.pendingFrom(0L)
        var report = RescheduleReport()

        for (reminder in pending) {
            report = when (val action = RecurrenceRules.rebootAction(reminder, now)) {
                is RecurrenceRules.RebootAction.Reschedule -> {
                    scheduler.cancel(reminder.id)
                    scheduler.schedule(reminder)
                    report.copy(rescheduled = report.rescheduled + 1)
                }

                is RecurrenceRules.RebootAction.AdvanceAndReschedule -> {
                    // Writes the new occurrence and re-arms in one place.
                    repository.advanceRecurring(reminder.id, action.scheduledAt)
                    report.copy(advanced = report.advanced + 1)
                }

                RecurrenceRules.RebootAction.LeaveOverdue -> {
                    // No alarm is armed for a past instant. The reminder stays pending and
                    // shows in Today with the existing overdue chip.
                    scheduler.cancel(reminder.id)
                    report.copy(leftOverdue = report.leftOverdue + 1)
                }

                RecurrenceRules.RebootAction.Ignore -> {
                    scheduler.cancel(reminder.id)
                    report.copy(ignored = report.ignored + 1)
                }
            }
        }

        Log.i(
            TAG,
            "Rescheduled ${report.rescheduled}, advanced ${report.advanced}, " +
                "overdue ${report.leftOverdue}, ignored ${report.ignored}"
        )
        return report
    }

    private companion object {
        const val TAG = "ArulRescheduler"
    }
}
