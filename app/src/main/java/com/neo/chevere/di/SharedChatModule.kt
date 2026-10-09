package com.neo.chevere.di

import com.neo.chevere.shared.chat.GenerateChatTurn
import com.neo.chevere.shared.inference.TextGenerationRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Assembles portable chat orchestration with the Android runtime binding. */
@Module
@InstallIn(SingletonComponent::class)
object SharedChatModule {
    @Provides
    @Singleton
    fun provideGenerateChatTurn(runtime: TextGenerationRuntime): GenerateChatTurn = GenerateChatTurn(runtime)
}
