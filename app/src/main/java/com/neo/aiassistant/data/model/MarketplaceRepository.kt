package com.neo.aiassistant.data.model

import com.neo.aiassistant.data.model.hf.HuggingFaceModelSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketplaceRepository @Inject constructor(
    private val hfSource: HuggingFaceModelSource
) {
    suspend fun searchModels(query: String): Result<List<MarketplaceModel>> {
        return hfSource.searchModels(query)
    }

    suspend fun getModelDetails(modelId: String): Result<MarketplaceModel> {
        return hfSource.getModelDetails(modelId)
    }
}
