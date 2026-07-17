package com.safecompanion

import android.app.Application
import com.safecompanion.notify.ParentNotifier
import com.safecompanion.settings.AppMode
import com.safecompanion.settings.SessionStore
import com.safecompanion.work.ChildSyncWorker
import com.safecompanion.work.ParentSyncWorker
import com.safecompanion.work.RetentionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ParentNotifier.ensureChannel(this)
        RetentionWorker.schedule(this)

        // Schedule the background sync matching the configured mode.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            when (SessionStore(this@SafeApp).current().mode) {
                AppMode.SUPERVISED -> ChildSyncWorker.schedulePeriodic(this@SafeApp)
                AppMode.SUPERVISOR -> ParentSyncWorker.schedulePeriodic(this@SafeApp)
                AppMode.UNSET -> Unit
            }
        }
    }
}
