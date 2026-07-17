package com.safecompanion.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Child-device local log of safety findings that were shared with the parent.
 * Fulfills spec §13: the child can see when an alert was shared. Stores only
 * matched fragments — never full conversations.
 */
@Entity(tableName = "alerts", indices = [Index("timestampMs")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val conversationTitle: String,
    val appPackage: String,
    val categoryId: String,
    val severityLevel: Int,          // 1..5
    val score: Int,
    val explanationEn: String,
    val explanationHe: String,
    val redactedSnippet: String,
    val signals: String,
    val seenByParent: Boolean = false
)

/**
 * Short-lived record of a recent message, used only as conversation context
 * for the rule engine. Capped per conversation, purged daily.
 */
@Entity(tableName = "recent_messages", indices = [Index("conversationKey"), Index("timestampMs")])
data class RecentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationKey: String,
    val timestampMs: Long,
    val text: String
)

/**
 * Offline queue: safety events waiting to be delivered to the family backend.
 */
@Entity(tableName = "outbox", indices = [Index("createdAtMs")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMs: Long,
    val payloadJson: String,
    val attempts: Int = 0
)
