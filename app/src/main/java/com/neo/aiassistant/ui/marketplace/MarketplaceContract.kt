package com.neo.aiassistant.ui.marketplace

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState

/**
 * The UI state for the Model Marketplace screen.
 */
data class MarketplaceState(
    val remoteModels: List<ModelEntry> = emptyList(),
    val localModels: List<InstalledModel> = emptyList(),
    val catalogState: CatalogState = CatalogState.Idle,
    val downloadingModelName: String? = null,
    val downloadProgress: Int? = null
) : UiState {
    val isDownloading: Boolean get() = downloadingModelName != null
}

sealed class MarketplaceIntent : UiIntent {
    data object FetchModels : MarketplaceIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : MarketplaceIntent()
    data class DeleteModel(val modelName: String) : MarketplaceIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : MarketplaceIntent()
}

sealed class MarketplaceEffect : UiEffect {
    data class ShowToast(val message: String) : MarketplaceEffect()
}
