package com.safecompanion.data

import android.content.Context
import com.safecompanion.engine.RuleEngine
import com.safecompanion.redact.Redactor
import com.safecompanion.settings.SessionStore
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Child-device pipeline: a captured or reported message flows through here.
 * analyze (local) → redact → local transparency log → outbox for delivery.
 */
class AlertRepository private constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val sessionStore: SessionStore
) {
    private val alertDao = db.alertDao()
    private val recentDao = db.recentMessageDao()
    private val outboxDao = db.outboxDao()

    val childLog: Flow<List<AlertEntity>> = alertDao.observeAll()
    val pendingUploads: Flow<Int> = outboxDao.countPending()

    /**
     * Analyze a freshly captured WhatsApp message on the supervised device.
     * Returns the locally logged alert if it crossed the threshold, else null.
     */
    suspend fun processMessage(
        appPackage: String,
        conversationTitle: String,
        text: String,
        timestampMs: Long
    ): AlertEntity? {
        if (text.isBlank()) return null

        val session = sessionStore.current()
        val key = conversationKey(appPackage, conversationTitle)

        val contextMsgs = recentDao.recentForConversation(key, CONTEXT_WINDOW).reversed()
        recentDao.insert(RecentMessageEntity(conversationKey = key, timestampMs = timestampMs, text = text))
        recentDao.trimConversation(key, CONTEXT_WINDOW)
        sessionStore.incrementAnalyzed()

        val engine = RuleEngine.from(sessionStore.engineConfigFrom(session))
        val result = engine.analyze(text, contextMsgs)
        if (!result.isAlert || result.category == null) return null

        // Map engine 1..3 → spec 5-level scale (2=low, 3=medium, 4=high).
        val severity5 = result.severity.level + 1
        val redacted = Redactor.redact(result.redactedSnippet)

        val entity = AlertEntity(
            timestampMs = timestampMs,
            conversationTitle = conversationTitle,
            appPackage = appPackage,
            categoryId = result.category.id,
            severityLevel = severity5,
            score = result.score,
            explanationEn = result.explanationEn,
            explanationHe = result.explanationHe,
            redactedSnippet = redacted,
            signals = result.signals.joinToString(", ") { it.ruleId }
        )
        val id = alertDao.insert(entity)

        enqueueEvent(
            source = "auto",
            category = result.category.id,
            severity = severity5,
            score = result.score,
            conversationTitle = conversationTitle,
            excerpt = redacted,
            signals = entity.signals,
            summaryEn = result.explanationEn,
            summaryHe = result.explanationHe
        )
        return entity.copy(id = id)
    }

    /** Manual "Report conversation" (typed, pasted, or shared from WhatsApp). */
    suspend fun submitManualReport(text: String, note: String?): AlertEntity {
        val session = sessionStore.current()
        val engine = RuleEngine.from(sessionStore.engineConfigFrom(session))
        val result = engine.analyze(text, emptyList())

        val category = result.category?.id ?: "manual_report"
        val severity5 = if (result.isAlert) (result.severity.level + 1).coerceAtLeast(3) else 3
        val redacted = Redactor.redact(
            if (result.redactedSnippet.isNotBlank()) result.redactedSnippet else text.take(400)
        )
        val summaryEn = "The child reported this conversation for review." +
            (note?.takeIf { it.isNotBlank() }?.let { " Note: $it" } ?: "")
        val summaryHe = "הילד/ה דיווח/ה על השיחה הזו לבדיקה." +
            (note?.takeIf { it.isNotBlank() }?.let { " הערה: $it" } ?: "")

        val entity = AlertEntity(
            timestampMs = System.currentTimeMillis(),
            conversationTitle = "Manual report",
            appPackage = "manual",
            categoryId = category,
            severityLevel = severity5,
            score = result.score,
            explanationEn = summaryEn,
            explanationHe = summaryHe,
            redactedSnippet = redacted,
            signals = result.signals.joinToString(", ") { it.ruleId }
        )
        val id = alertDao.insert(entity)

        enqueueEvent(
            source = "manual_report",
            category = category,
            severity = severity5,
            score = result.score,
            conversationTitle = "Manual report",
            excerpt = redacted,
            signals = entity.signals,
            summaryEn = summaryEn,
            summaryHe = summaryHe
        )
        return entity.copy(id = id)
    }

    /** "Ask for help": a high-priority signal to the parent, no content required. */
    suspend fun askForHelp(message: String?) {
        enqueueEvent(
            source = "help_request",
            category = "help_request",
            severity = 4,
            score = null,
            conversationTitle = null,
            excerpt = message?.let { Redactor.redact(it) },
            signals = null,
            summaryEn = "The child pressed 'Ask for help' and wants to talk.",
            summaryHe = "הילד/ה לחצ/ה על 'בקשת עזרה' ורוצה לדבר."
        )
    }

    private suspend fun enqueueEvent(
        source: String,
        category: String,
        severity: Int,
        score: Int?,
        conversationTitle: String?,
        excerpt: String?,
        signals: String?,
        summaryEn: String,
        summaryHe: String
    ) {
        val payload = JSONObject()
            .put("source", source)
            .put("category", category)
            .put("severity", severity)
            .put("conversation_type", "unknown")
            .put("summary_en", summaryEn)
            .put("summary_he", summaryHe)
        score?.let { payload.put("score", it) }
        conversationTitle?.let { payload.put("conversation_title", it) }
        excerpt?.let { payload.put("excerpt", it) }
        signals?.let { payload.put("signals", it) }

        outboxDao.insert(
            OutboxEntity(createdAtMs = System.currentTimeMillis(), payloadJson = payload.toString())
        )
        com.safecompanion.work.ChildSyncWorker.syncNow(context)
    }

    suspend fun purgeLocal(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
        alertDao.deleteOlderThan(cutoff)
        recentDao.deleteOlderThan(cutoff)
    }

    fun outbox(): OutboxDao = outboxDao

    private fun conversationKey(pkg: String, title: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("$pkg|$title".toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(24)
    }

    companion object {
        private const val CONTEXT_WINDOW = 6

        @Volatile private var INSTANCE: AlertRepository? = null

        fun get(context: Context): AlertRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlertRepository(
                    context.applicationContext,
                    AppDatabase.get(context),
                    SessionStore(context.applicationContext)
                ).also { INSTANCE = it }
            }
    }
}
