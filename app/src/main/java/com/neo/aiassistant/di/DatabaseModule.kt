package com.neo.aiassistant.di

import android.content.Context
import androidx.room.Room
import com.neo.aiassistant.data.datasource.local.AppDatabase
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_assistant_db"
        ).build()
    }

    @Provides
    fun provideSearchCacheDao(database: AppDatabase): SearchCacheDao {
        return database.searchCacheDao()
    }
}
