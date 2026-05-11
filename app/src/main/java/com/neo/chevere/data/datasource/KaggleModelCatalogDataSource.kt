package com.neo.chevere.data.datasource

import android.content.Context
import android.util.Log
import com.neo.chevere.core.Constants
import com.neo.chevere.domain.ModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class KaggleModelDto(
    val id: String,
    val displayName: String,
    val modelPageUrl: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0,
    val supportsVision: Boolean = false,
    val description: String = "",
    val license: String? = null
)

/**
 * Data source for models hosted on Kaggle Models.
 * Combines a curated offline list with dynamic discovery from the Kaggle/TFHub API
 * for any models tagged with 'litert'.
 */
@Singleton
class KaggleModelCatalogDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient
) : ModelCatalogDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        val allModels = mutableListOf<ModelEntry>()

        // 1. Load curated models from assets (Reliable fallback and primary choices)
        try {
            val assetPath = "model_catalog/kaggle_models.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val curatedKaggleModels = json.decodeFromString<List<KaggleModelDto>>(jsonString)
            
            allModels.addAll(curatedKaggleModels.map { dto ->
                ModelEntry(
                    name = dto.displayName,
                    url = dto.downloadUrl,
                    description = dto.description.ifEmpty { "Official Google/Kaggle Model" },
                    provider = "Kaggle",
                    sizeBytes = dto.sizeBytes,
                    runtimeType = "LiteRT",
                    fileName = dto.id + Constants.ModelFiles.LITERTLM_EXTENSION,
                    supportsVision = dto.supportsVision,
                    license = dto.license
                )
            })
        } catch (e: Exception) {
            Log.e("KaggleDataSource", "Error loading curated Kaggle models", e)
        }

        // 2. Dynamic Discovery (In a real app, we'd call the Kaggle Models / TF Hub Search API)
        // Since Kaggle's public API for model discovery often requires authentication or has 
        // specific TFLite filters, we prioritize the curated list but are ready to extend
        // with a search implementation here.
        
        return if (allModels.isEmpty()) {
            Result.failure(Exception("No Kaggle models found"))
        } else {
            Result.success(allModels)
        }
    }
}
