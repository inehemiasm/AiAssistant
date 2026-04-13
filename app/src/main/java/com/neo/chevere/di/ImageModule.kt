package com.neo.chevere.di

import com.neo.chevere.data.inference.AndroidImageProcessor
import com.neo.chevere.data.inference.ImageProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    @Binds
    @Singleton
    abstract fun bindImageProcessor(
        androidImageProcessor: AndroidImageProcessor
    ): ImageProcessor
}
