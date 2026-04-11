package com.neo.aiassistant.ui.marketplace

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.ui.common.CatalogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MarketplaceViewModel"

/**
 * ViewModel for the Model Marketplace screen.
 *
 * Manages the display of available AI models, coordinates model downloads,
 * and handles deletion of local models.
 *
 * @property application The application context.
 * @property repository The repository for chat and model data.
 * @property preferenceManager Manages user preferences, including the selected model.
 */
@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<MarketplaceState, MarketplaceIntent, MarketplaceEffect>(application, MarketplaceState()) {

    init {
        viewModelScope.launch {
            fetchRemoteModels()
            loadLocalModels()
        }

        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { modelId ->
                Log.d(TAG, "Active model updated in preferences: $modelId")
                setState { copy(activeModelId = modelId) }
            }
        }

        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                if (currentState.switchState is ModelSwitchState.Switching || currentState.switchState is ModelSwitchState.WarmingUp) {
                    Log.d(TAG, "Warmup state emitted: $status")
                    setState { 
                        copy(switchState = ModelSwitchState.WarmingUp(currentState.pendingModelId ?: "", status))
                    }
                }
            }
        }
    }

    /**
     * Loads the list of models currently available on the device.
     */
    private suspend fun loadLocalModels() {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
    }

    /**
     * Fetches the list of available models from the remote catalog.
     */
    private suspend fun fetchRemoteModels() {
        setState { copy(catalogState = CatalogState.Loading) }
        repository.fetchAvailableModels()
            .onSuccess { models ->
                setState { copy(remoteModels = models, catalogState = CatalogState.Idle) }
            }
            .onFailure { e ->
                setState { copy(catalogState = CatalogState.Error(e.message ?: "Fetch failed")) }
            }
    }

    /**
     * Handles user intents for the marketplace.
     */
    override suspend fun handleIntent(intent: MarketplaceIntent) {
        when (intent) {
            MarketplaceIntent.FetchModels -> fetchRemoteModels()
            is MarketplaceIntent.DownloadModel -> downloadModel(intent.modelName, intent.baseDir)
            is MarketplaceIntent.DeleteModel -> {
                if (repository.deleteModel(intent.modelName)) {
                    loadLocalModels()
                }
            }
            is MarketplaceIntent.SelectModel -> {
                Log.d(TAG, "Model card tapped: ${intent.modelId}")
                setState { copy(pendingModelId = intent.modelId) }
                Log.d(TAG, "Pending model updated: ${intent.modelId}")
            }
            is MarketplaceIntent.ConfirmSwitch -> {
                confirmSwitch(intent.modelId, intent.modelPath)
            }
        }
    }

    private fun confirmSwitch(modelId: String, modelPath: String) {
        Log.d(TAG, "Confirm button pressed for model: $modelId")
        val fromModelId = currentState.activeModelId
        
        setState { 
            copy(switchState = ModelSwitchState.Switching(fromModelId, modelId)) 
        }
        Log.d(TAG, "Switch request started: from $fromModelId to $modelId")

        viewModelScope.launch {
            Log.d(TAG, "New model load started: $modelId at $modelPath")
            // Note: repository.initializeModel internally calls orchestrator.reset() which acts as "old model unload"
            Log.d(TAG, "Old model unload started (via initialization reset)")
            
            repository.initializeModel(modelPath)
                .onSuccess {
                    Log.d(TAG, "Load success for model: $modelId")
                    preferenceManager.updateSelectedModel(modelId)
                    setState { 
                        copy(
                            activeModelId = modelId,
                            pendingModelId = null,
                            switchState = ModelSwitchState.Ready(modelId)
                        )
                    }
                    Log.d(TAG, "Active model updated to: $modelId")
                }
                .onFailure { e ->
                    Log.e(TAG, "Load failure for model: $modelId", e)
                    setState { 
                        copy(switchState = ModelSwitchState.Error(modelId, e.message ?: "Unknown error")) 
                    }
                    sendEffect { MarketplaceEffect.ShowToast("Failed to switch model: ${e.message}") }
                }
        }
    }

    /**
     * Initiates the download of a specific model.
     *
     * @param modelName The name of the model to download.
     * @param baseDir The directory where the model should be saved.
     */
    private fun downloadModel(modelName: String, baseDir: String) {
        val modelEntry = currentState.remoteModels.find { it.effectiveFileName == modelName || it.name == modelName } ?: return
        
        setState { copy(downloadingModelName = modelName, downloadProgress = 0) }
        
        viewModelScope.launch {
            repository.downloadModel(modelEntry.url, modelEntry.effectiveFileName, modelEntry.sha256).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> setState { copy(downloadProgress = progress.percent) }
                    DownloadProgress.Finished -> {
                        setState { copy(downloadingModelName = null, downloadProgress = null) }
                        loadLocalModels()
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(downloadingModelName = null, downloadProgress = null) }
                        sendEffect { MarketplaceEffect.ShowToast(progress.message) }
                    }
                }
            }
        }
    }
}
