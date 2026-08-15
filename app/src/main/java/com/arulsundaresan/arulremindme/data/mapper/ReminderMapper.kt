package com.arulsundaresan.arulremindme.data.mapper

import com.arulsundaresan.arulremindme.data.local.ReminderEntity
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Entity <-> domain conversion. All date maths goes through java.time — never string parsing.
 */

private fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

private fun LocalDateTime.toEpochMillis(zone: ZoneId): Long =
    atZone(zone).toInstant().toEpochMilli()

fun ReminderEntity.toDomain(): Reminder {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.systemDefault() }
    return Reminder(
        id = id,
        title = title,
        description = description,
        originalInput = originalInput,
        scheduledAt = scheduledAtEpochMillis.toLocalDateTime(zone),
        zoneId = zone,
        createdAt = createdAtEpochMillis.toLocalDateTime(zone),
        updatedAt = updatedAtEpochMillis.toLocalDateTime(zone),
        isCompleted = isCompleted,
        completedAt = completedAtEpochMillis?.toLocalDateTime(zone),
        isDeleted = isDeleted,
        snoozedUntil = snoozedUntilEpochMillis?.toLocalDateTime(zone),
        isAlarmScheduled = isAlarmScheduled,
        repeatMode = RepeatMode.fromStorage(repeatMode),
        repeatInterval = repeatInterval,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
}

fun Reminder.toEntity(): ReminderEntity {
    val zone = zoneId
    return ReminderEntity(
        id = id,
        title = title.trim(),
        description = description?.trim()?.ifBlank { null },
        originalInput = originalInput,
        scheduledAtEpochMillis = scheduledAt.toEpochMillis(zone),
        zoneId = zone.id,
        scheduledDateEpochDay = scheduledAt.toLocalDate().toEpochDay(),
        scheduledTimeSecondOfDay = scheduledAt.toLocalTime().toSecondOfDay(),
        createdAtEpochMillis = createdAt.toEpochMillis(zone),
        updatedAtEpochMillis = updatedAt.toEpochMillis(zone),
        isCompleted = isCompleted,
        completedAtEpochMillis = completedAt?.toEpochMillis(zone),
        isDeleted = isDeleted,
        deletedAtEpochMillis = null,
        snoozedUntilEpochMillis = snoozedUntil?.toEpochMillis(zone),
        isAlarmScheduled = isAlarmScheduled,
        repeatMode = repeatMode.name,
        repeatInterval = repeatInterval,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
}

fun List<ReminderEntity>.toDomain(): List<Reminder> = map { it.toDomain() }
