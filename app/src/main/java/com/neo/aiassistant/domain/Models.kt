package com.neo.aiassistant.domain

data class LocalModel(
    val id: String,
    val displayName: String,
    val filePath: String,
    val fileName: String,
    val source: ModelSource,
    val format: ModelFormat,
    val runtime: ModelRuntime,
    val capabilities: Set<ModelCapability>,
    val sizeBytes: Long = 0
)

enum class ModelSource {
    HUGGING_FACE,
    FIREBASE,
    LOCAL,
    UNKNOWN
}

enum class ModelRuntime {
    LITERT,
    QUALCOMM,
    UNKNOWN
}

enum class ModelFormat {
    LITERTLM,
    QNN,
    BIN,
    UNKNOWN
}

enum class ModelCapability {
    TEXT,
    VISION,
    AUDIO
}

sealed class LoadResult {
    object Success : LoadResult()
    data class Failure(val message: String, val throwable: Throwable? = null) : LoadResult()
}

sealed class InferenceResult {
    data class Success(val text: String) : InferenceResult()
    data class Failure(val message: String, val throwable: Throwable? = null) : InferenceResult()
}

data class InferenceRequest(
    val prompt: String,
    val imageUri: android.net.Uri? = null
)
