package com.neo.chevere.data.model.hf

import com.neo.chevere.core.Constants
import com.neo.chevere.data.model.MarketplaceModel
import com.neo.chevere.data.model.ModelSource
import com.neo.chevere.data.model.RuntimeType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
        // We search for both 'litert' and potentially 'stable-diffusion' or 'onnx' related tags
        // However, HF API 'filter' parameter usually takes one value. 
        // We'll broaden the search by removing the strict 'litert' filter if a query is provided,
        // or we can perform two fetches. For now, let's allow general search but prioritize our formats.
        val response: List<HFModelResponse> = httpClient.get(baseUrl) {
            parameter("search", query)
            // If query is empty, default to litert models. If searching, don't filter to allow finding image models.
            if (query.isBlank()) {
                parameter("filter", "litert")
            }
            parameter("full", "true")
            parameter("limit", 30)
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
        // 1. Check for LiteRT/TFLite models (Chat/Vision)
        val liteRtFile = siblings.find {
            it.rfilename.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION) ||
                    it.rfilename.endsWith(Constants.ModelFiles.BIN_EXTENSION) ||
                    it.rfilename.endsWith(".tflite")
        }

        if (liteRtFile != null) {
            return MarketplaceModel(
                id = id,
                name = id.substringAfter("/"),
                description = "On-device LLM by ${author ?: "HF User"}. Downloads: $downloads",
                author = author ?: "Unknown",
                fileSize = 0,
                downloadUrl = "https://huggingface.co/$id/resolve/main/${liteRtFile.rfilename}",
                repoUrl = "https://huggingface.co/$id",
                tags = tags,
                runtimeType = RuntimeType.LITERT_LM
            )
        }

        // 2. Check for ONNX Diffusion bundles (ZIP)
        // We look for .zip files if the model tags suggest it's a diffusion/image model
        val isDiffusion = tags.any {
            it.contains(
                "stable-diffusion",
                ignoreCase = true
            ) || it.contains("diffusion", ignoreCase = true)
        }
        val zipFile = siblings.find { it.rfilename.endsWith(Constants.ModelFiles.ZIP_EXTENSION) }

        if (isDiffusion && zipFile != null) {
            return MarketplaceModel(
                id = id,
                name = id.substringAfter("/"),
                description = "Image generation model by ${author ?: "HF User"}. Downloads: $downloads",
                author = author ?: "Unknown",
                fileSize = 0,
                downloadUrl = "https://huggingface.co/$id/resolve/main/${zipFile.rfilename}",
                repoUrl = "https://huggingface.co/$id",
                tags = tags,
                runtimeType = RuntimeType.ONNX_DIFFUSION
            )
        }

        return null
    }
}
