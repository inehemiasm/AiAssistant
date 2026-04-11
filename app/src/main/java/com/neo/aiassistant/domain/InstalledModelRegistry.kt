package com.neo.aiassistant.domain

import kotlinx.coroutines.flow.Flow

/**
 * Interface for the registry that tracks all installed models on the device.
 * This serves as the single source of truth for model metadata.
 */
interface InstalledModelRegistry {
    /**
     * Returns a list of all installed models.
     */
    suspend fun getInstalledModels(): List<InstalledModel>

    /**
     * Returns a flow of the list of installed models for real-time updates.
     */
    fun getInstalledModelsFlow(): Flow<List<InstalledModel>>

    /**
     * Retrieves a specific model by its unique identifier.
     */
    suspend fun getInstalledModel(id: String): InstalledModel?

    /**
     * Adds or updates a model in the registry.
     */
    suspend fun upsertInstalledModel(model: InstalledModel)

    /**
     * Removes a model from the registry.
     */
    suspend fun removeInstalledModel(id: String)

    /**
     * Updates the installation status of a model.
     */
    suspend fun updateInstallStatus(id: String, status: InstallStatus)
}
