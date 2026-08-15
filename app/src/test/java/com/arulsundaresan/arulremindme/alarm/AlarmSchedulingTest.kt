package com.arulsundaresan.arulremindme.alarm

import com.arulsundaresan.arulremindme.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Covers the decision logic behind scheduling. It deliberately does **not** claim to test
 * AlarmManager, lock-screen behaviour or notification delivery — none of that exists on the
 * JVM, and a green test that implied it did would be worse than no test.
 */
class AlarmSchedulingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val now: Long = Instant.parse("2026-08-13T02:30:00Z").toEpochMilli()

    private fun reminder(
        id: Long = 1L,
        at: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusHours(2),
        completed: Boolean = false,
        deleted: Boolean = false,
        snoozedUntil: LocalDateTime? = null
    ) = Reminder(
        id = id,
        title = "EB Bill கட்ட வேண்டும்",
        scheduledAt = at,
        zoneId = zone,
        isCompleted = completed,
        isDeleted = deleted,
        snoozedUntil = snoozedUntil
    )

    // ---- request ids -------------------------------------------------------

    @Test
    fun `request codes are stable across calls`() {
        assertEquals(
            AlarmIds.requestCode(42L, AlarmSlot.FIRE),
            AlarmIds.requestCode(42L, AlarmSlot.FIRE)
        )
    }

    @Test
    fun `each slot of one reminder gets a distinct code`() {
        val codes = AlarmSlot.entries.map { AlarmIds.requestCode(7L, it) }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `different reminders never collide`() {
        val codes = (1L..500L).flatMap { id ->
            AlarmSlot.entries.map { AlarmIds.requestCode(id, it) }
        }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `request codes stay inside positive Int range`() {
        val code = AlarmIds.requestCode(Long.MAX_VALUE, AlarmSlot.ALERT)
        assertTrue("got $code", code >= 0)
    }

    @Test
    fun `notification id matches the fire slot`() {
        assertEquals(AlarmIds.requestCode(9L, AlarmSlot.FIRE), AlarmIds.notificationId(9L))
        assertNotEquals(AlarmIds.notificationId(9L), AlarmIds.notificationId(10L))
    }

    // ---- schedule decisions ------------------------------------------------

    @Test
    fun `a future reminder is armed for its scheduled instant`() {
        val target = reminder()
        val decision = AlarmScheduleRules.decide(target, now)

        assertTrue(decision is ScheduleDecision.Arm)
        assertEquals(
            target.scheduledAtEpochMillis(zone),
            (decision as ScheduleDecision.Arm).triggerAtMillis
        )
    }

    @Test
    fun `a past reminder is never armed`() {
        val past = reminder(
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusHours(1)
        )
        val decision = AlarmScheduleRules.decide(past, now)

        assertEquals(ScheduleDecision.Skip(SkipReason.IN_THE_PAST), decision)
    }

    @Test
    fun `a reminder inside the minimum lead is treated as past`() {
        val tooSoon = reminder(
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusSeconds(2)
        )
        assertEquals(
            ScheduleDecision.Skip(SkipReason.IN_THE_PAST),
            AlarmScheduleRules.decide(tooSoon, now)
        )
    }

    @Test
    fun `completed and deleted reminders are not armed`() {
        assertEquals(
            ScheduleDecision.Skip(SkipReason.ALREADY_COMPLETED),
            AlarmScheduleRules.decide(reminder(completed = true), now)
        )
        assertEquals(
            ScheduleDecision.Skip(SkipReason.DELETED),
            AlarmScheduleRules.decide(reminder(deleted = true), now)
        )
    }

    @Test
    fun `an unsaved reminder has no stable id to schedule against`() {
        assertEquals(
            ScheduleDecision.Skip(SkipReason.NOT_PERSISTED),
            AlarmScheduleRules.decide(reminder(id = 0L), now)
        )
    }

    // ---- reschedule on edit ------------------------------------------------

    @Test
    fun `editing the time changes the trigger but keeps the request code`() {
        val original = reminder()
        val edited = original.copy(scheduledAt = original.scheduledAt.plusMinutes(30))

        val before = AlarmScheduleRules.decide(original, now) as ScheduleDecision.Arm
        val after = AlarmScheduleRules.decide(edited, now) as ScheduleDecision.Arm

        assertNotEquals(before.triggerAtMillis, after.triggerAtMillis)
        // Same code means AlarmManager replaces the old alarm instead of adding a second.
        assertEquals(
            AlarmIds.requestCode(original.id, AlarmSlot.FIRE),
            AlarmIds.requestCode(edited.id, AlarmSlot.FIRE)
        )
    }

    // ---- snooze ------------------------------------------------------------

    @Test
    fun `snooze offsets from the moment it was tapped`() {
        assertEquals(now + 5 * 60_000L, AlarmScheduleRules.snoozeUntilMillis(now, SnoozeOption.FIVE))
        assertEquals(now + 10 * 60_000L, AlarmScheduleRules.snoozeUntilMillis(now, SnoozeOption.TEN))
        assertEquals(
            now + 30 * 60_000L,
            AlarmScheduleRules.snoozeUntilMillis(now, SnoozeOption.THIRTY)
        )
    }

    @Test
    fun `the notification snooze action defaults to ten minutes`() {
        assertEquals(10, SnoozeOption.DEFAULT.minutes)
        assertEquals(SnoozeOption.THIRTY, SnoozeOption.fromMinutes(30))
        assertEquals(SnoozeOption.DEFAULT, SnoozeOption.fromMinutes(999))
    }

    @Test
    fun `a pending snooze wins over the original scheduled time`() {
        val snoozedTo = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusMinutes(10)
        val target = reminder(
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusHours(1),
            snoozedUntil = snoozedTo
        )

        val decision = AlarmScheduleRules.decide(target, now)

        assertTrue(decision is ScheduleDecision.Arm)
        assertEquals(
            snoozedTo.atZone(zone).toInstant().toEpochMilli(),
            (decision as ScheduleDecision.Arm).triggerAtMillis
        )
    }

    @Test
    fun `an expired snooze falls back to the scheduled time`() {
        val stale = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusMinutes(5)
        val target = reminder(snoozedUntil = stale)

        assertEquals(
            target.scheduledAtEpochMillis(zone),
            target.triggerAtEpochMillis(now, zone)
        )
    }

    // ---- what the receiver checks when an alarm lands ----------------------

    @Test
    fun `receiver notifies for a due pending reminder`() {
        val due = reminder(
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        )
        assertTrue(AlarmScheduleRules.shouldNotify(due, now))
    }

    @Test
    fun `receiver stays silent for missing completed or deleted reminders`() {
        assertFalse(AlarmScheduleRules.shouldNotify(null, now))
        assertFalse(AlarmScheduleRules.shouldNotify(reminder(completed = true), now))
        assertFalse(AlarmScheduleRules.shouldNotify(reminder(deleted = true), now))
    }

    @Test
    fun `receiver ignores a stale alarm that arrives after a fresh snooze`() {
        val snoozedAhead = reminder(
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusMinutes(1),
            snoozedUntil = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusMinutes(9)
        )
        assertFalse(AlarmScheduleRules.shouldNotify(snoozedAhead, now))
    }
}
