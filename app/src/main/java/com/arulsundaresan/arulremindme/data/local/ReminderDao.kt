package com.arulsundaresan.arulremindme.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query(
        "SELECT * FROM reminders WHERE isDeleted = 0 AND isCompleted = 0 " +
            "ORDER BY scheduledAtEpochMillis ASC"
    )
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query(
        "SELECT * FROM reminders WHERE isDeleted = 0 AND isCompleted = 1 " +
            "ORDER BY completedAtEpochMillis DESC, scheduledAtEpochMillis DESC"
    )
    fun observeCompleted(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query(
        "UPDATE reminders SET isCompleted = :completed, " +
            "completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :now WHERE id = :id"
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, now: Long)

    @Query(
        "UPDATE reminders SET isDeleted = :deleted, " +
            "deletedAtEpochMillis = :deletedAt, updatedAtEpochMillis = :now WHERE id = :id"
    )
    suspend fun setDeleted(id: Long, deleted: Boolean, deletedAt: Long?, now: Long)

    /** Session 3: snooze. The column already exists in schema v1 — no migration needed. */
    @Query(
        "UPDATE reminders SET snoozedUntilEpochMillis = :until, updatedAtEpochMillis = :now " +
            "WHERE id = :id"
    )
    suspend fun setSnoozedUntil(id: Long, until: Long?, now: Long)

    /**
     * Session 4: moves a recurring series to its next occurrence and clears any snooze.
     * Only existing columns are written — schema stays at version 1.
     */
    @Query(
        "UPDATE reminders SET scheduledAtEpochMillis = :scheduledAt, " +
            "scheduledDateEpochDay = :epochDay, scheduledTimeSecondOfDay = :secondOfDay, " +
            "snoozedUntilEpochMillis = NULL, updatedAtEpochMillis = :now WHERE id = :id"
    )
    suspend fun advanceSchedule(
        id: Long,
        scheduledAt: Long,
        epochDay: Long,
        secondOfDay: Int,
        now: Long
    )

    /** Session 3: records whether an exact alarm is actually armed. */
    @Query("UPDATE reminders SET isAlarmScheduled = :scheduled WHERE id = :id")
    suspend fun setAlarmScheduled(id: Long, scheduled: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteForever(id: Long)

    /** Session 4: re-arm alarms after BOOT_COMPLETED. */
    @Query(
        "SELECT * FROM reminders WHERE isDeleted = 0 AND isCompleted = 0 " +
            "AND scheduledAtEpochMillis >= :fromEpochMillis ORDER BY scheduledAtEpochMillis ASC"
    )
    suspend fun pendingFrom(fromEpochMillis: Long): List<ReminderEntity>

    /** Housekeeping for soft-deleted rows. */
    @Query("DELETE FROM reminders WHERE isDeleted = 1 AND deletedAtEpochMillis < :beforeEpochMillis")
    suspend fun purgeDeletedBefore(beforeEpochMillis: Long)
}
