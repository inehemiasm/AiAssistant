package com.neo.chevere.ui.settings

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.data.inference.InferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    application: Application,
    private val chatRepository: ChatRepository,
    private val inferenceManager: InferenceManager
) : BaseViewModel<BenchmarkState, BenchmarkIntent, BenchmarkEffect>(application, BenchmarkState()) {

    init {
        updateActiveModelName()
    }

    private fun updateActiveModelName() {
        val model = chatRepository.activeModel ?: inferenceManager.currentModel
        setState { copy(modelName = model?.displayName ?: "No Model Active") }
    }

    override suspend fun handleIntent(intent: BenchmarkIntent) {
        when (intent) {
            BenchmarkIntent.RunBenchmark -> runBenchmark()
            BenchmarkIntent.ClearResult -> setState { copy(result = null, errorMessage = null) }
        }
    }

    private fun runBenchmark() {
        viewModelScope.launch {
            setState { copy(isRunning = true, errorMessage = null) }
            
            // 1. Get active model
            var activeModel = chatRepository.activeModel ?: inferenceManager.currentModel
            
            // 2. If no active model, try to find and load first healthy chat model
            if (activeModel == null) {
                val healthyModels = chatRepository.getLocalModels()
                    .filter { it.isHealthy && (it.taskType == ModelTaskType.CHAT || it.taskType == ModelTaskType.VISION_CHAT) }
                val firstModel = healthyModels.firstOrNull()
                if (firstModel != null) {
                    val loadResult = chatRepository.initializeModel(firstModel.filePath, notify = false)
                    if (loadResult.isSuccess) {
                        activeModel = firstModel
                        updateActiveModelName()
                    } else {
                        setState { 
                            copy(
                                isRunning = false, 
                                errorMessage = "Failed to load candidate model: ${loadResult.exceptionOrNull()?.message}"
                            )
                        }
                        return@launch
                    }
                } else {
                    setState { 
                        copy(
                            isRunning = false, 
                            errorMessage = "No active or healthy chat models found. Please download a model first."
                        )
                    }
                    return@launch
                }
            }

            val model = activeModel
            
            // Measure engine load/warmup latency (already loaded, so verification is fast, <1ms)
            val loadStart = System.currentTimeMillis()
            val loadResult = inferenceManager.loadModel(model)
            val loadTimeMs = System.currentTimeMillis() - loadStart

            if (loadResult is com.neo.chevere.domain.LoadResult.Failure) {
                setState {
                    copy(
                        isRunning = false,
                        errorMessage = "Model engine failed to initialize: ${loadResult.message}"
                    )
                }
                return@launch
            }

            // Prepare memory & hardware info
            val context = getApplication<Application>()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val availMemGb = memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            val totalMemGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val systemRamText = String.format("%.2f GB / %.2f GB", availMemGb, totalMemGb)

            val isGpuEnabled = model.capabilities.contains(ModelCapability.VISION)
            val accelText = if (isGpuEnabled) "GPU (Vision backend)" else "CPU (Standard Text)"

            // Send benchmark prompt
            val prompt = "A three-word response: AI is ready."
            val request = InferenceRequest(prompt)
            
            val startTime = System.currentTimeMillis()
            var firstTokenTime = 0L
            var accumulatedText = ""
            
            inferenceManager.generateStream(request)
                .catch { e ->
                    Timber.tag("BenchmarkViewModel").e(e, "Benchmark generation failed")
                    setState {
                        copy(
                            isRunning = false,
                            errorMessage = "Generation error: ${e.localizedMessage}"
                        )
                    }
                }
                .collect { result ->
                    when (result) {
                        is InferenceResult.Success -> {
                            if (firstTokenTime == 0L && result.text.isNotEmpty()) {
                                firstTokenTime = System.currentTimeMillis()
                            }
                            accumulatedText = result.text
                        }
                        is InferenceResult.Failure -> {
                            throw result.throwable ?: Exception(result.message)
                        }
                        else -> {
                            // Ignore image success
                        }
                    }
                }

            val endTime = System.currentTimeMillis()
            if (accumulatedText.isBlank()) {
                setState {
                    copy(
                        isRunning = false,
                        errorMessage = "Model returned empty response."
                    )
                }
                return@launch
            }

            val ttftMs = if (firstTokenTime > 0) firstTokenTime - startTime else endTime - startTime
            val totalTimeMs = endTime - startTime
            
            // throughput count in approximate tokens (charLength / 4.0)
            val tokenCount = accumulatedText.length / 4.0
            val generationTimeMs = if (firstTokenTime > 0) endTime - firstTokenTime else totalTimeMs
            val generationTimeSec = maxOf(generationTimeMs, 1L) / 1000.0
            val throughputTps = tokenCount / generationTimeSec

            setState {
                copy(
                    isRunning = false,
                    result = BenchmarkMetrics(
                        loadTimeMs = loadTimeMs,
                        ttftMs = ttftMs,
                        throughputTps = throughputTps,
                        totalTimeMs = totalTimeMs,
                        systemRamText = systemRamText,
                        accelText = accelText
                    )
                )
            }
        }
    }
}
