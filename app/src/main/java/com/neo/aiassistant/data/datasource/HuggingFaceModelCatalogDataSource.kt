package com.neo.aiassistant.data.datasource

import android.content.Context
import android.util.Log
import com.neo.aiassistant.domain.ModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class HFModelDto(
    val id: String,
    val displayName: String,
    val source: String,
    val repoId: String,
    val fileName: String,
    val downloadUrl: String,
    val supportsVision: Boolean = false,
    val sizeBytes: Long = 0,
    val sha256: String? = null
)

/**
 * Implementation of ModelCatalogDataSource that provides curated models from Hugging Face.
 */
@Singleton
class HuggingFaceModelCatalogDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelCatalogDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        return try {
            val assetPath = "model_catalog/huggingface_models.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            Log.d("HFDataSource", "asset file found: $assetPath")

            val hfModels = json.decodeFromString<List<HFModelDto>>(jsonString)
            Log.d("HFDataSource", "parsed HF count: ${hfModels.size}")

            val entries = hfModels.map { dto ->
                ModelEntry(
                    name = dto.displayName,
                    url = dto.downloadUrl,
                    description = "Source: ${dto.repoId}",
                    provider = dto.source,
                    sizeBytes = dto.sizeBytes,
                    sha256 = dto.sha256,
                    fileName = dto.fileName,
                    supportsVision = dto.supportsVision
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            Log.e("HFDataSource", "Error loading HF models", e)
            Result.failure(e)
        }
    }
}
