package com.safecompanion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts")
    fun countAll(): Flow<Int>

    @Query("DELETE FROM alerts WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

@Dao
interface RecentMessageDao {

    @Insert
    suspend fun insert(msg: RecentMessageEntity)

    @Query("SELECT text FROM recent_messages WHERE conversationKey = :key ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun recentForConversation(key: String, limit: Int): List<String>

    @Query(
        "DELETE FROM recent_messages WHERE conversationKey = :key AND id NOT IN " +
            "(SELECT id FROM recent_messages WHERE conversationKey = :key ORDER BY timestampMs DESC LIMIT :keep)"
    )
    suspend fun trimConversation(key: String, keep: Int)

    @Query("DELETE FROM recent_messages WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY createdAtMs ASC LIMIT 20")
    suspend fun batch(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    fun countPending(): Flow<Int>
}
