package com.neo.aiassistant.ui.models

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState
import com.neo.aiassistant.ui.common.PerformanceMetrics

/**
 * The UI state for the AI Models screen.
 *
 * @property localModels List of models currently stored on the device.
 * @property remoteModels List of all models available in the remote catalog.
 * @property selectedModel The identifier of the currently selected/active model.
 * @property isDownloading Whether a model download is currently active.
 * @property downloadProgress The current progress percentage of the active download.
 * @property catalogState The current status of fetching the remote catalog.
 * @property metrics Performance data for the active model (latency, throughput, etc.).
 */
data class ModelsState(
    val localModels: List<InstalledModel> = emptyList(),
    val remoteModels: List<ModelEntry> = emptyList(),
    val selectedModel: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Int? = null,
    val catalogState: CatalogState = CatalogState.Idle,
    val metrics: PerformanceMetrics = PerformanceMetrics()
) : UiState {
    /**
     * Filters [remoteModels] to find those that are not yet downloaded locally.
     */
    val availableDownloads: List<ModelEntry> get() {
        val downloadedFileNames = localModels.map { it.fileName }.toSet()
        return remoteModels.filter { it.effectiveFileName !in downloadedFileNames }
    }
}

/**
 * User intents (actions) that can be performed on the Models screen.
 */
sealed class ModelsIntent : UiIntent {
    /** Request to fetch available models from the catalog. */
    data object FetchModels : ModelsIntent()
    
    /** 
     * Request to switch the active AI model.
     * @property modelName The name of the model to switch to.
     * @property baseDir The directory where the model file is located.
     */
    data class SwitchModel(val modelName: String, val baseDir: String) : ModelsIntent()
    
    /** 
     * Request to start downloading a model.
     * @property modelName The name of the model to download.
     * @property baseDir The directory where the model should be saved.
     */
    data class DownloadModel(val modelName: String, val baseDir: String) : ModelsIntent()
    
    /** Request to clear any active error states. */
    data object ClearError : ModelsIntent()
    
    /** Request to update performance metrics for the active model. */
    data object RefreshMetrics : ModelsIntent()
}

/**
 * One-time side effects that can be triggered from the Models ViewModel.
 */
sealed class ModelsEffect : UiEffect {
    /** Request to show a toast message. @property message The message to show. */
    data class ShowToast(val message: String) : ModelsEffect()
}
