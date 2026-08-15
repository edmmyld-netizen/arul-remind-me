package com.arulsundaresan.arulremindme.alarm

import kotlin.math.absoluteValue

/** Every distinct PendingIntent this app creates for one reminder. */
enum class AlarmSlot(internal val slot: Int) {
    /** The alarm itself, delivered to ReminderAlarmReceiver. */
    FIRE(0),

    /** "Done" notification action. */
    DONE(1),

    /** "Snooze" notification action. */
    SNOOZE(2),

    /** "Open" notification action / content tap. */
    OPEN(3),

    /** Full-screen intent to ReminderAlertActivity. */
    ALERT(4)
}

/**
 * Stable request codes and notification ids derived from the reminder id.
 *
 * Stability is the whole point: a PendingIntent is only "the same" to AlarmManager if its
 * request code matches, so a random code per call would make cancel() silently do nothing
 * and leave duplicate alarms behind after every edit.
 *
 * Pure Kotlin — no Android types — so this is covered by ordinary JVM unit tests.
 */
object AlarmIds {

    /** Keeps room for 5 slots per reminder inside a positive Int. */
    private const val ID_SPACE = 100_000_000L

    internal fun base(reminderId: Long): Int =
        (reminderId.absoluteValue % ID_SPACE).toInt()

    /** Request code for one reminder + one slot. Deterministic and collision-free. */
    fun requestCode(reminderId: Long, slot: AlarmSlot): Int =
        base(reminderId) * AlarmSlot.entries.size + slot.slot

    /** Notification id. Shares the FIRE code so a reminder always owns one notification. */
    fun notificationId(reminderId: Long): Int = requestCode(reminderId, AlarmSlot.FIRE)
}
