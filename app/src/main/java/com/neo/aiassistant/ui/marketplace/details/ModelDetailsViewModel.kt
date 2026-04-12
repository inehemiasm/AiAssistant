package com.neo.aiassistant.ui.marketplace.details

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.InstallStatus
import com.neo.aiassistant.ui.navigation.Route
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
        
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { activeId ->
                setState { copy(isActive = activeId == modelId) }
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
        setState { copy(isActionInProgress = true, downloadProgress = 0) }
        
        viewModelScope.launch {
            repository.downloadModel(entry.url, entry.effectiveFileName, entry.sha256).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> setState { copy(downloadProgress = progress.percent) }
                    DownloadProgress.Finished -> {
                        setState { copy(isActionInProgress = false, downloadProgress = null) }
                        loadDetails()
                        sendEffect { ModelDetailsEffect.ShowToast("Download complete") }
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(isActionInProgress = false, downloadProgress = null) }
                        sendEffect { ModelDetailsEffect.ShowToast(progress.message) }
                        loadDetails()
                    }
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
        // In this architecture, "selecting" usually means setting it as pending in Marketplace 
        // or just switching immediately if requested.
        // For the details screen, let's trigger the switch confirmation.
        confirmSwitch()
    }

    private fun confirmSwitch() {
        val model = currentState.installedModel ?: return
        setState { copy(isActionInProgress = true) }
        
        viewModelScope.launch {
            repository.initializeModel(model.filePath)
                .onSuccess {
                    preferenceManager.updateSelectedModel(modelId)
                    setState { copy(isActive = true, isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Model activated") }
                }
                .onFailure { e ->
                    setState { copy(isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Switch failed: ${e.message}") }
                }
        }
    }
}
