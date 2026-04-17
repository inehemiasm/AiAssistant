package com.neo.chevere.ui.marketplace

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.ui.common.CatalogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MarketplaceViewModel"

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
                setState { copy(activeModelId = modelId) }
            }
        }

        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                val currentSwitchState = currentState.switchState
                if (currentSwitchState is ModelSwitchState.Switching || currentSwitchState is ModelSwitchState.WarmingUp) {
                    val modelId = when (currentSwitchState) {
                        is ModelSwitchState.Switching -> currentSwitchState.toModelId
                        is ModelSwitchState.WarmingUp -> currentSwitchState.modelId
                        else -> currentState.pendingModelId ?: ""
                    }
                    setState { 
                        copy(switchState = ModelSwitchState.WarmingUp(modelId, status))
                    }
                }
            }
        }
    }

    private suspend fun loadLocalModels() {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
    }

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

    override suspend fun handleIntent(intent: MarketplaceIntent) {
        when (intent) {
            MarketplaceIntent.FetchModels -> fetchRemoteModels()
            is MarketplaceIntent.DownloadModel -> {
                if (currentState.isDownloading) {
                    sendEffect { MarketplaceEffect.ShowToast("Another download is in progress") }
                    return
                }
                downloadModel(intent.model.effectiveFileName, intent.baseDir, intent.model.url, intent.model.sha256)
            }
            is MarketplaceIntent.DeleteModel -> {
                if (intent.modelId == currentState.activeModelId) {
                    sendEffect { MarketplaceEffect.ShowToast("Cannot delete active model. Switch models first.") }
                    return
                }
                
                val model = currentState.localModels.find { it.id == intent.modelId }
                if (model?.isTransitioning == true || currentState.isSwitching) {
                    sendEffect { MarketplaceEffect.ShowToast("Cannot delete while model is busy.") }
                    return
                }

                if (repository.deleteModel(intent.modelId)) {
                    loadLocalModels()
                }
            }
            is MarketplaceIntent.SelectModel -> {
                val model = currentState.localModels.find { it.id == intent.modelId }
                
                if (model?.installStatus != InstallStatus.INSTALLED) {
                    sendEffect { MarketplaceEffect.ShowToast("Model is not ready for selection.") }
                    return
                }
                
                setState { copy(pendingModelId = intent.modelId) }
            }
            is MarketplaceIntent.ConfirmSwitch -> {
                confirmSwitch(intent.modelId, intent.modelPath)
            }
        }
    }

    private fun confirmSwitch(modelId: String, modelPath: String) {
        if (currentState.isSwitching) return
        
        val fromModelId = currentState.activeModelId
        setState { copy(switchState = ModelSwitchState.Switching(fromModelId, modelId)) }

        viewModelScope.launch {
            repository.initializeModel(modelPath)
                .onSuccess {
                    preferenceManager.updateSelectedModel(modelId)
                    setState { 
                        copy(
                            activeModelId = modelId,
                            pendingModelId = null,
                            switchState = ModelSwitchState.Ready(modelId)
                        )
                    }
                }
                .onFailure { e ->
                    setState { copy(switchState = ModelSwitchState.Error(modelId, e.message ?: "Unknown error")) }
                    sendEffect { MarketplaceEffect.ShowToast("Failed to switch: ${e.message}") }
                }
        }
    }

    private fun downloadModel(modelName: String, baseDir: String, url: String, sha256: String?) {
        setState { copy(downloadingModelName = modelName, downloadProgress = 0) }
        
        viewModelScope.launch {
            repository.downloadModel(url, modelName, sha256).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> setState { copy(downloadProgress = progress.percent) }
                    DownloadProgress.Finished -> {
                        setState { copy(downloadingModelName = null, downloadProgress = null) }
                        loadLocalModels()
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(downloadingModelName = null, downloadProgress = null) }
                        sendEffect { MarketplaceEffect.ShowToast(progress.message) }
                        loadLocalModels() // Refresh to show failure status
                    }
                }
            }
        }
    }
}
