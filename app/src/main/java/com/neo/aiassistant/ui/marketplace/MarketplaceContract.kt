package com.neo.aiassistant.ui.marketplace

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState

/**
 * The UI state for the Model Marketplace screen.
 *
 * @property remoteModels The list of AI models available for download from the catalog.
 * @property localModels The list of AI models already downloaded and available locally.
 * @property catalogState The current status of fetching the remote model catalog.
 * @property downloadingModelName The name of the model currently being downloaded, if any.
 * @property downloadProgress The current progress percentage of the active download.
 */
data class MarketplaceState(
    val remoteModels: List<ModelEntry> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val catalogState: CatalogState = CatalogState.Idle,
    val downloadingModelName: String? = null,
    val downloadProgress: Int? = null
) : UiState {
    /** `true` if a model download is currently in progress. */
    val isDownloading: Boolean get() = downloadingModelName != null
}

/**
 * User intents (actions) that can be performed on the Marketplace screen.
 */
sealed class MarketplaceIntent : UiIntent {
    /** Request to fetch the latest available models from the remote catalog. */
    data object FetchModels : MarketplaceIntent()
    
    /** 
     * Request to download a specific model.
     * @property modelName The name of the model to download.
     * @property baseDir The base directory where the model should be saved.
     */
    data class DownloadModel(val modelName: String, val baseDir: String) : MarketplaceIntent()
    
    /** 
     * Request to delete a locally stored model.
     * @property modelName The name of the model to delete.
     */
    data class DeleteModel(val modelName: String) : MarketplaceIntent()
    
    /** 
     * Request to switch the active model.
     * @property modelName The name of the model to switch to.
     * @property baseDir The base directory where the model is located.
     */
    data class SwitchModel(val modelName: String, val baseDir: String) : MarketplaceIntent()
}

/**
 * One-time side effects that can be triggered from the Marketplace ViewModel.
 */
sealed class MarketplaceEffect : UiEffect {
    /** Request to show a toast message. @property message The message to show. */
    data class ShowToast(val message: String) : MarketplaceEffect()
}
