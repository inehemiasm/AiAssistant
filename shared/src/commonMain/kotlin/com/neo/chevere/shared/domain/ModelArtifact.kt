package com.neo.chevere.shared.domain

/** Metadata needed by shared code to select and load an installed model. */
data class ModelArtifact(
    val id: String,
    val displayName: String,
    val location: ModelLocation,
    val runtime: ModelRuntime,
    val format: ModelFormat,
    val task: ModelTask,
    val capabilities: Set<ModelCapability>
) {
    init {
        require(id.isNotBlank()) { "Model id cannot be blank." }
        require(displayName.isNotBlank()) { "Model display name cannot be blank." }
    }
}

sealed interface ModelLocation {
    data class LocalPath(val value: String) : ModelLocation {
        init {
            require(value.isNotBlank()) { "Model path cannot be blank." }
        }
    }

    data class Uri(val value: String) : ModelLocation {
        init {
            require(value.isNotBlank()) { "Model URI cannot be blank." }
        }
    }
}

enum class ModelRuntime {
    LITERT_LM,
    ONNX_RUNTIME,
    UNKNOWN
}

enum class ModelFormat {
    LITERT_LM,
    ONNX,
    ORT,
    BUNDLE,
    UNKNOWN
}

enum class ModelTask {
    TEXT_GENERATION,
    VISION_LANGUAGE,
    IMAGE_GENERATION,
    UNKNOWN
}

enum class ModelCapability {
    TEXT_INPUT,
    IMAGE_INPUT,
    AUDIO_INPUT,
    TEXT_OUTPUT,
    IMAGE_OUTPUT
}
