package com.arulsundaresan.arulremindme.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.scheduler.ReminderScheduler

/**
 * The real scheduler. Replaces NoOpReminderScheduler in the live graph.
 *
 * Uses `AlarmManager.setAlarmClock` when the OS allows exact alarms: it is the one mechanism
 * that is exempt from Doze batching and survives the app being closed, which is exactly the
 * Session 3 requirement. There is no Handler, Timer or coroutine delay anywhere in this
 * path — none of those survive process death.
 */
class AlarmManagerReminderScheduler(
    private val context: Context
) : ReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    override suspend fun schedule(reminder: Reminder): Boolean {
        val manager = alarmManager ?: return false

        when (val decision = AlarmScheduleRules.decide(reminder, System.currentTimeMillis())) {
            is ScheduleDecision.Skip -> {
                // Cancel anything left over, e.g. after an edit that moved a reminder into
                // the past or after it was completed.
                cancel(reminder.id)
                Log.i(TAG, "No alarm for #${reminder.id}: ${decision.reason}")
                return false
            }

            is ScheduleDecision.Arm -> {
                val pendingIntent = ReminderIntents.firePendingIntent(context, reminder.id)
                // Re-arming replaces the old alarm because the request code is stable.
                return try {
                    if (canScheduleExactAlarms()) {
                        manager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(
                                decision.triggerAtMillis,
                                ReminderIntents.openAppPendingIntent(context, reminder.id)
                            ),
                            pendingIntent
                        )
                        true
                    } else {
                        // Honest fallback: still Doze-aware, but the OS may delay it. The
                        // false return is what makes the UI say "needs permission" instead
                        // of implying an exact alarm was armed.
                        manager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            decision.triggerAtMillis,
                            pendingIntent
                        )
                        Log.w(TAG, "Inexact alarm for #${reminder.id}: exact alarms not permitted")
                        false
                    }
                } catch (e: SecurityException) {
                    // The permission can be revoked between the check and the call.
                    Log.w(TAG, "Exact alarm refused for #${reminder.id}", e)
                    runCatching {
                        manager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            decision.triggerAtMillis,
                            pendingIntent
                        )
                    }
                    false
                }
            }
        }
    }

    override suspend fun cancel(reminderId: Long) {
        val manager = alarmManager ?: return
        ReminderIntents.cancelPendingIntent(context, reminderId)?.let { pending ->
            manager.cancel(pending)
            pending.cancel()
        }
    }

    override fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    private companion object {
        const val TAG = "ArulAlarmScheduler"
    }
}
