package com.neo.aiassistant.data.datasource

import com.neo.aiassistant.domain.ModelEntry

/**
 * Interface for fetching the available models for download.
 */
interface ModelCatalogDataSource {
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
}
