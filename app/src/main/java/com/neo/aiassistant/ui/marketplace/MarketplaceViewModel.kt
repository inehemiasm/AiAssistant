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
