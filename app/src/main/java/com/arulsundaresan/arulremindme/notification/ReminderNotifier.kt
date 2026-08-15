package com.arulsundaresan.arulremindme.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.arulsundaresan.arulremindme.R
import com.arulsundaresan.arulremindme.alarm.AlarmIds
import com.arulsundaresan.arulremindme.alarm.ReminderIntents
import com.arulsundaresan.arulremindme.alarm.SnoozeOption
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.util.DateTimeFormatters

/**
 * Builds and posts the reminder notification.
 *
 * Sound and vibration come from the notification channel rather than from the app playing
 * audio itself — that is what keeps Do Not Disturb, per-channel settings and the user's
 * volume choices working instead of being fought.
 */
class ReminderNotifier(private val context: Context) {

    /**
     * Safe to call repeatedly: createNotificationChannel is a no-op when the channel already
     * exists, so importance and sound the user has since changed are never reset.
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_description)
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            enableLights(true)
            setBypassDnd(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts the reminder. Returns false when the OS will not show it, so the caller can log
     * that instead of assuming the user was alerted.
     */
    fun notify(reminder: Reminder): Boolean {
        ensureChannel()
        if (!hasPostNotificationsPermission()) return false

        val id = AlarmIds.notificationId(reminder.id)
        val whenText = DateTimeFormatters.shortDate(reminder.scheduledDate) +
            " · " + DateTimeFormatters.time(reminder.scheduledTime)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(whenText)
            .setSubText(context.getString(R.string.app_name))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        listOfNotNull(reminder.title, reminder.description, whenText)
                            .joinToString("\n")
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // The whole point is being readable on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(ReminderIntents.openAppPendingIntent(context, reminder.id))
            .addAction(
                0,
                context.getString(R.string.notif_action_done),
                ReminderIntents.donePendingIntent(context, reminder.id)
            )
            .addAction(
                0,
                context.getString(
                    R.string.notif_action_snooze_minutes,
                    SnoozeOption.DEFAULT.minutes
                ),
                ReminderIntents.snoozePendingIntent(context, reminder.id, SnoozeOption.DEFAULT)
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_open),
                ReminderIntents.openAppPendingIntent(context, reminder.id)
            )

        // Pre-O devices have no channel, so sound/vibration go on the notification itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (reminder.soundEnabled) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            if (reminder.vibrationEnabled) {
                builder.setVibrate(VIBRATION_PATTERN)
            }
        } else if (!reminder.soundEnabled && !reminder.vibrationEnabled) {
            builder.setSilent(true)
        }

        // Full-screen intent: the alarm-style screen. Android decides whether to honour it;
        // when it does not, the heads-up/lock-screen notification above still fires.
        builder.setFullScreenIntent(
            ReminderIntents.alertPendingIntent(context, reminder.id),
            true
        )

        return try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(AlarmIds.notificationId(reminderId))
    }

    companion object {
        const val CHANNEL_ID = "arul_reminders"
        private val VIBRATION_PATTERN = longArrayOf(0L, 500L, 300L, 500L, 300L, 700L)
    }
}
