package com.neo.chevere.data.inference

import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle and execution of AI model inference engines.
 */
@Singleton
class InferenceManager @Inject constructor(
    private val engineFactory: ModelEngineFactory
) {
    private val mutex = Mutex()
    private var _currentModel: InstalledModel? = null

    /**
     * The model currently loaded in the engine.
     */
    val currentModel: InstalledModel? get() = _currentModel

    private val _currentEngine = MutableStateFlow<ModelEngine?>(null)

    /**
     * The currently active inference engine, or `null` if no model is loaded.
     */
    val currentEngine: ModelEngine? get() = _currentEngine.value

    /**
     * A [Flow] emitting the initialization status of the current engine.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val initStatus: Flow<InitializationStatus> = _currentEngine.flatMapLatest { engine ->
        engine?.initStatus ?: flowOf(InitializationStatus.Uninitialized)
    }

    /**
     * Loads the specified [InstalledModel] into an appropriate engine.
     */
    suspend fun loadModel(model: InstalledModel): LoadResult = mutex.withLock {
        if (_currentModel?.id == model.id && _currentEngine.value != null) {
            return LoadResult.Success
        }

        // Unload previous engine if it exists
        _currentEngine.value?.unload()
        _currentEngine.value = null
        _currentModel = null

        val engine = try {
            engineFactory.getEngine(model.runtime)
        } catch (e: Exception) {
            return LoadResult.Failure("Failed to get engine for runtime ${model.runtime}: ${e.message}")
        }

        // Set the engine before loading so initStatus updates can be observed
        _currentEngine.value = engine

        val result = try {
            engine.load(model)
        } catch (e: Exception) {
            LoadResult.Failure("Exception during engine load: ${e.message}", e)
        }

        if (result is LoadResult.Success) {
            _currentModel = model
        } else {
            _currentEngine.value = null
        }
        return result
    }

    suspend fun generate(request: InferenceRequest): InferenceResult = mutex.withLock {
        val engine = _currentEngine.value ?: return InferenceResult.Failure("No model loaded")
        return engine.generate(request)
    }

    suspend fun clearConversation() = mutex.withLock {
        _currentEngine.value?.clearConversation()
    }

    suspend fun unload() = mutex.withLock {
        _currentEngine.value?.unload()
        _currentEngine.value = null
        _currentModel = null
    }

    fun isVisionSupported(): Boolean {
        return _currentEngine.value?.isVisionSupported() ?: false
    }
}
