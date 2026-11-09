package com.neo.chevere.di

import com.neo.chevere.data.purchases.RevenueCatSandboxRepository
import com.neo.chevere.domain.PurchaseSandboxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Provides a separate Test Store catalog snapshot for each purchase screen ViewModel. */
@Module
@InstallIn(ViewModelComponent::class)
abstract class PurchaseSandboxModule {
    @Binds
    abstract fun bindSandboxRepository(implementation: RevenueCatSandboxRepository): PurchaseSandboxRepository
}
