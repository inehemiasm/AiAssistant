package com.neo.aiassistant.ui.marketplace.details

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.ModelEntry

data class ModelDetailsState(
    val modelId: String = "",
    val modelEntry: ModelEntry? = null,
    val installedModel: InstalledModel? = null,
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val isActive: Boolean = false,
    val isPending: Boolean = false,
    val error: String? = null,
    val downloadProgress: Int? = null
) : UiState {
    val displayName: String get() = installedModel?.displayName ?: modelEntry?.name ?: "Unknown Model"
    val isInstalled: Boolean get() = installedModel?.isHealthy ?: false
}

sealed class ModelDetailsIntent : UiIntent {
    data class LoadDetails(val modelId: String) : ModelDetailsIntent()
    data object Download : ModelDetailsIntent()
    data object Delete : ModelDetailsIntent()
    data object Select : ModelDetailsIntent()
    data object ConfirmSwitch : ModelDetailsIntent()
}

sealed class ModelDetailsEffect : UiEffect {
    data class ShowToast(val message: String) : ModelDetailsEffect()
    data object NavigateBack : ModelDetailsEffect()
}
