package com.neo.chevere.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SearchCacheEntity::class,
        InstalledModelEntity::class,
        TaskEntity::class,
        DocumentChunkEntity::class,
        ConversationSessionEntity::class,
        ConversationMessageEntity::class,
        InvoiceEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun installedModelDao(): InstalledModelDao
    abstract fun taskDao(): TaskDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun documentChunkDao(): DocumentChunkDao
    abstract fun conversationHistoryDao(): ConversationHistoryDao
}
