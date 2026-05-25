package com.neo.chevere.ui.settings

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState

data class BenchmarkMetrics(
    val loadTimeMs: Long,
    val ttftMs: Long,
    val throughputTps: Double,
    val totalTimeMs: Long,
    val systemRamText: String,
    val accelText: String
)

data class BenchmarkState(
    val isRunning: Boolean = false,
    val result: BenchmarkMetrics? = null,
    val modelName: String = "",
    val errorMessage: String? = null
) : UiState

sealed class BenchmarkIntent : UiIntent {
    data object RunBenchmark : BenchmarkIntent()
    data object ClearResult : BenchmarkIntent()
}

sealed class BenchmarkEffect : UiEffect {
    data class ShowToast(val message: String) : BenchmarkEffect()
}
