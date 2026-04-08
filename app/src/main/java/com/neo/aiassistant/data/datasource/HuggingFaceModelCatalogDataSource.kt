package com.neo.aiassistant.data.datasource

import com.neo.aiassistant.domain.ModelEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ModelCatalogDataSource that provides curated models from Hugging Face.
 */
@Singleton
class HuggingFaceModelCatalogDataSource @Inject constructor() : ModelCatalogDataSource {
    
    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        // Curated list of LiteRT models from Hugging Face
        val curatedModels = listOf(
            ModelEntry(
                name = "Gemma 2b IT (LiteRT)",
                url = "https://huggingface.co/google/gemma-2b-it-litert/resolve/main/gemma-2b-it.litertlm",
                description = "Google's Gemma 2B instruction-tuned model in LiteRT format.",
                provider = "Hugging Face",
                sizeBytes = 1350000000L, // Approx size
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // Example placeholder
                fileName = "gemma-2b-it.litertlm"
            ),
            ModelEntry(
                name = "Gemma 2 2b IT (LiteRT)",
                url = "https://huggingface.co/google/gemma-2-2b-it-litert/resolve/main/gemma-2-2b-it.litertlm",
                description = "Google's Gemma 2 2B instruction-tuned model in LiteRT format.",
                provider = "Hugging Face",
                sizeBytes = 1600000000L,
                fileName = "gemma-2-2b-it.litertlm"
            )
        )
        return Result.success(curatedModels)
    }
}
