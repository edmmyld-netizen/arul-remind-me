package com.arulsundaresan.arulremindme

import android.app.Application
import com.arulsundaresan.arulremindme.di.AppContainer
import com.arulsundaresan.arulremindme.di.DefaultAppContainer
import com.arulsundaresan.arulremindme.notification.ReminderNotifier

class ArulRemindMeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // createNotificationChannel is a no-op when the channel already exists, so this
        // never resets settings the user has changed.
        ReminderNotifier(this).ensureChannel()
    }
}
