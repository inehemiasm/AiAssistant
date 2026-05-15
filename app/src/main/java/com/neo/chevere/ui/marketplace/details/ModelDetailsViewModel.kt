package com.neo.chevere.ui.marketplace.details

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.core.Constants
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.ui.navigation.Route
import com.neo.chevere.ui.marketplace.ModelActivationCategory
import com.neo.chevere.ui.marketplace.activationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ModelDetailsVM"

@HiltViewModel
class ModelDetailsViewModel @Inject constructor(
    application: Application,
    private val repository: ChatRepository,
    private val preferenceManager: PreferenceManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<ModelDetailsState, ModelDetailsIntent, ModelDetailsEffect>(application, ModelDetailsState()) {

    private val modelDetailsRoute = savedStateHandle.toRoute<Route.ModelDetails>()
    private val modelId = modelDetailsRoute.modelId
    private var handledFinishedDownload = false
    private var startedDownloadHere = false

    init {
        Log.d(TAG, "--- INITIALIZING VM for Model: $modelId ---")
        setState { copy(modelId = modelId) }
        
        // Initial data load via Intent
        viewModelScope.launch {
            handleIntent(ModelDetailsIntent.LoadDetails(modelId))
        }
        
        // Reactive Observation of Active Model
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { activeId ->
                val isNowActive = activeId == modelId
                Log.d(TAG, "[SYNC] Active Model in DataStore: '$activeId' | This Model: '$modelId' | Result: $isNowActive")
                setState { copy(isActive = isNowActive) }
            }
        }

        // Global Download Observation
        viewModelScope.launch {
            repository.allDownloadsProgress.collectLatest { progressMap ->
                val progress = progressMap[modelId] ?: currentState.modelEntry?.let { entry ->
                    progressMap[entry.effectiveFileName] ?: progressMap[entry.effectiveInstalledId]
                }
                Log.v(TAG, "[DOWNLOAD] Map update for $modelId: $progress")
                
                when (progress) {
                    is DownloadProgress.Progress -> {
                        setState { copy(downloadProgress = progress.percent, isActionInProgress = true) }
                    }
                    is DownloadProgress.Finished -> {
                        Log.i(TAG, "[DOWNLOAD] Finished for $modelId. Reloading details.")
                        val shouldAnnounceCompletion = startedDownloadHere || currentState.downloadProgress != null
                        setState { copy(downloadProgress = null, isActionInProgress = false) }
                        handleIntent(ModelDetailsIntent.LoadDetails(modelId))
                        if (!handledFinishedDownload) {
                            handledFinishedDownload = true
                            if (shouldAnnounceCompletion) {
                                val displayName = currentState.installedModel?.displayName
                                    ?: currentState.modelEntry?.name
                                    ?: modelId
                                sendEffect { ModelDetailsEffect.ShowToast("Download complete: $displayName") }
                            }
                            maybeAutoActivateAfterDownload()
                        }
                    }
                    else -> { /* Idle or Error */ }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ModelDetailsIntent) {
        Log.d(TAG, "Handling Intent: ${intent::class.simpleName}")
        when (intent) {
            is ModelDetailsIntent.LoadDetails -> loadDetails(intent.modelId)
            ModelDetailsIntent.Download -> setState { copy(showDownloadRequirements = true) }
            ModelDetailsIntent.ConfirmDownload -> downloadModel()
            ModelDetailsIntent.DismissDownloadRequirements -> setState { copy(showDownloadRequirements = false) }
            ModelDetailsIntent.CancelDownload -> cancelDownload()
            ModelDetailsIntent.Delete -> deleteModel()
            ModelDetailsIntent.Select -> selectModel()
            ModelDetailsIntent.ConfirmSwitch -> confirmSwitch()
        }
    }

    private suspend fun loadDetails(id: String) {
        setState { copy(isLoading = true) }
        Log.d(TAG, "Loading details for $id...")
        
        // Local scan
        val localModels = repository.getLocalModels()
        val installed = localModels.find {
            it.id == id || it.id == id.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION) || it.fileName == id
        }
        Log.d(TAG, "Local Check: Found=${installed != null}, Status=${installed?.installStatus}")
        
        // Remote scan
        val remoteModels = repository.fetchAvailableModels().getOrDefault(emptyList())
        val entry = remoteModels.find { it.effectiveFileName == id || it.effectiveInstalledId == id || it.name == id }
        Log.d(TAG, "Remote Check: Found=${entry != null}, Provider=${entry?.provider}")

        setState { 
            copy(
                installedModel = installed,
                modelEntry = entry,
                isLoading = false
            )
        }
    }

    private fun downloadModel() {
        val entry = currentState.modelEntry ?: return
        Log.i(TAG, "Starting download for: ${entry.name} from ${entry.url}")
        startedDownloadHere = true
        setState { copy(showDownloadRequirements = false, isActionInProgress = true) }
        
        viewModelScope.launch {
            repository.downloadModel(entry).collectLatest { progress ->
                if (progress is DownloadProgress.Error) {
                    Log.e(TAG, "Download Error: ${progress.message}")
                    setState { copy(isActionInProgress = false, downloadProgress = null) }
                    sendEffect { ModelDetailsEffect.ShowToast(progress.message) }
                }
            }
        }
    }

    private fun cancelDownload() {
        val entry = currentState.modelEntry
        viewModelScope.launch {
            repository.cancelModelDownload(entry?.effectiveFileName ?: modelId)
            setState { copy(isActionInProgress = false, downloadProgress = null) }
            sendEffect { ModelDetailsEffect.ShowToast("Download canceled") }
        }
    }

    private fun deleteModel() {
        if (currentState.isActive) {
            Log.w(TAG, "Delete aborted: Model is active")
            sendEffect { ModelDetailsEffect.ShowToast("Cannot delete active model") }
            return
        }

        viewModelScope.launch {
            setState { copy(isActionInProgress = true) }
            Log.i(TAG, "Deleting model: $modelId")
            if (repository.deleteModel(modelId)) {
                sendEffect { ModelDetailsEffect.ShowToast("Model deleted") }
                handleIntent(ModelDetailsIntent.LoadDetails(modelId))
            } else {
                Log.e(TAG, "Delete failed for $modelId")
                sendEffect { ModelDetailsEffect.ShowToast("Failed to delete model") }
            }
            setState { copy(isActionInProgress = false) }
        }
    }

    private fun selectModel() {
        if (currentState.installedModel?.isHealthy != true) {
            Log.w(TAG, "Select aborted: Model not healthy")
            sendEffect { ModelDetailsEffect.ShowToast("Model is not ready") }
            return
        }
        
        if (currentState.isActive) {
            Log.d(TAG, "Select ignored: Already active")
            return
        }

        viewModelScope.launch {
            handleIntent(ModelDetailsIntent.ConfirmSwitch)
        }
    }

    private fun confirmSwitch() {
        val model = currentState.installedModel ?: return
        
        if (currentState.isActive) {
            Log.d(TAG, "ConfirmSwitch ignored: Already active")
            return
        }

        setState { copy(isActionInProgress = true) }
        
        viewModelScope.launch {
            Log.i(TAG, "[SWITCH] Initializing Engine with path: ${model.filePath}")
            repository.initializeModel(model.filePath)
                .onSuccess {
                    Log.i(TAG, "[SWITCH] Engine Ready. Updating DataStore to: $modelId")
                    preferenceManager.updateSelectedModel(modelId)
                    setState { copy(isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Model activated") }
                }
                .onFailure { e ->
                    Log.e(TAG, "[SWITCH] Engine Init FAILED", e)
                    setState { copy(isActionInProgress = false) }
                    sendEffect { ModelDetailsEffect.ShowToast("Switch failed: ${e.message}") }
                }
        }
    }

    private suspend fun maybeAutoActivateAfterDownload() {
        val installedModels = repository.getLocalModels()
        val installedModel = installedModels.find {
            it.id == modelId ||
                it.id == modelId.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION) ||
                it.fileName == modelId
        } ?: return

        if (!installedModel.isHealthy) return

        when (installedModel.activationCategory()) {
            ModelActivationCategory.CHAT -> {
                val healthyChatModels = installedModels.filter {
                    it.isHealthy && it.activationCategory() == ModelActivationCategory.CHAT
                }
                if (healthyChatModels.size == 1) {
                    activateChatModel(installedModel)
                }
            }
            ModelActivationCategory.IMAGE_GENERATION -> {
                val healthyImageModels = installedModels.filter {
                    it.isHealthy && it.activationCategory() == ModelActivationCategory.IMAGE_GENERATION
                }
                if (healthyImageModels.size == 1) {
                    sendEffect { ModelDetailsEffect.ShowToast("${installedModel.displayName} is ready for image generation") }
                }
            }
        }
    }

    private suspend fun activateChatModel(model: InstalledModel) {
        repository.initializeModel(model.filePath)
            .onSuccess {
                preferenceManager.updateSelectedModel(model.id)
                setState { copy(isActionInProgress = false, isActive = true) }
                sendEffect { ModelDetailsEffect.ShowToast("${model.displayName} activated") }
            }
            .onFailure { e ->
                sendEffect { ModelDetailsEffect.ShowToast("Downloaded, but activation failed: ${e.message}") }
            }
    }
}
