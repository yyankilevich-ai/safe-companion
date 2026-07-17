package com.safecompanion.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safecompanion.BackendConfig
import com.safecompanion.data.AlertRepository
import com.safecompanion.net.AnthropicClient
import com.safecompanion.net.SupabaseApi
import com.safecompanion.notify.ParentNotifier
import com.safecompanion.service.WaNotificationListener
import com.safecompanion.settings.AppMode
import com.safecompanion.settings.SecretStore
import com.safecompanion.settings.SessionStore
import org.json.JSONObject

private fun netConstraints() = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

/**
 * CHILD device sync: flush the outbox, send a heartbeat with permission status,
 * and pull the current policy (the parent may have changed it).
 */
class ChildSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!BackendConfig.isConfigured) return Result.success()
        val store = SessionStore(applicationContext)
        val secrets = SecretStore(applicationContext)
        val session = store.current()
        if (session.mode != AppMode.SUPERVISED || secrets.deviceToken.isBlank()) {
            return Result.success()
        }

        val repo = AlertRepository.get(applicationContext)
        var hadFailure = false

        // 1) Flush outbox.
        val batch = repo.outbox().batch()
        for (item in batch) {
            try {
                val resp = SupabaseApi.rpc(
                    "submit_event",
                    JSONObject()
                        .put("p_device_token", secrets.deviceToken)
                        .put("p_event", JSONObject(item.payloadJson))
                )
                if (resp.optBoolean("ok", false)) {
                    repo.outbox().delete(item.id)
                } else {
                    repo.outbox().bumpAttempts(item.id)
                    if (resp.optString("error") == "unknown_device") {
                        // Enrollment was revoked; stop retrying this batch.
                        return Result.success()
                    }
                    hadFailure = true
                }
            } catch (_: Exception) {
                repo.outbox().bumpAttempts(item.id)
                hadFailure = true
            }
        }

        // 2) Heartbeat + policy pull.
        try {
            val status = JSONObject()
                .put("notification_access", WaNotificationListener.isAccessGranted(applicationContext))
                .put("app_version", "0.2.0")
            val resp = SupabaseApi.rpc(
                "device_heartbeat",
                JSONObject().put("p_device_token", secrets.deviceToken).put("p_status", status)
            )
            if (resp.optBoolean("ok", false)) {
                store.updatePolicy(
                    childName = resp.optString("child_name", session.childName),
                    ageBand = resp.optString("age_band", session.ageBand),
                    policyJson = resp.optJSONObject("policy")?.toString() ?: session.policyJson
                )
            }
        } catch (_: Exception) {
            hadFailure = true
        }

        return if (hadFailure) Result.retry() else Result.success()
    }

    companion object {
        fun syncNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<ChildSyncWorker>()
                .setConstraints(netConstraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("child_sync_now", ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ChildSyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).setConstraints(netConstraints()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "child_sync_periodic", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}

/**
 * PARENT device sync: fetch new safety events, run AI second-stage analysis on
 * pending ones (key lives only here), notify per threshold, purge expired rows.
 * Returns the number of new events via [lastNewCount] for foreground refreshes.
 */
class ParentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val n = runSync(applicationContext)
            lastNewCount = n
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        @Volatile var lastNewCount: Int = 0

        /** Shared sync routine, also called directly by the supervisor UI. */
        suspend fun runSync(context: Context): Int {
            if (!BackendConfig.isConfigured) return 0
            val store = SessionStore(context)
            val secrets = SecretStore(context)
            val session = store.current()
            if (session.mode != AppMode.SUPERVISOR || secrets.accessToken.isBlank()) return 0

            var token = secrets.accessToken

            // Refresh the session token if the API rejects it.
            fun <T> withAuthRetry(block: (String) -> T): T {
                return try {
                    block(token)
                } catch (e: SupabaseApi.ApiException) {
                    if (e.code == 401 && secrets.refreshToken.isNotBlank()) {
                        val s = SupabaseApi.refresh(secrets.refreshToken)
                        secrets.accessToken = s.accessToken
                        secrets.refreshToken = s.refreshToken
                        token = s.accessToken
                        block(token)
                    } else throw e
                }
            }

            // 1) Fetch events newer than the cursor. (URL-encode: ISO stamps contain '+')
            val cursor = session.lastEventCursor
            val encodedCursor = java.net.URLEncoder.encode(cursor, "UTF-8")
            val rows = withAuthRetry { t ->
                SupabaseApi.select(
                    "events",
                    "select=*&created_at=gt." + encodedCursor + "&order=created_at.asc&limit=50",
                    t
                )
            }

            var newest = cursor
            var notified = 0
            val aiKey = secrets.anthropicKey
            val model = session.aiModel

            for (i in 0 until rows.length()) {
                val e = rows.getJSONObject(i)
                val created = e.optString("created_at")
                if (created > newest) newest = created

                var severity = e.optInt("severity", 2)
                var summaryEn = e.optString("summary_en")
                var summaryHe = e.optString("summary_he")

                // 2) AI second stage for pending events.
                if (e.optString("ai_status") == "pending" && aiKey.isNotBlank()) {
                    try {
                        val verdict = AnthropicClient.analyze(
                            apiKey = aiKey,
                            model = model,
                            ageBand = "child",
                            conversationTitle = e.optString("conversation_title"),
                            excerpt = e.optString("excerpt"),
                            ruleCategory = e.optString("category"),
                            ruleSeverity = severity,
                            signals = e.optString("signals")
                        )
                        severity = verdict.severity
                        summaryEn = verdict.summaryEn.ifBlank { summaryEn }
                        summaryHe = verdict.summaryHe.ifBlank { summaryHe }
                        val patch = JSONObject()
                            .put("ai_status", "done")
                            .put("ai_model", model)
                            .put("severity", verdict.severity)
                            .put("confidence", verdict.confidence)
                            .put("summary_en", summaryEn)
                            .put("summary_he", summaryHe)
                            .put("recommendation_en", verdict.recommendationEn)
                            .put("recommendation_he", verdict.recommendationHe)
                        if (verdict.category != "none") patch.put("category", verdict.category)
                        withAuthRetry { t ->
                            SupabaseApi.update("events", "id=eq." + e.optString("id"), patch, t)
                        }
                    } catch (_: Exception) {
                        try {
                            withAuthRetry { t ->
                                SupabaseApi.update(
                                    "events", "id=eq." + e.optString("id"),
                                    JSONObject().put("ai_status", "failed"), t
                                )
                            }
                        } catch (_: Exception) { /* keep rule-based result */ }
                    }
                }

                // 3) Notify the parent (lock-screen-safe wording).
                if (severity >= session.minNotifySeverity) {
                    val childName = childNameFor(e.optString("child_id"), token)
                    ParentNotifier.notifyParent(context, childName, severity, e.optString("id"))
                    notified++
                }
            }

            if (newest > cursor) store.setCursor(newest)

            // 4) Retention: purge expired events for this family.
            try {
                withAuthRetry { t ->
                    SupabaseApi.delete("events", "retention_expires_at=lt.now()", t)
                }
            } catch (_: Exception) { /* non-fatal */ }

            return rows.length()
        }

        private val childNames = HashMap<String, String>()

        private fun childNameFor(childId: String, token: String): String {
            childNames[childId]?.let { return it }
            return try {
                val arr = SupabaseApi.select(
                    "children", "select=display_name&id=eq.$childId", token
                )
                val name = if (arr.length() > 0)
                    arr.getJSONObject(0).optString("display_name", "your child")
                else "your child"
                childNames[childId] = name
                name
            } catch (_: Exception) { "your child" }
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ParentSyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).setConstraints(netConstraints()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "parent_sync_periodic", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
