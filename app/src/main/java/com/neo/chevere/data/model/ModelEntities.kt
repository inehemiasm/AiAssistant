package com.neo.chevere.data.model

import java.io.File

/**
 * Represents a model available in the marketplace (e.g., Hugging Face).
 */
data class MarketplaceModel(
    val id: String, // e.g., "google/gemma-2b-it-litert"
    val name: String,
    val description: String,
    val author: String,
    val fileSize: Long,
    val downloadUrl: String,
    val repoUrl: String,
    val tags: List<String> = emptyList(),
    val runtimeType: RuntimeType = RuntimeType.LITERT_LM
)

/**
 * Metadata for a model that has been downloaded and registered locally.
 */
data class LocalModelRecord(
    val id: String,
    val name: String,
    val localPath: String,
    val fileSize: Long,
    val runtimeType: RuntimeType,
    val dateDownloaded: Long = System.currentTimeMillis(),
    val hfRevision: String? = null
) {
    val exists: Boolean get() = File(localPath).exists()
}

enum class RuntimeType {
    LITERT_LM,
    MEDIAPIPE_GENAI
}
