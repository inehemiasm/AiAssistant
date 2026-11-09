package com.neo.chevere.di

import com.neo.chevere.data.voice.AndroidSpeechSynthesizer
import com.neo.chevere.domain.SpeechSynthesizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds platform voice output used by read-aloud and hands-free conversation mode. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds @Singleton
    abstract fun bindSpeechSynthesizer(implementation: AndroidSpeechSynthesizer): SpeechSynthesizer
}
