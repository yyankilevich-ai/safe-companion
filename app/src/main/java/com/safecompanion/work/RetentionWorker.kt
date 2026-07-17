package com.safecompanion.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safecompanion.data.AlertRepository
import com.safecompanion.settings.SessionStore
import java.util.concurrent.TimeUnit

/**
 * Daily local cleanup on the supervised device: purge the transparency log and
 * cached context messages past the policy's retention window.
 */
class RetentionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val session = store.current()
        AlertRepository.get(applicationContext).purgeLocal(store.retentionDays(session))
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "retention_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
