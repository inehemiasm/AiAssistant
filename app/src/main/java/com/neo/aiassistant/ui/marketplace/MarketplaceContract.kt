package com.neo.aiassistant.ui.marketplace

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState

/**
 * Represents the various states during a model switch operation.
 */
sealed class ModelSwitchState {
    data object Idle : ModelSwitchState()
    data class Switching(val fromModelId: String?, val toModelId: String) : ModelSwitchState()
    data class WarmingUp(val modelId: String, val progress: String? = null) : ModelSwitchState()
    data class Ready(val modelId: String) : ModelSwitchState()
    data class Error(val modelId: String, val message: String) : ModelSwitchState()
}

/**
 * The UI state for the Model Marketplace screen.
 */
data class MarketplaceState(
    val remoteModels: List<ModelEntry> = emptyList(),
    val localModels: List<InstalledModel> = emptyList(),
    val catalogState: CatalogState = CatalogState.Idle,
    val downloadingModelName: String? = null,
    val downloadProgress: Int? = null,
    val activeModelId: String? = null,
    val pendingModelId: String? = null,
    val switchState: ModelSwitchState = ModelSwitchState.Idle
) : UiState {
    val isDownloading: Boolean get() = downloadingModelName != null
    val isSwitching: Boolean get() = switchState is ModelSwitchState.Switching || switchState is ModelSwitchState.WarmingUp
}

sealed class MarketplaceIntent : UiIntent {
    data object FetchModels : MarketplaceIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : MarketplaceIntent()
    data class DeleteModel(val modelName: String) : MarketplaceIntent()
    data class SelectModel(val modelId: String) : MarketplaceIntent()
    data class ConfirmSwitch(val modelId: String, val modelPath: String) : MarketplaceIntent()
}

sealed class MarketplaceEffect : UiEffect {
    data class ShowToast(val message: String) : MarketplaceEffect()
}
