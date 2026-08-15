package com.arulsundaresan.arulremindme.domain

import com.arulsundaresan.arulremindme.data.mapper.toDomain
import com.arulsundaresan.arulremindme.data.mapper.toEntity
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderModelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun reminder(at: LocalDateTime) = Reminder(
        title = "EB Bill கட்ட வேண்டும்",
        scheduledAt = at,
        zoneId = zone,
        createdAt = LocalDateTime.of(2026, 8, 12, 9, 0)
    )

    @Test
    fun `entity round trip preserves date and time`() {
        val original = reminder(LocalDateTime.of(2026, 8, 15, 17, 30))

        val restored = original.toEntity().toDomain()

        assertEquals(LocalDate.of(2026, 8, 15), restored.scheduledDate)
        assertEquals(LocalTime.of(17, 30), restored.scheduledTime)
        assertEquals(zone, restored.zoneId)
        assertEquals(original.title, restored.title)
    }

    @Test
    fun `entity stores numeric date parts not display strings`() {
        val entity = reminder(LocalDateTime.of(2026, 8, 15, 17, 30)).toEntity()

        assertEquals(LocalDate.of(2026, 8, 15).toEpochDay(), entity.scheduledDateEpochDay)
        assertEquals(17 * 3600 + 30 * 60, entity.scheduledTimeSecondOfDay)
        assertTrue(entity.scheduledAtEpochMillis > 0L)
    }

    @Test
    fun `overdue is true only for past pending reminders`() {
        val now = LocalDateTime.of(2026, 8, 12, 10, 0)

        assertTrue(reminder(now.minusHours(1)).isOverdue(now))
        assertFalse(reminder(now.plusHours(1)).isOverdue(now))
        assertFalse(reminder(now.minusHours(1)).copy(isCompleted = true).isOverdue(now))
    }

    @Test
    fun `isToday compares only the date part`() {
        val today = LocalDate.of(2026, 8, 12)

        assertTrue(reminder(today.atTime(23, 59)).isToday(today))
        assertFalse(reminder(today.plusDays(1).atTime(0, 1)).isToday(today))
    }

    @Test
    fun `repeat mode falls back to NONE for unknown storage values`() {
        assertEquals(RepeatMode.NONE, RepeatMode.fromStorage(null))
        assertEquals(RepeatMode.NONE, RepeatMode.fromStorage("nonsense"))
        assertEquals(RepeatMode.WEEKLY, RepeatMode.fromStorage("weekly"))
    }
}
