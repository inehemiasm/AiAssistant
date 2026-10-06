package com.neo.chevere.di

import com.neo.chevere.data.TaskRepositoryImpl
import com.neo.chevere.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the domain task boundary to ViewModels and agent tools. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(implementation: TaskRepositoryImpl): TaskRepository
}
