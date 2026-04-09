package com.neo.aiassistant.data.inference

import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import com.neo.aiassistant.domain.LoadResult
import com.neo.aiassistant.domain.LocalModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceManager @Inject constructor(
    private val engineFactory: ModelEngineFactory
) {
    private val mutex = Mutex()
    private var currentModel: LocalModel? = null
    
    private val _currentEngine = MutableStateFlow<ModelEngine?>(null)
    val currentEngine: ModelEngine? get() = _currentEngine.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val initStatus: Flow<String> = _currentEngine.flatMapLatest { engine ->
        engine?.initStatus ?: emptyFlow()
    }

    suspend fun loadModel(model: LocalModel): LoadResult = mutex.withLock {
        if (currentModel?.id == model.id && _currentEngine.value != null) {
            return LoadResult.Success
        }

        // Unload previous engine if it exists
        _currentEngine.value?.unload()
        _currentEngine.value = null
        currentModel = null

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
            currentModel = model
        } else {
            // Clean up on failure
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
        currentModel = null
    }

    fun isVisionSupported(): Boolean {
        return _currentEngine.value?.isVisionSupported() ?: false
    }
}
