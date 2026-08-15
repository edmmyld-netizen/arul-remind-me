package com.arulsundaresan.arulremindme.di

import android.content.Context
import com.arulsundaresan.arulremindme.alarm.AlarmManagerReminderScheduler
import com.arulsundaresan.arulremindme.data.local.ReminderDatabase
import com.arulsundaresan.arulremindme.data.repository.ReminderRepositoryImpl
import com.arulsundaresan.arulremindme.domain.repository.ReminderRepository
import com.arulsundaresan.arulremindme.domain.scheduler.ReminderScheduler
import com.arulsundaresan.arulremindme.nlp.ReminderParser

/**
 * Hand-rolled DI. Deliberately not Hilt: the graph is three objects, and keeping it plain
 * avoids annotation-processor surprises. Swapping in Hilt later touches only this file
 * and [com.arulsundaresan.arulremindme.ArulRemindMeApp].
 */
interface AppContainer {
    val reminderRepository: ReminderRepository
    val reminderScheduler: ReminderScheduler
    val reminderParser: ReminderParser
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: ReminderDatabase by lazy { ReminderDatabase.getInstance(context) }

    /**
     * Session 3: the real AlarmManager-backed scheduler. NoOpReminderScheduler still exists
     * for tests, but nothing in the app graph uses it any more.
     */
    override val reminderScheduler: ReminderScheduler by lazy {
        AlarmManagerReminderScheduler(context.applicationContext)
    }

    /**
     * Session 2. Stateless and offline, so one shared instance is enough. It takes the
     * device clock by default, which is what keeps relative dates correct forever.
     */
    override val reminderParser: ReminderParser by lazy { ReminderParser() }

    override val reminderRepository: ReminderRepository by lazy {
        ReminderRepositoryImpl(
            dao = database.reminderDao(),
            scheduler = reminderScheduler
        )
    }
}
