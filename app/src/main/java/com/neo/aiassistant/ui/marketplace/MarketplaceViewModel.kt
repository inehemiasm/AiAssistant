package com.neo.aiassistant.ui.marketplace

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.ui.common.CatalogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            is MarketplaceIntent.SwitchModel -> {
                preferenceManager.updateSelectedModel(intent.modelName)
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
