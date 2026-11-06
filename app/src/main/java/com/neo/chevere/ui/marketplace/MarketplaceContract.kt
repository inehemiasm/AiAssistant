package com.neo.chevere.ui.marketplace

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.ui.common.CatalogState

/**
 * Represents the various states during a model switch operation.
 */
sealed class ModelSwitchState {
    data object Idle : ModelSwitchState()
    data class Switching(val fromModelId: String?, val toModelId: String) : ModelSwitchState()
    data class WarmingUp(
        val modelId: String,
        val progress: InitializationStatus = InitializationStatus.Uninitialized
    ) : ModelSwitchState()

    data class Ready(val modelId: String) : ModelSwitchState()
    data class Error(val modelId: String, val message: String) : ModelSwitchState()
}

/**
 * Visibility and detail state of the model-selection guidance.
 */
sealed interface ModelGuidanceState {
    data object Compact : ModelGuidanceState
    data object Expanded : ModelGuidanceState
    data object Dismissed : ModelGuidanceState
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
    val activeDownloads: Map<String, DownloadProgress> = emptyMap(),
    val activeModelId: String? = null,
    val activeImageModelId: String? = null,
    val pendingModelId: String? = null,
    val switchState: ModelSwitchState = ModelSwitchState.Idle,
    val deviceTotalRamGb: Double = 0.0,
    val modelGuidance: ModelGuidanceState = ModelGuidanceState.Compact
) : UiState {
    val isDownloading: Boolean get() = activeDownloads.isNotEmpty()
    val isSwitching: Boolean get() = switchState is ModelSwitchState.Switching || switchState is ModelSwitchState.WarmingUp

    val chatRemoteModels: List<ModelEntry>
        get() = remoteModels.filter { it.activationCategory() == ModelActivationCategory.CHAT }

    val textVisionRemoteModels: List<ModelEntry>
        get() = chatRemoteModels.filterNot { it.isAudioModel }

    val audioRemoteModels: List<ModelEntry>
        get() = chatRemoteModels.filter { it.isAudioModel }

    val imageRemoteModels: List<ModelEntry>
        get() = remoteModels.filter { it.activationCategory() == ModelActivationCategory.IMAGE_GENERATION }

    val chatLocalModels: List<InstalledModel>
        get() = localModels.filter { it.activationCategory() == ModelActivationCategory.CHAT }

    val textVisionLocalModels: List<InstalledModel>
        get() = chatLocalModels.filterNot { it.isAudioCapable() }

    val audioLocalModels: List<InstalledModel>
        get() = chatLocalModels.filter { it.isAudioCapable() }

    val imageLocalModels: List<InstalledModel>
        get() = localModels.filter { it.activationCategory() == ModelActivationCategory.IMAGE_GENERATION }
}

sealed class MarketplaceIntent : UiIntent {
    data object ExpandModelGuidance : MarketplaceIntent()
    data object CollapseModelGuidance : MarketplaceIntent()
    data object DismissModelGuidance : MarketplaceIntent()
    data object FetchModels : MarketplaceIntent()
    data class DownloadModel(val model: ModelEntry, val baseDir: String) : MarketplaceIntent()
    data class DeleteModel(val modelId: String) : MarketplaceIntent()
    data class SelectModel(val modelId: String) : MarketplaceIntent()
    data class ConfirmSwitch(val modelId: String, val modelPath: String) : MarketplaceIntent()
}

sealed class MarketplaceEffect : UiEffect {
    data class ShowToast(val message: String) : MarketplaceEffect()
}
