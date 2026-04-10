package com.neo.aiassistant.ui.common

/**
 * Represents the status of fetching the model catalog from the remote data source.
 */
sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Error(val message: String) : CatalogState
}

data class PerformanceMetrics(
    val lastLatencyMs: Long = 0,
    val vramUsagePercent: Int = 0,
    val throughputTks: Float = 0f
)
