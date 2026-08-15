package com.arulsundaresan.arulremindme.ui.permissions

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.arulsundaresan.arulremindme.notification.ReminderNotifier

/** What the OS currently allows, as one snapshot the UI can render. */
data class ReminderPermissionStatus(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    /** False on Android 11 and below, where exact alarms need no user grant at all. */
    val exactAlarmPermissionApplies: Boolean,
    /**
     * Session 4. True when Android has been told to leave this app alone in Doze. Exact
     * alarms already work without it, but OEM power managers are far less likely to delay
     * or kill the app when it is unrestricted.
     */
    val batteryUnrestricted: Boolean = false
) {
    /** The two that actually block reminders. Battery is advisory, not a hard failure. */
    val allGood: Boolean get() = notificationsEnabled && exactAlarmsAllowed
    val everythingOptimal: Boolean get() = allGood && batteryUnrestricted
}

/**
 * Reads permission state and hands back the official Settings intents.
 *
 * Everything here is a documented API. Nothing tries to grant itself a permission or work
 * around a denial — when the OS says no, the UI says so plainly.
 */
object ReminderPermissions {

    fun status(context: Context): ReminderPermissionStatus {
        val notifier = ReminderNotifier(context)
        val applies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
        } else {
            // Below Android 12 exact alarms need no user grant at all.
            true
        }
        val battery = context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

        return ReminderPermissionStatus(
            notificationsEnabled = notifier.hasPostNotificationsPermission() &&
                notifier.areNotificationsEnabled(),
            exactAlarmsAllowed = exact,
            exactAlarmPermissionApplies = applies,
            batteryUnrestricted = battery
        )
    }

    /** Opens this app's notification settings so the user can switch them back on. */
    fun notificationSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }

    /**
     * The official "Alarms & reminders" screen. Returns null below Android 12, where the
     * permission does not exist.
     */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
    }

    /**
     * Session 4: the system battery-optimisation list.
     *
     * This is the documented list screen, not `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     * — that one needs a Play-restricted permission and pops a dialog most apps are not
     * allowed to show. The user makes the change themselves, which is the honest route.
     */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Falls back to this app's details page if the list screen is missing on a device. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
