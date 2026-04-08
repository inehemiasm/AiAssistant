package com.neo.aiassistant.data.model

/**
 * Interface for different model sources (e.g., Hugging Face, Curated, Local).
 */
interface ModelSource {
    val name: String
    suspend fun searchModels(query: String): Result<List<MarketplaceModel>>
    suspend fun getModelDetails(modelId: String): Result<MarketplaceModel>
}

/**
 * Status of a model in the marketplace relative to the local device.
 */
sealed interface ModelStatus {
    object NotDownloaded : ModelStatus
    data class Downloading(val progress: Int) : ModelStatus
    data class Downloaded(val localRecord: LocalModelRecord) : ModelStatus
    data class Error(val message: String) : ModelStatus
}
