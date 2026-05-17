package com.neo.chevere.ui.models

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.ui.common.CatalogState
import com.neo.chevere.ui.marketplace.ModelSwitchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ModelsViewModel"

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
                Timber.tag(TAG).d("Active model updated: $model")
                if (model != null) setState { copy(selectedModel = model) }
            }
        }
    }

    private fun observeInitStatus() {
        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                if (currentState.isSwitching) {
                    Timber.tag(TAG).d("Warmup status: $status")
                    setState {
                        copy(
                            switchState = ModelSwitchState.WarmingUp(
                                currentState.pendingModel ?: currentState.selectedModel, status
                            )
                        )
                    }
                }
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
            is ModelsIntent.SelectModel -> {
                Timber.tag(TAG).d("Model selected in UI: ${intent.modelName}")
                setState { copy(pendingModel = intent.modelName) }
            }

            is ModelsIntent.ConfirmSwitch -> {
                confirmSwitch(intent.modelName, intent.modelPath)
            }

            is ModelsIntent.SwitchModel -> {
                // Legacy support - directly updating preference, but we should use ConfirmSwitch
                preferenceManager.updateSelectedModel(intent.modelName)
            }

            is ModelsIntent.DownloadModel -> {
                downloadModel(intent.modelName, intent.baseDir)
            }

            ModelsIntent.ClearError -> setState { copy(catalogState = CatalogState.Idle) }
            ModelsIntent.RefreshMetrics -> { /* Logic to update metrics */
            }
        }
    }

    private fun confirmSwitch(modelName: String, modelPath: String) {
        Timber.tag(TAG).d("Confirming switch to $modelName")
        val fromModel = currentState.selectedModel

        setState {
            copy(switchState = ModelSwitchState.Switching(fromModel, modelName))
        }

        viewModelScope.launch {
            Timber.tag(TAG).d("Initializing model at $modelPath")
            repository.initializeModel(modelPath)
                .onSuccess {
                    Timber.tag(TAG).d("Model switch successful: $modelName")
                    preferenceManager.updateSelectedModel(modelName)
                    setState {
                        copy(
                            selectedModel = modelName,
                            pendingModel = null,
                            switchState = ModelSwitchState.Ready(modelName)
                        )
                    }
                }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Model switch failed")
                    setState {
                        copy(
                            switchState = ModelSwitchState.Error(
                                modelName,
                                e.message ?: "Unknown error"
                            )
                        )
                    }
                    sendEffect { ModelsEffect.ShowToast("Switch failed: ${e.message}") }
                }
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
        val modelEntry =
            currentState.remoteModels.find { it.effectiveFileName == modelName || it.name == modelName }
                ?: return

        setState { copy(isDownloading = true, downloadProgress = 0) }

        viewModelScope.launch {
            repository.downloadModel(modelEntry).collect { progress ->
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
