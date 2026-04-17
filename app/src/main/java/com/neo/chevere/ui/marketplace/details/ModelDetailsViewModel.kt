package com.neo.chevere.ui.marketplace.details

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ModelDetailsViewModel"

@HiltViewModel
class ModelDetailsViewModel @Inject constructor(
    application: Application,
    private val repository: ChatRepository,
    private val preferenceManager: PreferenceManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<ModelDetailsState, ModelDetailsIntent, ModelDetailsEffect>(application, ModelDetailsState()) {

    private val modelDetailsRoute = savedStateHandle.toRoute<Route.ModelDetails>()
    private val modelId = modelDetailsRoute.modelId

    init {
        setState { copy(modelId = modelId) }
        loadDetails()
        
        // Use collectLatest on preferenceManager to stay in sync with the source of truth
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { activeId ->
                setState { copy(isActive = activeId == modelId) }
            }
        }

        // Observe global downloads progress for this specific model
        viewModelScope.launch {
            repository.allDownloadsProgress.collectLatest { progressMap ->
                val progress = progressMap[modelId]
                if (progress is DownloadProgress.Progress) {
                    setState { copy(downloadProgress = progress.percent, isActionInProgress = true) }
                } else if (progress is DownloadProgress.Finished) {
                    setState { copy(downloadProgress = null, isActionInProgress = false) }
                    loadDetails()
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ModelDetailsIntent) {
        when (intent) {
            is ModelDetailsIntent.LoadDetails -> loadDetails()
            ModelDetailsIntent.Download -> downloadModel()
            ModelDetailsIntent.Delete -> deleteModel()
            ModelDetailsIntent.Select -> selectModel()
            ModelDetailsIntent.ConfirmSwitch -> confirmSwitch()
        }
    }

    private fun loadDetails() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            
            // Get local metadata
            val localModels = repository.getLocalModels()
            val installed = localModels.find { it.id == modelId }
            
            // Get remote metadata
            val remoteModels = repository.fetchAvailableModels().getOrDefault(emptyList())
            val entry = remoteModels.find { it.effectiveFileName == modelId || it.name == modelId }

            setState { 
                copy(
                    installedModel = installed,
                    modelEntry = entry,
                    isLoading = false
                )
            }
        }
    }

    private fun downloadModel() {
        val entry = currentState.modelEntry ?: return
        viewModelScope.launch {
            repository.downloadModel(entry.url, entry.effectiveFileName, entry.sha256).collectLatest { progress ->
                if (progress is DownloadProgress.Error) {
                    sendEffect { ModelDetailsEffect.ShowToast(progress.message) }
                }
            }
        }
    }

    private fun deleteModel() {
        if (currentState.isActive) {
            sendEffect { ModelDetailsEffect.ShowToast("Cannot delete active model") }
            return
        }

        viewModelScope.launch {
            setState { copy(isActionInProgress = true) }
            if (repository.deleteModel(modelId)) {
                loadDetails()
                sendEffect { ModelDetailsEffect.ShowToast("Model deleted") }
            } else {
                sendEffect { ModelDetailsEffect.ShowToast("Failed to delete model") }
            }
            setState { copy(isActionInProgress = false) }
        }
    }

    private fun selectModel() {
        if (currentState.installedModel?.isHealthy != true) {
            sendEffect { ModelDetailsEffect.ShowToast("Model is not ready") }
            return
        }
        confirmSwitch()
    }

    private fun confirmSwitch() {
        val model = currentState.installedModel ?: return
        setState { copy(isActionInProgress = true) }
        
        viewModelScope.launch {
            repository.initializeModel(model.filePath)
                .onSuccess {
                    // Update PreferenceManager - this will trigger the isActive collector
                    preferenceManager.updateSelectedModel(modelId)
                    setState { copy(isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Model activated") }
                }
                .onFailure { e ->
                    setState { copy(isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Switch failed: ${e.message}") }
                }
        }
    }
}
