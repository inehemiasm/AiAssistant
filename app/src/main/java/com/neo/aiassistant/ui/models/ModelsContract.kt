package com.neo.aiassistant.ui.models

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState
import com.neo.aiassistant.ui.common.PerformanceMetrics

data class ModelsState(
    val localModels: List<LocalModel> = emptyList(),
    val remoteModels: List<ModelEntry> = emptyList(),
    val selectedModel: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Int? = null,
    val catalogState: CatalogState = CatalogState.Idle,
    val metrics: PerformanceMetrics = PerformanceMetrics()
) : UiState {
    val availableDownloads: List<ModelEntry> get() {
        val downloadedFileNames = localModels.map { it.fileName }.toSet()
        return remoteModels.filter { it.effectiveFileName !in downloadedFileNames }
    }
}

sealed class ModelsIntent : UiIntent {
    data object FetchModels : ModelsIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ModelsIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : ModelsIntent()
    data object ClearError : ModelsIntent()
    data object RefreshMetrics : ModelsIntent()
}

sealed class ModelsEffect : UiEffect {
    data class ShowToast(val message: String) : ModelsEffect()
}
