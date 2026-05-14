package com.neo.chevere.data.datasource

import android.content.Context
import android.util.Log
import com.neo.chevere.core.Constants
import com.neo.chevere.domain.ModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HFDataSource"

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
    val runtimeType: String = "LiteRT",
    val sha256: String? = null,
    val license: String? = null
)

/**
 * Implementation of ModelCatalogDataSource that provides curated models from Hugging Face
 * and also searches the Hugging Face Hub for compatible models.
 */
@Singleton
class HuggingFaceModelCatalogDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient
) : ModelCatalogDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        val allModels = mutableListOf<ModelEntry>()
        
        // 1. Load curated models from assets (Highest Priority)
        try {
            val assetPath = "model_catalog/huggingface_models.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val hfModels = json.decodeFromString<List<HFModelDto>>(jsonString)
            
            allModels.addAll(hfModels.map { dto ->
                ModelEntry(
                    name = dto.displayName,
                    url = dto.downloadUrl,
                    description = "Source: ${dto.repoId} (Curated)",
                    provider = "Hugging Face",
                    sizeBytes = dto.sizeBytes,
                    runtimeType = dto.runtimeType,
                    sha256 = dto.sha256,
                    fileName = dto.fileName,
                    supportsVision = dto.supportsVision,
                    license = dto.license
                )
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error loading curated models", e)
        }

        // 2. Search Hugging Face Hub for compatible models dynamically (Discovery)
        try {
            val response = httpClient.get("https://huggingface.co/api/models") {
                parameter("search", "litertlm")
                parameter("full", "true")
            }
            
            if (response.status.value in 200..299) {
                val hubModels: List<HFHubModel> = response.body()
                val discoveredEntries = hubModels.mapNotNull { hubModel ->
                    // Find a file ending in .litertlm or .bin
                    val sibling = hubModel.siblings.firstOrNull { 
                        it.rfilename.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION) ||
                            it.rfilename.endsWith(Constants.ModelFiles.BIN_EXTENSION)
                    } ?: return@mapNotNull null
                    
                    val targetFile = sibling.rfilename
                    
                    // Phase 5: Duplicate handling - skip if already in curated list
                    if (allModels.any { it.url.contains(hubModel.id) }) {
                        Log.d(TAG, "Skipping duplicate discovered model: ${hubModel.id}")
                        return@mapNotNull null
                    }

                    // Phase 2: Aggressive filtering for raw items
                    if (sibling.size == null || sibling.size < 100 * 1024 * 1024) { // Filter < 100MB
                        Log.d(TAG, "Filtered out low-quality/invalid model: ${hubModel.id}")
                        return@mapNotNull null
                    }

                    // Extract license from tags if available
                    val license = hubModel.tags.find { it.startsWith("license:") }?.removePrefix("license:")

                    ModelEntry(
                        name = hubModel.id.split("/").last(),
                        url = "https://huggingface.co/${hubModel.id}/resolve/main/$targetFile",
                        description = "ID: ${hubModel.id} • ${hubModel.likes} likes • ${hubModel.downloads} downloads",
                        provider = "HF Hub",
                        fileName = targetFile,
                        sizeBytes = sibling.size,
                        supportsVision = hubModel.tags.any { it.contains("vision", ignoreCase = true) },
                        license = license,
                        runtimeType = if (targetFile.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION)) "LiteRT" else "TFLite/Other"
                    )
                }
                allModels.addAll(discoveredEntries)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching HF Hub", e)
        }

        return if (allModels.isEmpty()) {
            Result.failure(Exception("No models found"))
        } else {
            Result.success(allModels)
        }
    }
}
