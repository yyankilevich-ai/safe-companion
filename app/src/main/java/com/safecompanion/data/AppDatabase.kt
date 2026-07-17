package com.safecompanion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlertEntity::class, RecentMessageEntity::class, OutboxEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun recentMessageDao(): RecentMessageDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safe_companion.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
