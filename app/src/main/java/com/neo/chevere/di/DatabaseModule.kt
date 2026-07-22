package com.neo.chevere.di

import android.content.Context
import androidx.room.Room
import com.neo.chevere.data.ChatHistoryRepositoryImpl
import com.neo.chevere.data.datasource.local.AppDatabase
import com.neo.chevere.data.datasource.local.ConversationHistoryDao
import com.neo.chevere.data.datasource.local.InstalledModelDao
import com.neo.chevere.data.datasource.local.RoomInstalledModelRegistry
import com.neo.chevere.data.datasource.local.SearchCacheDao
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.datasource.local.InvoiceDao
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.domain.ChatHistoryRepository
import com.neo.chevere.domain.InstalledModelRegistry
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
            .fallbackToDestructiveMigration(dropAllTables = true)
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

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideInvoiceDao(database: AppDatabase): InvoiceDao {
        return database.invoiceDao()
    }

    @Provides
    fun provideDocumentChunkDao(database: AppDatabase): DocumentChunkDao {
        return database.documentChunkDao()
    }

    @Provides
    fun provideConversationHistoryDao(database: AppDatabase): ConversationHistoryDao {
        return database.conversationHistoryDao()
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

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(
        impl: ChatHistoryRepositoryImpl
    ): ChatHistoryRepository
}
