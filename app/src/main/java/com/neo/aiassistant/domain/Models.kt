package com.neo.aiassistant.domain

/**
 * Represents a machine learning model stored locally on the device.
 *
 * @property id Unique identifier for the model.
 * @property displayName Human-readable name of the model.
 * @property filePath Absolute path to the model file.
 * @property fileName Name of the model file.
 * @property source The origin of the model (e.g., Hugging Face, Firebase).
 * @property format The file format of the model.
 * @property runtime The runtime environment required for the model.
 * @property capabilities The set of tasks the model can perform.
 * @property sizeBytes Size of the model file in bytes.
 */
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

/**
 * Defines the possible sources from which a model can be acquired.
 */
enum class ModelSource {
    HUGGING_FACE,
    FIREBASE,
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
 * Defines the capabilities a model might possess.
 */
enum class ModelCapability {
    TEXT,
    VISION,
    AUDIO
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
