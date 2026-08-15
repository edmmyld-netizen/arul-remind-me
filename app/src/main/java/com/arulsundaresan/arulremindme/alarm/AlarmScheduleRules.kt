package com.arulsundaresan.arulremindme.alarm

import com.arulsundaresan.arulremindme.domain.model.Reminder
import java.time.Duration

/** Snooze lengths offered by the notification and the full-screen alert. */
enum class SnoozeOption(val minutes: Int) {
    FIVE(5),
    TEN(10),
    THIRTY(30);

    companion object {
        /** What the single notification "Snooze" action uses. */
        val DEFAULT: SnoozeOption = TEN

        fun fromMinutes(minutes: Int): SnoozeOption =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}

/** What the scheduler should do with one reminder. */
sealed interface ScheduleDecision {

    /** Arm an alarm for this instant. */
    data class Arm(val triggerAtMillis: Long) : ScheduleDecision

    /** Nothing to arm, with the reason kept so callers can log or explain it. */
    data class Skip(val reason: SkipReason) : ScheduleDecision
}

enum class SkipReason {
    ALREADY_COMPLETED,
    DELETED,

    /** The trigger instant has already passed — never hand AlarmManager a past time. */
    IN_THE_PAST,

    /** The row has no id yet, so no stable request code can be derived. */
    NOT_PERSISTED
}

/**
 * The decision logic behind scheduling, kept free of Android types so it can be unit-tested
 * on the JVM. [AlarmManagerReminderScheduler] does the platform calls; this decides whether
 * there is anything to call about.
 */
object AlarmScheduleRules {

    /**
     * Alarms within this window of "now" are treated as already past. Without it, a reminder
     * saved for 30 seconds from now could be armed and then immediately missed while the
     * database write finishes.
     */
    val MINIMUM_LEAD: Duration = Duration.ofSeconds(5)

    fun decide(reminder: Reminder, nowMillis: Long): ScheduleDecision = when {
        reminder.id <= 0L -> ScheduleDecision.Skip(SkipReason.NOT_PERSISTED)
        reminder.isDeleted -> ScheduleDecision.Skip(SkipReason.DELETED)
        reminder.isCompleted -> ScheduleDecision.Skip(SkipReason.ALREADY_COMPLETED)
        else -> {
            val trigger = reminder.triggerAtEpochMillis(nowMillis)
            if (trigger <= nowMillis + MINIMUM_LEAD.toMillis()) {
                ScheduleDecision.Skip(SkipReason.IN_THE_PAST)
            } else {
                ScheduleDecision.Arm(trigger)
            }
        }
    }

    /**
     * Whether the receiver should actually notify when an alarm arrives.
     *
     * An alarm is a message from the past: by the time it lands the reminder may have been
     * completed, deleted or snoozed again. The receiver re-reads the row and asks this.
     */
    fun shouldNotify(reminder: Reminder?, nowMillis: Long): Boolean {
        if (reminder == null) return false
        if (reminder.isDeleted || reminder.isCompleted) return false
        // A snooze that still lies ahead means this alarm is a stale one from before it.
        val trigger = reminder.triggerAtEpochMillis(nowMillis)
        return trigger <= nowMillis + MINIMUM_LEAD.toMillis()
    }

    /** The instant a snooze should fire, measured from when the user tapped it. */
    fun snoozeUntilMillis(nowMillis: Long, option: SnoozeOption): Long =
        nowMillis + Duration.ofMinutes(option.minutes.toLong()).toMillis()
}
