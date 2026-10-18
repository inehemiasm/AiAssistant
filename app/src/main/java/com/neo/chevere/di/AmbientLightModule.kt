package com.neo.chevere.di

import com.neo.chevere.data.sensors.AndroidAmbientLightRepository
import com.neo.chevere.domain.AmbientLightRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Shares the platform sensor implementation between direct chat and agent tools. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AmbientLightModule {
    @Binds @Singleton
    abstract fun bindAmbientLightRepository(implementation: AndroidAmbientLightRepository): AmbientLightRepository
}
