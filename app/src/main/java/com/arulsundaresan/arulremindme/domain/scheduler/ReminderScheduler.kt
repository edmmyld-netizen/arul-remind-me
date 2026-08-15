package com.arulsundaresan.arulremindme.domain.scheduler

import com.arulsundaresan.arulremindme.domain.model.Reminder

/**
 * Alarm scheduling seam.
 *
 * Session 1 ships [NoOpReminderScheduler] only — no alarms are set and nothing pretends
 * otherwise. Session 3 will add an AlarmManagerReminderScheduler implementing this same
 * interface, and only [com.arulsundaresan.arulremindme.di.AppContainer] will change.
 */
interface ReminderScheduler {

    /**
     * Arm (or re-arm) the alarm for this reminder.
     *
     * @return true only when an *exact* alarm was actually armed. False means nothing was
     *   scheduled, or only an inexact fallback was — the caller records that honestly
     *   rather than letting the UI imply a guarantee the OS did not give.
     */
    suspend fun schedule(reminder: Reminder): Boolean

    /** Cancel any pending alarm for this reminder id. */
    suspend fun cancel(reminderId: Long)

    /** False when the OS has not granted the exact-alarm permission. */
    fun canScheduleExactAlarms(): Boolean
}

/**
 * No-op implementation. Session 3 replaced it in the live graph with
 * [com.arulsundaresan.arulremindme.alarm.AlarmManagerReminderScheduler]; this is kept for
 * tests and for any build that deliberately runs without alarms.
 */
class NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(reminder: Reminder): Boolean = false
    override suspend fun cancel(reminderId: Long) = Unit
    override fun canScheduleExactAlarms(): Boolean = false
}
