package com.neo.chevere.domain

/**
 * Represents a machine learning model stored locally on the device.
 * This is the new source of truth for installed models.
 */
data class InstalledModel(
    val id: String,
    val displayName: String,
    val filePath: String,
    val fileName: String,
    val source: ModelSource,
    val format: ModelFormat,
    val runtime: ModelRuntime,
    val taskType: ModelTaskType,
    val capabilities: Set<ModelCapability>,
    val installStatus: InstallStatus,
    val sizeBytes: Long? = null,
    val checksum: String? = null,
    val installedAt: Long? = null,
    val license: String? = null
) {
    /**
     * Helper to check if the model is in a healthy, usable state.
     */
    val isHealthy: Boolean get() = installStatus == InstallStatus.INSTALLED
    
    /**
     * Helper to check if the model is currently undergoing a lifecycle transition.
     */
    val isTransitioning: Boolean get() = installStatus == InstallStatus.DOWNLOADING || 
            installStatus == InstallStatus.VERIFYING
}

/**
 * Defines the possible sources from which a model can be acquired.
 */
enum class ModelSource {
    HUGGING_FACE,
    FIREBASE,
    KAGGLE,
    LOCAL,
    UNKNOWN
}

/**
 * Defines the supported runtimes for executing models.
 */
enum class ModelRuntime {
    LITERT,
    QUALCOMM,
    UNKNOWN
}

/**
 * Defines the supported model file formats.
 */
enum class ModelFormat {
    LITERTLM,
    QNN,
    BIN,
    UNKNOWN
}

/**
 * Defines the types of tasks a model is designed for.
 */
enum class ModelTaskType {
    CHAT,
    VISION_CHAT,
    IMAGE_GENERATION,
    UNKNOWN
}

/**
 * Defines the capabilities a model might possess.
 */
enum class ModelCapability {
    TEXT,
    VISION,
    AUDIO,
    IMAGE_GEN
}

/**
 * Defines the installation status of a model.
 */
enum class InstallStatus {
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    FAILED,
    INVALID,
    CORRUPTED,
    UNSUPPORTED,
    PENDING_DELETE
}

/**
 * Represents the result of an attempt to load a model.
 */
sealed class LoadResult {
    /** Indicates the model was loaded successfully. */
    object Success : LoadResult()
    /** 
     * Indicates the model failed to load. 
     * @property message A description of the failure.
     * @property throwable The exception that caused the failure, if any.
     */
    data class Failure(val message: String, val throwable: Throwable? = null) : LoadResult()
}

/**
 * Represents the result of a model inference operation.
 */
sealed class InferenceResult {
    /** 
     * Indicates the inference was successful.
     * @property text The generated response text.
     */
    data class Success(val text: String) : InferenceResult()
    /** 
     * Indicates the inference failed.
     * @property message A description of the failure.
     * @property throwable The exception that caused the failure, if any.
     */
    data class Failure(val message: String, val throwable: Throwable? = null) : InferenceResult()
}

/**
 * Represents a request for model inference.
 *
 * @property prompt The input text for the model.
 * @property imageUri Optional URI of an image to be processed by the model.
 */
data class InferenceRequest(
    val prompt: String,
    val imageUri: android.net.Uri? = null
)

/**
 * Represents the progress of a model download.
 */
sealed class DownloadProgress {
    /** 
     * Indicates the download is in progress.
     * @property percent The download percentage (0-100).
     */
    data class Progress(val percent: Int) : DownloadProgress()
    
    /** Indicates the download finished successfully. */
    object Finished : DownloadProgress()
    
    /** 
     * Indicates the download failed.
     * @property message A description of the failure.
     */
    data class Error(val message: String) : DownloadProgress()
}
