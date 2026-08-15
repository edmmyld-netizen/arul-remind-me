package com.arulsundaresan.arulremindme.domain.repository

import com.arulsundaresan.arulremindme.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * The only contract the UI layer knows about. Room lives entirely behind this.
 */
interface ReminderRepository {

    /** Pending (not completed, not deleted), soonest first. */
    fun observeActive(): Flow<List<Reminder>>

    /** Completed and not deleted, most recently completed first. */
    fun observeCompleted(): Flow<List<Reminder>>

    fun observeById(id: Long): Flow<Reminder?>

    suspend fun getById(id: Long): Reminder?

    /** @return the id of the newly created reminder. */
    suspend fun add(reminder: Reminder): Long

    suspend fun update(reminder: Reminder)

    suspend fun setCompleted(id: Long, completed: Boolean)

    /** Soft delete — keeps the row so Undo works and so alarms can be audited. */
    suspend fun delete(id: Long)

    suspend fun restoreDeleted(id: Long)

    suspend fun deleteForever(id: Long)

    /**
     * Session 3: pushes the reminder out to [until] and re-arms the alarm for that instant.
     * Uses the existing `snoozedUntilEpochMillis` column.
     */
    suspend fun snooze(id: Long, until: LocalDateTime)

    /**
     * Session 4: moves a recurring reminder to [nextScheduledAt], clears any snooze and
     * re-arms exactly one alarm. Used after a recurring alarm fires and after reboot
     * recovery.
     */
    suspend fun advanceRecurring(id: Long, nextScheduledAt: LocalDateTime)

    /**
     * Session 4: "Done" pressed on a notification or the full-screen alert.
     * One-time reminders complete as before; a recurring series has already been advanced
     * by the firing alarm, so this only stops the current occurrence.
     */
    suspend fun completeOccurrence(id: Long)

    /** Used by the Session 4 BOOT_COMPLETED receiver to re-arm alarms. */
    suspend fun pendingFrom(epochMillis: Long): List<Reminder>
}
