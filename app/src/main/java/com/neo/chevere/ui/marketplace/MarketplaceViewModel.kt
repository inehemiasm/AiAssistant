package com.neo.chevere.ui.marketplace

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.ui.common.CatalogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MarketplaceViewModel"

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<MarketplaceState, MarketplaceIntent, MarketplaceEffect>(application, MarketplaceState()) {

    private val handledFinishedDownloads = mutableSetOf<String>()
    private val startedDownloadKeys = mutableSetOf<String>()

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

        // Observe global downloads progress
        viewModelScope.launch {
            repository.allDownloadsProgress.collectLatest { progressMap ->
                // Update local state based on all active downloads
                // If a download for a specific model is in progress, update its state
                val downloadingModel = progressMap.entries.firstOrNull { it.value is DownloadProgress.Progress }
                
                setState { 
                    copy(
                        downloadingModelName = downloadingModel?.key,
                        downloadProgress = (downloadingModel?.value as? DownloadProgress.Progress)?.percent,
                        // We also need to map this back to individual model items in the UI if needed
                        // but usually the installStatus in localModels (observed from DB) handles the rest
                    )
                }

                val finishedDownloads = progressMap
                    .filterValues { it is DownloadProgress.Finished }
                    .keys
                    .filter { handledFinishedDownloads.add(it) }

                if (finishedDownloads.isNotEmpty()) {
                    val models = loadLocalModels()
                    finishedDownloads.forEach { finishedKey ->
                        maybeAutoActivateDownloadedModel(finishedKey, models)
                    }
                }
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

    private suspend fun loadLocalModels(): List<InstalledModel> {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
        return models
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
                downloadModel(intent.model)
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

                if (model.activationCategory() == ModelActivationCategory.IMAGE_GENERATION) {
                    sendEffect { MarketplaceEffect.ShowToast("Image models are used automatically when you generate images.") }
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

    private fun downloadModel(model: ModelEntry) {
        // Just trigger it. The global observer in init will handle state updates.
        startedDownloadKeys.add(model.effectiveFileName)
        startedDownloadKeys.add(model.effectiveInstalledId)
        viewModelScope.launch {
            repository.downloadModel(model).collectLatest { progress ->
                if (progress is DownloadProgress.Error) {
                    sendEffect { MarketplaceEffect.ShowToast(progress.message) }
                }
            }
        }
    }

    private suspend fun maybeAutoActivateDownloadedModel(
        finishedKey: String,
        installedModels: List<InstalledModel>
    ) {
        val remoteEntry = currentState.remoteModels.find {
            it.effectiveFileName == finishedKey || it.effectiveInstalledId == finishedKey
        }
        val installedModel = installedModels.find { model ->
            model.id == finishedKey ||
                model.fileName == finishedKey ||
                (remoteEntry != null && model.matchesEntry(remoteEntry))
        } ?: return

        if (!installedModel.isHealthy) return

        if (finishedKey in startedDownloadKeys || remoteEntry?.let { it.effectiveFileName in startedDownloadKeys || it.effectiveInstalledId in startedDownloadKeys } == true) {
            sendEffect { MarketplaceEffect.ShowToast("Download complete: ${installedModel.displayName}") }
        }

        when (installedModel.activationCategory()) {
            ModelActivationCategory.CHAT -> {
                val healthyChatModels = installedModels
                    .filter { it.isHealthy && it.activationCategory() == ModelActivationCategory.CHAT }
                if (healthyChatModels.size == 1) {
                    activateChatModel(installedModel)
                }
            }
            ModelActivationCategory.IMAGE_GENERATION -> {
                val healthyImageModels = installedModels
                    .filter { it.isHealthy && it.activationCategory() == ModelActivationCategory.IMAGE_GENERATION }
                if (healthyImageModels.size == 1) {
                    sendEffect { MarketplaceEffect.ShowToast("${installedModel.displayName} is ready for image generation") }
                }
            }
        }
    }

    private suspend fun activateChatModel(model: InstalledModel) {
        repository.initializeModel(model.filePath)
            .onSuccess {
                preferenceManager.updateSelectedModel(model.id)
                setState {
                    copy(
                        activeModelId = model.id,
                        pendingModelId = null,
                        switchState = ModelSwitchState.Ready(model.id)
                    )
                }
                sendEffect { MarketplaceEffect.ShowToast("${model.displayName} activated") }
            }
            .onFailure { e ->
                sendEffect { MarketplaceEffect.ShowToast("Downloaded, but activation failed: ${e.message}") }
            }
    }
}
