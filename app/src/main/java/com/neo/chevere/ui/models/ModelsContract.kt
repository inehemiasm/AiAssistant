package com.neo.chevere.ui.models

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.ui.common.CatalogState
import com.neo.chevere.ui.common.PerformanceMetrics
import com.neo.chevere.ui.marketplace.ModelSwitchState

/**
 * The UI state for the AI Models screen.
 *
 * @property localModels List of models currently stored on the device.
 * @property remoteModels List of all models available in the remote catalog.
 * @property selectedModel The identifier of the currently selected/active model.
 * @property pendingModel The identifier of the model the user has selected in the UI but not yet confirmed.
 * @property isDownloading Whether a model download is currently active.
 * @property downloadProgress The current progress percentage of the active download.
 * @property catalogState The current status of fetching the remote catalog.
 * @property metrics Performance data for the active model (latency, throughput, etc.).
 * @property switchState The current state of an engine switch operation.
 */
data class ModelsState(
    val localModels: List<InstalledModel> = emptyList(),
    val remoteModels: List<ModelEntry> = emptyList(),
    val selectedModel: String = "",
    val pendingModel: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int? = null,
    val catalogState: CatalogState = CatalogState.Idle,
    val metrics: PerformanceMetrics = PerformanceMetrics(),
    val switchState: ModelSwitchState = ModelSwitchState.Idle
) : UiState {
    /**
     * Filters [remoteModels] to find those that are not yet downloaded locally.
     */
    val availableDownloads: List<ModelEntry> get() {
        val downloadedFileNames = localModels.map { it.fileName }.toSet()
        return remoteModels.filter { it.effectiveFileName !in downloadedFileNames }
    }

    val isSwitching: Boolean get() = switchState is ModelSwitchState.Switching || switchState is ModelSwitchState.WarmingUp
}

/**
 * User intents (actions) that can be performed on the Models screen.
 */
sealed class ModelsIntent : UiIntent {
    /** Request to fetch available models from the catalog. */
    data object FetchModels : ModelsIntent()
    
    /** 
     * Request to select a model in the UI (pending confirmation).
     * @property modelName The name of the model to select.
     */
    data class SelectModel(val modelName: String) : ModelsIntent()

    /** 
     * Request to confirm and switch the active AI model.
     * @property modelName The name of the model to switch to.
     * @property modelPath The absolute path to the model file.
     */
    data class ConfirmSwitch(val modelName: String, val modelPath: String) : ModelsIntent()
    
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

    /** Old intent kept for compatibility if needed, but should use ConfirmSwitch */
    data class SwitchModel(val modelName: String, val baseDir: String) : ModelsIntent()
}

/**
 * One-time side effects that can be triggered from the Models ViewModel.
 */
sealed class ModelsEffect : UiEffect {
    /** Request to show a toast message. @property message The message to show. */
    data class ShowToast(val message: String) : ModelsEffect()
}
