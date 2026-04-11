package com.neo.aiassistant.di

import android.content.Context
import androidx.room.Room
import com.neo.aiassistant.data.datasource.local.AppDatabase
import com.neo.aiassistant.data.datasource.local.InstalledModelDao
import com.neo.aiassistant.data.datasource.local.RoomInstalledModelRegistry
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
import com.neo.aiassistant.domain.InstalledModelRegistry
import dagger.Binds
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
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideSearchCacheDao(database: AppDatabase): SearchCacheDao {
        return database.searchCacheDao()
    }

    @Provides
    fun provideInstalledModelDao(database: AppDatabase): InstalledModelDao {
        return database.installedModelDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindInstalledModelRegistry(
        impl: RoomInstalledModelRegistry
    ): InstalledModelRegistry
}
