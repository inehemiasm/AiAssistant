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
 * Data source for models hosted on Kaggle Models (formerly TF Hub).
 * Currently uses a curated list for guaranteed LiteRT/TFLite compatibility.
 */
@Singleton
class KaggleModelCatalogDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelCatalogDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        return try {
            val assetPath = "model_catalog/kaggle_models.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val kaggleModels = json.decodeFromString<List<KaggleModelDto>>(jsonString)
            
            val entries = kaggleModels.map { dto ->
                ModelEntry(
                    name = dto.displayName,
                    url = dto.downloadUrl,
                    description = dto.description.ifEmpty { "Source: Kaggle Models / TF Hub" },
                    provider = "Kaggle",
                    sizeBytes = dto.sizeBytes,
                    runtimeType = "LiteRT",
                    fileName = dto.id + ".litertlm",
                    supportsVision = dto.supportsVision,
                    license = dto.license
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            Log.e("KaggleDataSource", "Error loading Kaggle models", e)
            Result.failure(e)
        }
    }
}
