package com.arulsundaresan.arulremindme.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arulsundaresan.arulremindme.ArulRemindMeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Rebuilds every alarm after the events that silently wipe them.
 *
 * - `BOOT_COMPLETED` — AlarmManager keeps nothing across a restart. (Direct boot is not
 *   supported: the Room database is credential-encrypted, so nothing can be read before the
 *   user's first unlock. Reminders are re-armed at that point instead.)
 * - `MY_PACKAGE_REPLACED` — an app update cancels the app's alarms.
 * - `TIMEZONE_CHANGED` / `TIME_SET` — a reminder stores its own `zoneId`, so the wall-clock
 *   time the user chose stays fixed while the underlying instant moves; the alarm has to be
 *   re-armed against the new instant.
 *
 * All of it delegates to [AlarmRescheduler] and the existing
 * [com.arulsundaresan.arulremindme.alarm.AlarmManagerReminderScheduler]. There is no second
 * scheduler implementation.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) {
            Log.w(TAG, "Ignoring unexpected action: $action")
            return
        }

        val app = context.applicationContext as? ArulRemindMeApp ?: return
        val container = app.container

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val report = AlarmRescheduler(
                    repository = container.reminderRepository,
                    scheduler = container.reminderScheduler
                ).rescheduleAll()
                Log.i(TAG, "$action -> re-armed ${report.total} reminder(s)")
            } catch (e: Exception) {
                // This runs with no UI on screen; a crash here would be invisible and
                // unrecoverable, so it is logged and swallowed.
                Log.e(TAG, "Failed to reschedule after $action", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ArulBootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED
        )
    }
}
