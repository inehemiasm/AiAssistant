package com.neo.aiassistant.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SearchCacheEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
}
