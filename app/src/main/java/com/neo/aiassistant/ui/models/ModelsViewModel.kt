package com.neo.aiassistant.ui.models

import android.app.Application
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

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<ModelsState, ModelsIntent, ModelsEffect>(application, ModelsState()) {

    init {
        viewModelScope.launch {
            loadLocalModels()
            observeSelectedModel()
            observeInitStatus()
        }
    }

    private fun observeSelectedModel() {
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { model ->
                if (model != null) setState { copy(selectedModel = model) }
            }
        }
    }

    private fun observeInitStatus() {
        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                // Use status to trigger metric refreshes or UI updates if needed
            }
        }
    }

    private suspend fun loadLocalModels() {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
    }

    override suspend fun handleIntent(intent: ModelsIntent) {
        when (intent) {
            ModelsIntent.FetchModels -> fetchRemoteModels()
            is ModelsIntent.SwitchModel -> {
                preferenceManager.updateSelectedModel(intent.modelName)
            }
            is ModelsIntent.DownloadModel -> {
                downloadModel(intent.modelName, intent.baseDir)
            }
            ModelsIntent.ClearError -> setState { copy(catalogState = CatalogState.Idle) }
            ModelsIntent.RefreshMetrics -> { /* Logic to update metrics */ }
        }
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

    private fun downloadModel(modelName: String, baseDir: String) {
        val modelEntry = currentState.remoteModels.find { it.effectiveFileName == modelName || it.name == modelName } ?: return
        
        setState { copy(isDownloading = true, downloadProgress = 0) }
        
        viewModelScope.launch {
            repository.downloadModel(modelEntry.url, modelEntry.effectiveFileName, modelEntry.sha256).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> setState { copy(downloadProgress = progress.percent) }
                    DownloadProgress.Finished -> {
                        setState { copy(isDownloading = false, downloadProgress = null) }
                        loadLocalModels()
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(isDownloading = false, downloadProgress = null) }
                        sendEffect { ModelsEffect.ShowToast(progress.message) }
                    }
                }
            }
        }
    }
}
