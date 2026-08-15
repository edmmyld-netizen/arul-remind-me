package com.arulsundaresan.arulremindme.alarm

import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Recurrence maths and the decisions that go with it. Pure Kotlin and `java.time` only, so
 * every rule below is covered by ordinary JVM unit tests — no string date arithmetic
 * anywhere.
 *
 * Weekly and monthly recurrences need no extra schema: the weekday comes from the
 * reminder's own `scheduledAt`, and so does the day of the month. "Every Monday 10 AM" is
 * simply a reminder whose `scheduledAt` falls on a Monday at 10 AM with `repeatMode=WEEKLY`.
 */
object RecurrenceRules {

    /**
     * The next occurrence strictly after [after].
     *
     * @param anchor the reminder's own scheduled time — supplies the time of day, the
     *   weekday for WEEKLY and the day of month for MONTHLY.
     * @return null for a non-recurring reminder.
     */
    fun nextOccurrence(
        anchor: LocalDateTime,
        repeatMode: RepeatMode,
        repeatInterval: Int = 1,
        after: LocalDateTime
    ): LocalDateTime? {
        val step = repeatInterval.coerceAtLeast(1)
        return when (repeatMode) {
            RepeatMode.NONE -> null
            RepeatMode.DAILY -> advance(anchor, after) { it.plusDays(step.toLong()) }
            RepeatMode.WEEKLY -> advance(anchor, after) { it.plusWeeks(step.toLong()) }
            RepeatMode.MONTHLY -> nextMonthly(anchor, step, after)
        }
    }

    private inline fun advance(
        anchor: LocalDateTime,
        after: LocalDateTime,
        step: (LocalDateTime) -> LocalDateTime
    ): LocalDateTime {
        var next = anchor
        // Walk forward from the anchor rather than from "now", so a reminder that was
        // missed for a week still lands on the right weekday and time of day.
        while (!next.isAfter(after)) {
            next = step(next)
        }
        return next
    }

    /**
     * Month-end policy: the day of month is taken from the anchor and **clamped** to the
     * length of the target month, so a reminder on the 31st fires on the 30th in November
     * and on the 28th (or 29th) in February. The anchor is never rewritten, so the 31st
     * returns in the next month that has one.
     */
    private fun nextMonthly(
        anchor: LocalDateTime,
        step: Int,
        after: LocalDateTime
    ): LocalDateTime {
        val dayOfMonth = anchor.dayOfMonth
        val time = anchor.toLocalTime()
        var month = YearMonth.from(anchor)
        var candidate = atClampedDay(month, dayOfMonth, time)
        while (!candidate.isAfter(after)) {
            month = month.plusMonths(step.toLong())
            candidate = atClampedDay(month, dayOfMonth, time)
        }
        return candidate
    }

    private fun atClampedDay(
        month: YearMonth,
        dayOfMonth: Int,
        time: java.time.LocalTime
    ): LocalDateTime {
        val day = dayOfMonth.coerceAtMost(month.lengthOfMonth())
        return LocalDateTime.of(LocalDate.of(month.year, month.month, day), time)
    }

    /** Convenience for the common case: the next occurrence after a reminder's own time. */
    fun nextOccurrence(reminder: Reminder, after: LocalDateTime): LocalDateTime? =
        nextOccurrence(
            anchor = reminder.scheduledAt,
            repeatMode = reminder.repeatMode,
            repeatInterval = reminder.repeatInterval,
            after = after
        )

    // ---- what happens when an alarm fires ----------------------------------

    /** Decision for the receiver once it has confirmed the reminder is still live. */
    sealed interface FireOutcome {

        /** One-time reminder: notify and leave it alone; the user marks it Done. */
        data object NotifyOnly : FireOutcome

        /**
         * Recurring reminder: notify, then move the schedule on so the chain continues
         * even if the user never touches the notification.
         */
        data class NotifyAndAdvance(val nextScheduledAt: LocalDateTime) : FireOutcome

        /**
         * A snooze alarm landing. The occurrence was already advanced when the regular
         * alarm fired, so only the snooze marker is cleared.
         */
        data object NotifyAndClearSnooze : FireOutcome
    }

    /**
     * Distinguishing a snooze alarm from a regular one is what stops a daily 8 AM reminder
     * from drifting to 8:10 after one snooze: [snoozedUntil] is only set while a snooze is
     * outstanding, and the regular occurrence is advanced exactly once, on the first fire.
     */
    fun onFire(reminder: Reminder, now: LocalDateTime): FireOutcome = when {
        reminder.snoozedUntil != null -> FireOutcome.NotifyAndClearSnooze
        reminder.repeatMode == RepeatMode.NONE -> FireOutcome.NotifyOnly
        else -> {
            val next = nextOccurrence(reminder, after = reminder.scheduledAt)
            if (next == null) FireOutcome.NotifyOnly else FireOutcome.NotifyAndAdvance(next)
        }
    }

    // ---- what happens on Done ----------------------------------------------

    sealed interface DoneOutcome {

        /** One-time reminder: the existing Session 1 completion path. */
        data object MarkCompleted : DoneOutcome

        /** Recurring: skip this occurrence, keep the series alive. */
        data class AdvanceToNext(val nextScheduledAt: LocalDateTime) : DoneOutcome

        /**
         * Recurring, tapped from the notification after the regular alarm already advanced
         * the series. Nothing to change — just clear the notification.
         */
        data object DismissOnly : DoneOutcome
    }

    /**
     * Done pressed in the reminder list. For a recurring reminder this means "skip this
     * one", so the series moves to the following occurrence.
     */
    fun onDoneFromList(reminder: Reminder, now: LocalDateTime): DoneOutcome {
        if (reminder.repeatMode == RepeatMode.NONE) return DoneOutcome.MarkCompleted
        val from = maxOf(reminder.scheduledAt, now)
        val next = nextOccurrence(reminder, after = from)
        return if (next == null) DoneOutcome.MarkCompleted else DoneOutcome.AdvanceToNext(next)
    }

    /**
     * Done pressed on the notification or the full-screen alert. The regular alarm has
     * already advanced a recurring series, so this only dismisses.
     */
    fun onDoneFromAlert(reminder: Reminder): DoneOutcome =
        if (reminder.repeatMode == RepeatMode.NONE) {
            DoneOutcome.MarkCompleted
        } else {
            DoneOutcome.DismissOnly
        }

    // ---- reboot ------------------------------------------------------------

    /** What the boot receiver should do with one stored reminder. */
    sealed interface RebootAction {

        /** Re-arm for this instant. */
        data class Reschedule(val scheduledAt: LocalDateTime) : RebootAction

        /**
         * A recurring reminder whose occurrence passed while the phone was off: move the
         * series forward to the next future occurrence and arm that.
         */
        data class AdvanceAndReschedule(val scheduledAt: LocalDateTime) : RebootAction

        /**
         * A one-time reminder whose time passed while the phone was off. No alarm is armed
         * for a past instant; the reminder stays pending and shows in the Today section
         * with the existing overdue chip, so it is visible rather than silently lost.
         */
        data object LeaveOverdue : RebootAction

        /** Completed or deleted — nothing to do. */
        data object Ignore : RebootAction
    }

    fun rebootAction(reminder: Reminder, now: LocalDateTime): RebootAction = when {
        reminder.isDeleted || reminder.isCompleted -> RebootAction.Ignore
        // A snooze that is still ahead survives the reboot.
        reminder.snoozedUntil?.isAfter(now) == true ->
            RebootAction.Reschedule(reminder.snoozedUntil)

        reminder.scheduledAt.isAfter(now) -> RebootAction.Reschedule(reminder.scheduledAt)
        reminder.repeatMode != RepeatMode.NONE -> {
            val next = nextOccurrence(reminder, after = now)
            if (next == null) {
                RebootAction.LeaveOverdue
            } else {
                RebootAction.AdvanceAndReschedule(next)
            }
        }

        else -> RebootAction.LeaveOverdue
    }
}
