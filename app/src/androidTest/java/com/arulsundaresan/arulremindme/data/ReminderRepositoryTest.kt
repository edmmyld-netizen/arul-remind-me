package com.arulsundaresan.arulremindme.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arulsundaresan.arulremindme.data.local.ReminderDatabase
import com.arulsundaresan.arulremindme.data.repository.ReminderRepositoryImpl
import com.arulsundaresan.arulremindme.domain.model.Reminder
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import com.arulsundaresan.arulremindme.domain.scheduler.NoOpReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Covers the Session 1 acceptance list: create, read, update, complete, delete.
 */
@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    private lateinit var database: ReminderDatabase
    private lateinit var repository: ReminderRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReminderDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = ReminderRepositoryImpl(
            dao = database.reminderDao(),
            scheduler = NoOpReminderScheduler()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sample(
        title: String = "EB Bill கட்ட வேண்டும்",
        at: LocalDateTime = LocalDateTime.of(2026, 8, 15, 17, 30)
    ) = Reminder(title = title, scheduledAt = at)

    @Test
    fun createAndRead() = runTest {
        val id = repository.add(sample())

        assertTrue(id > 0L)
        val stored = repository.getById(id)
        assertNotNull(stored)
        assertEquals("EB Bill கட்ட வேண்டும்", stored!!.title)
        assertEquals(LocalDateTime.of(2026, 8, 15, 17, 30), stored.scheduledAt)

        val active = repository.observeActive().first()
        assertEquals(1, active.size)
    }

    @Test
    fun activeListIsSortedBySchedule() = runTest {
        repository.add(sample(title = "Later", at = LocalDateTime.of(2026, 9, 1, 9, 0)))
        repository.add(sample(title = "Sooner", at = LocalDateTime.of(2026, 8, 20, 9, 0)))

        val active = repository.observeActive().first()

        assertEquals(listOf("Sooner", "Later"), active.map { it.title })
    }

    @Test
    fun updateChangesTitleAndSchedule() = runTest {
        val id = repository.add(sample())
        val stored = repository.getById(id)!!

        repository.update(
            stored.copy(
                title = "Collector meeting",
                scheduledAt = LocalDateTime.of(2026, 8, 16, 9, 15)
            )
        )

        val updated = repository.getById(id)!!
        assertEquals("Collector meeting", updated.title)
        assertEquals(LocalDateTime.of(2026, 8, 16, 9, 15), updated.scheduledAt)
    }

    @Test
    fun completeMovesReminderToCompletedList() = runTest {
        val id = repository.add(sample())

        repository.setCompleted(id, completed = true)

        assertTrue(repository.observeActive().first().isEmpty())
        val completed = repository.observeCompleted().first()
        assertEquals(1, completed.size)
        assertTrue(completed.first().isCompleted)
        assertNotNull(completed.first().completedAt)
    }

    @Test
    fun restoringACompletedReminderMakesItActiveAgain() = runTest {
        val id = repository.add(sample())
        repository.setCompleted(id, completed = true)

        repository.setCompleted(id, completed = false)

        assertEquals(1, repository.observeActive().first().size)
        assertTrue(repository.observeCompleted().first().isEmpty())
        assertNull(repository.getById(id)!!.completedAt)
    }

    @Test
    fun deleteIsSoftAndUndoable() = runTest {
        val id = repository.add(sample())

        repository.delete(id)
        assertTrue(repository.observeActive().first().isEmpty())
        assertNotNull(repository.getById(id))
        assertTrue(repository.getById(id)!!.isDeleted)

        repository.restoreDeleted(id)
        assertEquals(1, repository.observeActive().first().size)
        assertFalse(repository.getById(id)!!.isDeleted)
    }

    @Test
    fun deleteForeverRemovesTheRow() = runTest {
        val id = repository.add(sample())

        repository.deleteForever(id)

        assertNull(repository.getById(id))
    }

    @Test
    fun pendingFromReturnsOnlyFutureUncompletedReminders() = runTest {
        val past = LocalDateTime.of(2026, 1, 1, 8, 0)
        val future = LocalDateTime.of(2030, 1, 1, 8, 0)
        repository.add(sample(title = "Past", at = past))
        val futureId = repository.add(sample(title = "Future", at = future))
        val completedId = repository.add(sample(title = "Done", at = future))
        repository.setCompleted(completedId, completed = true)

        val cutoff = System.currentTimeMillis()
        val pending = repository.pendingFrom(cutoff)

        assertEquals(listOf(futureId), pending.map { it.id })
    }
}
