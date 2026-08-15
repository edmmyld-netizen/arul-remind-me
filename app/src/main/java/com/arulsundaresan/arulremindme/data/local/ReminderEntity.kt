package com.arulsundaresan.arulremindme.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Storage model.
 *
 * Date/time rule: [scheduledAtEpochMillis] + [zoneId] is the single source of truth.
 * [scheduledDateEpochDay] and [scheduledTimeSecondOfDay] are denormalised *numeric* copies
 * used for cheap grouping/queries — never display strings.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index("scheduledAtEpochMillis"),
        Index("isCompleted"),
        Index("isDeleted")
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,
    val description: String? = null,
    val originalInput: String? = null,

    /** Canonical instant. What AlarmManager will be given in Session 3. */
    val scheduledAtEpochMillis: Long,
    /** IANA zone the user was in when the reminder was created, e.g. "Asia/Kolkata". */
    val zoneId: String,
    val scheduledDateEpochDay: Long,
    val scheduledTimeSecondOfDay: Int,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,

    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMillis: Long? = null,

    // ---- reserved for later sessions (present from schema v1 to avoid migrations) ----
    @ColumnInfo(defaultValue = "NONE")
    val repeatMode: String = "NONE",
    @ColumnInfo(defaultValue = "1")
    val repeatInterval: Int = 1,
    val snoozedUntilEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val isAlarmScheduled: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val soundEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val vibrationEnabled: Boolean = true
)
