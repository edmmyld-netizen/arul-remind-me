package com.arulsundaresan.arulremindme.data.repository

import com.arulsundaresan.arulremindme.data.local.ReminderDao
import com.arulsundaresan.arulremindme.data.mapper.toDomain
import com.arulsundaresan.arulremindme.data.mapper.toEntity
import com.arulsundaresan.arulremindme.alarm.RecurrenceRules
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import com.arulsundaresan.arulremindme.domain.scheduler.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Single place where persistence and alarm scheduling are kept in sync.
 *
 * In Session 1 [scheduler] is a no-op; when the real AlarmManager implementation lands in
 * Session 3, every create/update/complete/delete path here already calls it.
 */
class ReminderRepositoryImpl(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReminderRepository {

    override fun observeActive(): Flow<List<Reminder>> =
        dao.observeActive().map { it.toDomain() }.flowOn(ioDispatcher)

    override fun observeCompleted(): Flow<List<Reminder>> =
        dao.observeCompleted().map { it.toDomain() }.flowOn(ioDispatcher)

    override fun observeById(id: Long): Flow<Reminder?> =
        dao.observeById(id).map { entity -> entity?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun getById(id: Long): Reminder? = withContext(ioDispatcher) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun add(reminder: Reminder): Long = withContext(ioDispatcher) {
        val now = LocalDateTime.now()
        val toSave = reminder.copy(createdAt = now, updatedAt = now)
        val newId = dao.insert(toSave.toEntity().copy(id = 0L))
        val exact = scheduler.schedule(toSave.copy(id = newId))
        dao.setAlarmScheduled(newId, exact)
        newId
    }

    override suspend fun update(reminder: Reminder): Unit = withContext(ioDispatcher) {
        // Editing clears any snooze — the user just chose a new time explicitly.
        val updated = reminder.copy(updatedAt = LocalDateTime.now(), snoozedUntil = null)
        dao.update(updated.toEntity())
        // Cancel first so an edited time can never leave the old alarm armed.
        scheduler.cancel(updated.id)
        val exact = if (updated.isCompleted) false else scheduler.schedule(updated)
        dao.setAlarmScheduled(updated.id, exact)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Unit =
        withContext(ioDispatcher) {
            // Session 4: "Done" on a recurring reminder means "skip this occurrence",
            // never "end the series".
            val existing = dao.getById(id)?.toDomain()
            if (completed && existing != null && existing.repeatMode != RepeatMode.NONE) {
                when (
                    val outcome = RecurrenceRules.onDoneFromList(existing, LocalDateTime.now())
                ) {
                    is RecurrenceRules.DoneOutcome.AdvanceToNext -> {
                        advanceRecurring(id, outcome.nextScheduledAt)
                        return@withContext
                    }

                    else -> Unit // fall through to the ordinary completion path
                }
            }
            val now = System.currentTimeMillis()
            dao.setCompleted(
                id = id,
                completed = completed,
                completedAt = if (completed) now else null,
                now = now
            )
            if (completed) {
                scheduler.cancel(id)
                dao.setAlarmScheduled(id, false)
            } else {
                dao.getById(id)?.toDomain()?.let {
                    dao.setAlarmScheduled(id, scheduler.schedule(it))
                }
            }
        }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.setDeleted(id = id, deleted = true, deletedAt = now, now = now)
        scheduler.cancel(id)
        dao.setAlarmScheduled(id, false)
    }

    override suspend fun restoreDeleted(id: Long): Unit = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.setDeleted(id = id, deleted = false, deletedAt = null, now = now)
        dao.getById(id)?.toDomain()?.takeIf { !it.isCompleted }?.let {
            dao.setAlarmScheduled(id, scheduler.schedule(it))
        }
    }

    override suspend fun deleteForever(id: Long): Unit = withContext(ioDispatcher) {
        scheduler.cancel(id)
        dao.deleteForever(id)
    }

    override suspend fun snooze(id: Long, until: LocalDateTime): Unit = withContext(ioDispatcher) {
        val zone = dao.getById(id)?.toDomain()?.zoneId ?: ZoneId.systemDefault()
        val untilMillis = until.atZone(zone).toInstant().toEpochMilli()
        dao.setSnoozedUntil(id, untilMillis, System.currentTimeMillis())
        scheduler.cancel(id)
        dao.getById(id)?.toDomain()?.let {
            dao.setAlarmScheduled(id, scheduler.schedule(it))
        }
    }

    override suspend fun advanceRecurring(id: Long, nextScheduledAt: LocalDateTime): Unit =
        withContext(ioDispatcher) {
            val existing = dao.getById(id)?.toDomain() ?: return@withContext
            val zone = existing.zoneId
            dao.advanceSchedule(
                id = id,
                scheduledAt = nextScheduledAt.atZone(zone).toInstant().toEpochMilli(),
                epochDay = nextScheduledAt.toLocalDate().toEpochDay(),
                secondOfDay = nextScheduledAt.toLocalTime().toSecondOfDay(),
                now = System.currentTimeMillis()
            )
            // Cancel first so the advanced series can never leave the old alarm armed.
            scheduler.cancel(id)
            dao.getById(id)?.toDomain()?.let {
                dao.setAlarmScheduled(id, scheduler.schedule(it))
            }
        }

    override suspend fun completeOccurrence(id: Long): Unit = withContext(ioDispatcher) {
        val existing = dao.getById(id)?.toDomain() ?: return@withContext
        when (RecurrenceRules.onDoneFromAlert(existing)) {
            // The firing alarm already moved a recurring series on; nothing to change here.
            is RecurrenceRules.DoneOutcome.DismissOnly -> Unit
            else -> setCompleted(id, completed = true)
        }
    }

    override suspend fun pendingFrom(epochMillis: Long): List<Reminder> =
        withContext(ioDispatcher) { dao.pendingFrom(epochMillis).toDomain() }
}
