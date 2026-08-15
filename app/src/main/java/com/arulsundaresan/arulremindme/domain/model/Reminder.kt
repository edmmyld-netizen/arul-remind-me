package com.arulsundaresan.arulremindme.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Domain model. Pure Kotlin + java.time — no Room, no Android types, so it stays testable
 * with plain JVM unit tests.
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    /** Raw text the user typed/spoke. Used by the Session 2 NLP parser for corrections. */
    val originalInput: String? = null,
    val scheduledAt: LocalDateTime,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    val isDeleted: Boolean = false,
    /** Session 3: set by Snooze. When it is in the future it wins over [scheduledAt]. */
    val snoozedUntil: LocalDateTime? = null,
    /** Session 3: true when an exact alarm is actually armed for this reminder. */
    val isAlarmScheduled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatInterval: Int = 1,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
) {
    val scheduledDate: LocalDate get() = scheduledAt.toLocalDate()
    val scheduledTime: LocalTime get() = scheduledAt.toLocalTime()

    /** Canonical instant used by AlarmManager. */
    fun scheduledAtEpochMillis(zone: ZoneId = zoneId): Long =
        scheduledAt.atZone(zone).toInstant().toEpochMilli()

    /**
     * When the alarm should actually fire: the snooze time if one is still ahead of us,
     * otherwise the scheduled time. A stale snooze from a previous cycle is ignored.
     */
    fun triggerAtEpochMillis(nowMillis: Long, zone: ZoneId = zoneId): Long {
        val scheduled = scheduledAtEpochMillis(zone)
        val snoozed = snoozedUntil?.atZone(zone)?.toInstant()?.toEpochMilli()
        return if (snoozed != null && snoozed > nowMillis) snoozed else scheduled
    }

    fun isToday(today: LocalDate = LocalDate.now()): Boolean = scheduledDate == today

    fun isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !isCompleted && scheduledAt.isBefore(now)
}
