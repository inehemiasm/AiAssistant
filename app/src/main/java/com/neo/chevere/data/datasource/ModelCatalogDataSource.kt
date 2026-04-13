package com.neo.chevere.data.datasource

import com.neo.chevere.domain.ModelEntry

/**
 * Interface for fetching the available models for download.
 */
interface ModelCatalogDataSource {
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
}
