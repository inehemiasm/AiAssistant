package com.neo.aiassistant.data.model.hf

import com.neo.aiassistant.data.model.MarketplaceModel
import com.neo.aiassistant.data.model.ModelSource
import com.neo.aiassistant.data.model.RuntimeType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class HFModelResponse(
    val id: String,
    val author: String? = null,
    val lastModified: String? = null,
    val siblings: List<HFSibling> = emptyList(),
    val tags: List<String> = emptyList(),
    val downloads: Int = 0
)

@Serializable
data class HFSibling(val rfilename: String)

@Singleton
class HuggingFaceModelSource @Inject constructor(
    private val httpClient: HttpClient
) : ModelSource {
    override val name: String = "Hugging Face"

    private val baseUrl = "https://huggingface.co/api/models"

    override suspend fun searchModels(query: String): Result<List<MarketplaceModel>> = runCatching {
        val response: List<HFModelResponse> = httpClient.get(baseUrl) {
            parameter("search", query)
            parameter("filter", "litert") 
            parameter("full", "true")
            parameter("limit", 20)
        }.body()

        response.mapNotNull { it.toMarketplaceModel() }
    }

    override suspend fun getModelDetails(modelId: String): Result<MarketplaceModel> = runCatching {
        val response: HFModelResponse = httpClient.get("$baseUrl/$modelId") {
            parameter("full", "true")
        }.body()
        
        response.toMarketplaceModel() ?: throw Exception("Incompatible model format")
    }

    private fun HFModelResponse.toMarketplaceModel(): MarketplaceModel? {
        val supportedFile = siblings.find { 
            it.rfilename.endsWith(".litertlm") || 
            it.rfilename.endsWith(".bin") || 
            it.rfilename.endsWith(".tflite") 
        } ?: return null

        return MarketplaceModel(
            id = id,
            name = id.substringAfter("/"),
            description = "On-device model by ${author ?: "HF User"}. Downloads: $downloads",
            author = author ?: "Unknown",
            fileSize = 0,
            downloadUrl = "https://huggingface.co/$id/resolve/main/${supportedFile.rfilename}",
            repoUrl = "https://huggingface.co/$id",
            tags = tags,
            runtimeType = RuntimeType.LITERT_LM
        )
    }
}
