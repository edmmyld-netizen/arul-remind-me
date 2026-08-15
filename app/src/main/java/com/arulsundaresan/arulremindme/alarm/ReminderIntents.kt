package com.arulsundaresan.arulremindme.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.arulsundaresan.arulremindme.MainActivity
import com.arulsundaresan.arulremindme.ui.alert.ReminderAlertActivity

/**
 * One place that knows the shape of every Intent this feature sends, so the receiver, the
 * notification and the full-screen Activity cannot drift apart.
 */
object ReminderIntents {

    const val ACTION_FIRE = "com.arulsundaresan.arulremindme.action.FIRE"
    const val ACTION_DONE = "com.arulsundaresan.arulremindme.action.DONE"
    const val ACTION_SNOOZE = "com.arulsundaresan.arulremindme.action.SNOOZE"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

    /** Set by the notification's "Open" action so MainActivity can deep-link later. */
    const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"

    private val mutabilityFlag: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Nothing downstream fills these in, so immutable is both correct and required
            // from Android 12 onwards.
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

    private fun broadcastFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag

    // ---- the alarm itself --------------------------------------------------

    fun fireIntent(context: Context, reminderId: Long): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
            // Distinct data keeps the extras from being collapsed by Intent.filterEquals().
            data = android.net.Uri.parse("arulremindme://reminder/$reminderId/fire")
        }

    fun firePendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.FIRE),
            fireIntent(context, reminderId),
            broadcastFlags()
        )

    /**
     * Same request code and same Intent shape as [firePendingIntent] — that identity is what
     * makes AlarmManager.cancel() actually find the alarm.
     */
    fun cancelPendingIntent(context: Context, reminderId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.FIRE),
            fireIntent(context, reminderId),
            PendingIntent.FLAG_NO_CREATE or mutabilityFlag
        )

    // ---- notification actions ----------------------------------------------

    fun donePendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.DONE),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                data = android.net.Uri.parse("arulremindme://reminder/$reminderId/done")
            },
            broadcastFlags()
        )

    fun snoozePendingIntent(
        context: Context,
        reminderId: Long,
        option: SnoozeOption = SnoozeOption.DEFAULT
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.SNOOZE) + option.minutes,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_SNOOZE_MINUTES, option.minutes)
                data = android.net.Uri.parse(
                    "arulremindme://reminder/$reminderId/snooze/${option.minutes}"
                )
            },
            broadcastFlags()
        )

    /** "Open" action and notification tap — brings the app up. */
    fun openAppPendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.OPEN),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )

    // ---- full-screen alert -------------------------------------------------

    fun alertIntent(context: Context, reminderId: Long): Intent =
        Intent(context, ReminderAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

    fun alertPendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            AlarmIds.requestCode(reminderId, AlarmSlot.ALERT),
            alertIntent(context, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )
}
