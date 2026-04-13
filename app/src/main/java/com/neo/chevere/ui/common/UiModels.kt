package com.neo.chevere.ui.common

/**
 * Represents the status of fetching the model catalog from the remote data source.
 */
sealed interface CatalogState {
    /** The catalog is not currently being fetched. */
    data object Idle : CatalogState
    /** The catalog is currently being fetched from the remote source. */
    data object Loading : CatalogState
    /** 
     * An error occurred while fetching the catalog.
     * @property message A description of the error.
     */
    data class Error(val message: String) : CatalogState
}

/**
 * Represents performance metrics for AI model inference.
 *
 * @property lastLatencyMs The time taken for the last inference operation in milliseconds.
 * @property vramUsagePercent The estimated VRAM usage as a percentage (0-100).
 * @property throughputTks The inference throughput in tokens per second.
 */
data class PerformanceMetrics(
    val lastLatencyMs: Long = 0,
    val vramUsagePercent: Int = 0,
    val throughputTks: Float = 0f
)
