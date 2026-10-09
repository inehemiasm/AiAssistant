package com.neo.chevere.data.inference.shared

import android.net.Uri
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.InstalledModelRegistry
import com.neo.chevere.domain.LoadResult
import com.neo.chevere.domain.ModelCapability as AndroidModelCapability
import com.neo.chevere.domain.ModelFormat as AndroidModelFormat
import com.neo.chevere.domain.ModelRuntime as AndroidModelRuntime
import com.neo.chevere.domain.ModelTaskType as AndroidModelTask
import com.neo.chevere.shared.domain.MediaReference
import com.neo.chevere.shared.domain.ModelArtifact
import com.neo.chevere.shared.domain.ModelCapability
import com.neo.chevere.shared.domain.ModelFormat
import com.neo.chevere.shared.domain.ModelLocation
import com.neo.chevere.shared.domain.ModelRuntime
import com.neo.chevere.shared.domain.ModelTask
import com.neo.chevere.shared.inference.ModelLoadResult
import com.neo.chevere.shared.inference.ModelRuntimeState
import com.neo.chevere.shared.inference.RuntimeFailure
import com.neo.chevere.shared.inference.RuntimeFailureCode
import com.neo.chevere.shared.inference.TextGenerationEvent
import com.neo.chevere.shared.inference.TextGenerationRequest
import com.neo.chevere.shared.inference.TextGenerationRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Android implementation of the shared text-generation boundary. */
@Singleton
class AndroidTextGenerationRuntime @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val installedModelRegistry: InstalledModelRegistry
) : TextGenerationRuntime {

    override val state: Flow<ModelRuntimeState> = inferenceManager.initStatus.map { status ->
        status.toSharedState(inferenceManager.currentModel?.id)
    }

    override suspend fun load(model: ModelArtifact): ModelLoadResult {
        if (model.task != ModelTask.TEXT_GENERATION && model.task != ModelTask.VISION_LANGUAGE) {
            return ModelLoadResult.Failed(
                RuntimeFailure(
                    code = RuntimeFailureCode.UNSUPPORTED_MODEL,
                    message = "Model ${model.id} does not support text generation."
                )
            )
        }

        val installedModel = installedModelRegistry.getInstalledModel(model.id)
            ?: return ModelLoadResult.Failed(
                RuntimeFailure(
                    code = RuntimeFailureCode.MODEL_NOT_FOUND,
                    message = "Installed model ${model.id} was not found."
                )
            )

        return when (val result = inferenceManager.loadModel(installedModel)) {
            LoadResult.Success -> ModelLoadResult.Success
            is LoadResult.Failure -> ModelLoadResult.Failed(
                RuntimeFailure(
                    code = RuntimeFailureCode.LOAD_FAILED,
                    message = result.message
                )
            )
        }
    }

    override fun generate(request: TextGenerationRequest): Flow<TextGenerationEvent> = flow {
        val tracker = CumulativeTextTracker()
        var failed = false
        val androidRequest = InferenceRequest(
            prompt = request.prompt,
            imageUri = request.image?.toAndroidUri()
        )

        inferenceManager.generateStream(androidRequest).collect { result ->
            if (failed) return@collect

            when (result) {
                is InferenceResult.Success -> {
                    val delta = tracker.accept(result.text)
                    if (delta.isNotEmpty()) emit(TextGenerationEvent.Delta(delta))
                }
                is InferenceResult.Failure -> {
                    failed = true
                    emit(
                        TextGenerationEvent.Failed(
                            RuntimeFailure(
                                code = result.message.toFailureCode(),
                                message = result.message,
                                isRecoverable = true
                            )
                        )
                    )
                }
                is InferenceResult.ImageSuccess -> {
                    failed = true
                    emit(
                        TextGenerationEvent.Failed(
                            RuntimeFailure(
                                code = RuntimeFailureCode.GENERATION_FAILED,
                                message = "Text runtime returned an image result."
                            )
                        )
                    )
                }
            }
        }

        if (!failed) emit(TextGenerationEvent.Completed(tracker.currentText))
    }

    override suspend fun resetConversation() {
        inferenceManager.clearConversation()
    }

    override suspend fun unload() {
        inferenceManager.unload()
    }
}

internal class CumulativeTextTracker {
    var currentText: String = ""
        private set

    fun accept(snapshot: String): String {
        val delta = if (snapshot.startsWith(currentText)) {
            snapshot.substring(currentText.length)
        } else {
            snapshot
        }
        currentText = snapshot
        return delta
    }
}

internal fun InstalledModel.toSharedArtifact(): ModelArtifact = ModelArtifact(
    id = id,
    displayName = displayName,
    location = ModelLocation.LocalPath(filePath),
    runtime = runtime.toSharedRuntime(),
    format = format.toSharedFormat(),
    task = taskType.toSharedTask(),
    capabilities = capabilities.flatMapTo(mutableSetOf()) { it.toSharedCapabilities() }
)

private fun InitializationStatus.toSharedState(modelId: String?): ModelRuntimeState = when (this) {
    InitializationStatus.Uninitialized -> ModelRuntimeState.Idle
    is InitializationStatus.Initializing -> ModelRuntimeState.Loading(message)
    InitializationStatus.Ready -> ModelRuntimeState.Ready(modelId)
    is InitializationStatus.Failure -> ModelRuntimeState.Failed(
        RuntimeFailure(RuntimeFailureCode.LOAD_FAILED, message)
    )
}

private fun MediaReference.toAndroidUri(): Uri = when (this) {
    is MediaReference.LocalFile -> Uri.fromFile(File(path))
    is MediaReference.Uri -> Uri.parse(value)
}

private fun String.toFailureCode(): RuntimeFailureCode =
    if (contains("No model loaded", ignoreCase = true)) {
        RuntimeFailureCode.NO_MODEL_LOADED
    } else {
        RuntimeFailureCode.GENERATION_FAILED
    }

private fun AndroidModelRuntime.toSharedRuntime(): ModelRuntime = when (this) {
    AndroidModelRuntime.LITERT -> ModelRuntime.LITERT_LM
    AndroidModelRuntime.ONNX_DIFFUSION -> ModelRuntime.ONNX_RUNTIME
    AndroidModelRuntime.IMAGE_GENERATOR,
    AndroidModelRuntime.UNKNOWN -> ModelRuntime.UNKNOWN
}

private fun AndroidModelFormat.toSharedFormat(): ModelFormat = when (this) {
    AndroidModelFormat.LITERTLM -> ModelFormat.LITERT_LM
    AndroidModelFormat.ONNX_DIFFUSION_BUNDLE,
    AndroidModelFormat.IMAGE_GENERATOR_BUNDLE -> ModelFormat.BUNDLE
    AndroidModelFormat.BIN,
    AndroidModelFormat.UNKNOWN -> ModelFormat.UNKNOWN
}

private fun AndroidModelTask.toSharedTask(): ModelTask = when (this) {
    AndroidModelTask.CHAT -> ModelTask.TEXT_GENERATION
    AndroidModelTask.VISION_CHAT -> ModelTask.VISION_LANGUAGE
    AndroidModelTask.IMAGE_GENERATION -> ModelTask.IMAGE_GENERATION
    AndroidModelTask.UNKNOWN -> ModelTask.UNKNOWN
}

private fun AndroidModelCapability.toSharedCapabilities(): Set<ModelCapability> = when (this) {
    AndroidModelCapability.TEXT -> setOf(
        ModelCapability.TEXT_INPUT,
        ModelCapability.TEXT_OUTPUT
    )
    AndroidModelCapability.VISION -> setOf(ModelCapability.IMAGE_INPUT)
    AndroidModelCapability.AUDIO -> setOf(ModelCapability.AUDIO_INPUT)
    AndroidModelCapability.IMAGE_GEN -> setOf(ModelCapability.IMAGE_OUTPUT)
}
