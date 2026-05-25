package com.neo.chevere.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SearchCacheEntity::class,
        InstalledModelEntity::class,
        TaskEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun installedModelDao(): InstalledModelDao
    abstract fun taskDao(): TaskDao
}
