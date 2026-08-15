package com.arulsundaresan.arulremindme.alarm

import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recurrence maths, fire/done behaviour and reboot recovery.
 *
 * These cover the *decision* logic only. Nothing here claims to verify an actual Android
 * reboot, AlarmManager delivery or notification display — none of that exists on the JVM,
 * and a green test implying otherwise would be worse than no test at all.
 */
class RecurrenceRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun reminder(
        at: LocalDateTime,
        mode: RepeatMode = RepeatMode.NONE,
        interval: Int = 1,
        snoozedUntil: LocalDateTime? = null,
        completed: Boolean = false,
        deleted: Boolean = false
    ) = Reminder(
        id = 1L,
        title = "EB Bill கட்ட வேண்டும்",
        scheduledAt = at,
        zoneId = zone,
        repeatMode = mode,
        repeatInterval = interval,
        snoozedUntil = snoozedUntil,
        isCompleted = completed,
        isDeleted = deleted
    )

    // ---- daily -------------------------------------------------------------

    @Test
    fun `daily 8 AM rolls to the next day at 8 AM`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)

        assertEquals(
            at.plusDays(1),
            RecurrenceRules.nextOccurrence(at, RepeatMode.DAILY, after = at)
        )
    }

    @Test
    fun `a daily reminder missed for days keeps its time of day`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)

        assertEquals(
            LocalDateTime.of(2026, 8, 19, 8, 0),
            RecurrenceRules.nextOccurrence(at, RepeatMode.DAILY, after = at.plusDays(5).plusHours(3))
        )
    }

    @Test
    fun `an interval greater than one is honoured`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)

        assertEquals(
            at.plusDays(3),
            RecurrenceRules.nextOccurrence(at, RepeatMode.DAILY, repeatInterval = 3, after = at)
        )
    }

    // ---- weekly ------------------------------------------------------------

    @Test
    fun `weekly Monday 10 AM rolls to the next Monday`() {
        val monday = LocalDateTime.of(2026, 8, 17, 10, 0)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)

        val next = RecurrenceRules.nextOccurrence(monday, RepeatMode.WEEKLY, after = monday)

        assertEquals(monday.plusWeeks(1), next)
        assertEquals(DayOfWeek.MONDAY, next?.dayOfWeek)
    }

    @Test
    fun `the weekday survives a long gap`() {
        val monday = LocalDateTime.of(2026, 8, 17, 10, 0)

        val next = RecurrenceRules.nextOccurrence(
            monday,
            RepeatMode.WEEKLY,
            after = monday.plusDays(40)
        )

        assertEquals(DayOfWeek.MONDAY, next?.dayOfWeek)
    }

    // ---- monthly and month-end --------------------------------------------

    @Test
    fun `monthly 15th rolls to the 15th of the next month`() {
        val at = LocalDateTime.of(2026, 8, 15, 17, 30)

        assertEquals(
            LocalDateTime.of(2026, 9, 15, 17, 30),
            RecurrenceRules.nextOccurrence(at, RepeatMode.MONTHLY, after = at)
        )
    }

    /**
     * Documented month-end policy: the day is **clamped** to the length of the target
     * month, and the anchor is never rewritten, so the 31st comes back in months that
     * have one.
     */
    @Test
    fun `a 31st reminder clamps to the last day of shorter months`() {
        val oct31 = LocalDateTime.of(2026, 10, 31, 9, 0)

        assertEquals(
            LocalDateTime.of(2026, 11, 30, 9, 0),
            RecurrenceRules.nextOccurrence(oct31, RepeatMode.MONTHLY, after = oct31)
        )
        assertEquals(
            LocalDateTime.of(2027, 2, 28, 9, 0),
            RecurrenceRules.nextOccurrence(
                oct31,
                RepeatMode.MONTHLY,
                after = LocalDateTime.of(2027, 1, 31, 10, 0)
            )
        )
        // The anchor is untouched, so March gets its 31st back.
        assertEquals(
            LocalDateTime.of(2027, 3, 31, 9, 0),
            RecurrenceRules.nextOccurrence(
                oct31,
                RepeatMode.MONTHLY,
                after = LocalDateTime.of(2027, 2, 28, 10, 0)
            )
        )
    }

    @Test
    fun `february clamping respects leap years`() {
        val jan31 = LocalDateTime.of(2028, 1, 31, 9, 0)

        assertEquals(
            LocalDateTime.of(2028, 2, 29, 9, 0),
            RecurrenceRules.nextOccurrence(jan31, RepeatMode.MONTHLY, after = jan31)
        )
    }

    @Test
    fun `a non-recurring reminder has no next occurrence`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)
        assertNull(RecurrenceRules.nextOccurrence(at, RepeatMode.NONE, after = at))
    }

    // ---- firing ------------------------------------------------------------

    @Test
    fun `a recurring alarm advances the series when it fires`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)

        val outcome = RecurrenceRules.onFire(reminder(at, RepeatMode.DAILY), at)

        assertTrue(outcome is RecurrenceRules.FireOutcome.NotifyAndAdvance)
        assertEquals(
            at.plusDays(1),
            (outcome as RecurrenceRules.FireOutcome.NotifyAndAdvance).nextScheduledAt
        )
    }

    @Test
    fun `a one-time alarm only notifies`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)
        assertEquals(RecurrenceRules.FireOutcome.NotifyOnly, RecurrenceRules.onFire(reminder(at), at))
    }

    /** The regression this guards: a snooze must not drag the daily series to 8:10. */
    @Test
    fun `snoozing a daily reminder leaves the series on 8 AM`() {
        val eight = LocalDateTime.of(2026, 8, 13, 8, 0)
        // After the 8:00 alarm fired, the series already points at tomorrow 8:00.
        val afterFire = reminder(
            at = eight.plusDays(1),
            mode = RepeatMode.DAILY,
            snoozedUntil = eight.plusMinutes(10)
        )

        // The snooze alarm fires: the marker is cleared, the series is untouched.
        assertEquals(
            RecurrenceRules.FireOutcome.NotifyAndClearSnooze,
            RecurrenceRules.onFire(afterFire, eight.plusMinutes(10))
        )
        assertEquals(eight.plusDays(1), afterFire.scheduledAt)
        assertEquals(
            eight.plusDays(2),
            RecurrenceRules.nextOccurrence(
                afterFire.copy(snoozedUntil = null),
                after = eight.plusDays(1)
            )
        )
    }

    // ---- done --------------------------------------------------------------

    @Test
    fun `done in the list skips one occurrence of a recurring reminder`() {
        val monday = LocalDateTime.of(2026, 8, 17, 10, 0)

        val outcome = RecurrenceRules.onDoneFromList(reminder(monday, RepeatMode.WEEKLY), monday)

        assertTrue(outcome is RecurrenceRules.DoneOutcome.AdvanceToNext)
        assertEquals(
            monday.plusWeeks(1),
            (outcome as RecurrenceRules.DoneOutcome.AdvanceToNext).nextScheduledAt
        )
    }

    @Test
    fun `done on the notification does not advance a series twice`() {
        val monday = LocalDateTime.of(2026, 8, 17, 10, 0)

        assertEquals(
            RecurrenceRules.DoneOutcome.DismissOnly,
            RecurrenceRules.onDoneFromAlert(reminder(monday, RepeatMode.WEEKLY))
        )
    }

    @Test
    fun `done still completes a one-time reminder`() {
        val at = LocalDateTime.of(2026, 8, 13, 8, 0)

        assertEquals(
            RecurrenceRules.DoneOutcome.MarkCompleted,
            RecurrenceRules.onDoneFromList(reminder(at), at)
        )
        assertEquals(
            RecurrenceRules.DoneOutcome.MarkCompleted,
            RecurrenceRules.onDoneFromAlert(reminder(at))
        )
    }

    // ---- reboot ------------------------------------------------------------

    @Test
    fun `a future reminder is simply re-armed after reboot`() {
        val now = LocalDateTime.of(2026, 8, 13, 9, 0)

        assertEquals(
            RecurrenceRules.RebootAction.Reschedule(now.plusHours(2)),
            RecurrenceRules.rebootAction(reminder(now.plusHours(2)), now)
        )
    }

    /**
     * Documented behaviour for spec §4: a one-time reminder whose time passed while the
     * phone was off is **not** given a past alarm and is **not** fired late. It stays
     * pending and shows in Today with the existing overdue chip.
     */
    @Test
    fun `a one-time reminder missed while powered off is left overdue`() {
        val now = LocalDateTime.of(2026, 8, 13, 9, 0)

        assertEquals(
            RecurrenceRules.RebootAction.LeaveOverdue,
            RecurrenceRules.rebootAction(reminder(LocalDateTime.of(2026, 8, 13, 8, 0)), now)
        )
    }

    @Test
    fun `a recurring reminder missed while powered off jumps to the next occurrence`() {
        val now = LocalDateTime.of(2026, 8, 13, 9, 0)
        val eight = LocalDateTime.of(2026, 8, 13, 8, 0)

        val action = RecurrenceRules.rebootAction(reminder(eight, RepeatMode.DAILY), now)

        assertTrue(action is RecurrenceRules.RebootAction.AdvanceAndReschedule)
        assertEquals(
            eight.plusDays(1),
            (action as RecurrenceRules.RebootAction.AdvanceAndReschedule).scheduledAt
        )
    }

    @Test
    fun `completed and deleted reminders are ignored after reboot`() {
        val now = LocalDateTime.of(2026, 8, 13, 9, 0)

        assertEquals(
            RecurrenceRules.RebootAction.Ignore,
            RecurrenceRules.rebootAction(reminder(now.plusHours(1), completed = true), now)
        )
        assertEquals(
            RecurrenceRules.RebootAction.Ignore,
            RecurrenceRules.rebootAction(reminder(now.plusHours(1), deleted = true), now)
        )
    }

    @Test
    fun `an outstanding snooze survives a reboot`() {
        val now = LocalDateTime.of(2026, 8, 13, 9, 0)
        val target = reminder(
            at = LocalDateTime.of(2026, 8, 13, 8, 0),
            snoozedUntil = now.plusMinutes(5)
        )

        assertEquals(
            RecurrenceRules.RebootAction.Reschedule(now.plusMinutes(5)),
            RecurrenceRules.rebootAction(target, now)
        )
    }

    @Test
    fun `re-arming reuses the same request code so no duplicate alarm appears`() {
        assertEquals(
            AlarmIds.requestCode(2L, AlarmSlot.FIRE),
            AlarmIds.requestCode(2L, AlarmSlot.FIRE)
        )
        val codes = listOf(1L, 2L, 3L).map { AlarmIds.requestCode(it, AlarmSlot.FIRE) }
        assertEquals(3, codes.distinct().size)
    }
}
