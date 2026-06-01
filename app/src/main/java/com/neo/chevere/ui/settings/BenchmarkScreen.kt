package com.neo.chevere.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neo.chevere.R
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.chat.components.QuantumThinkingIndicator
import com.neo.chevere.ui.designsystem.Typography

@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    BenchmarkContent(
        state = state,
        onIntent = { viewModel.onIntent(it) },
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkContent(
    state: BenchmarkState,
    onIntent: (BenchmarkIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel = remember(state, onIntent) {
        object {
            fun onIntent(intent: BenchmarkIntent) {
                onIntent(intent)
            }
            val uiState = object {
                val value = state
            }
        }
    }
    val hapticView = LocalView.current

    val glassBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.benchmark_label).uppercase(),
                        style = Typography.titleLarge,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        onBackClick()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBackground)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Info Explainer Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.benchmark_explanation),
                            style = Typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Interactive Terminal Console
                ConsolePanel(
                    isRunning = state.isRunning,
                    modelName = state.modelName,
                    errorMessage = state.errorMessage,
                    metrics = state.result
                )

                Spacer(Modifier.height(24.dp))

                // Glowing Metrics Table
                if (state.result != null) {
                    MetricsTable(metrics = state.result!!)
                    Spacer(Modifier.height(24.dp))
                }

                // Action Trigger Button
                Button(
                    onClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Action)
                        viewModel.onIntent(BenchmarkIntent.RunBenchmark)
                    },
                    enabled = !state.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (state.isRunning) {
                            stringResource(R.string.benchmarking)
                        } else {
                            stringResource(R.string.run_benchmark)
                        },
                        style = Typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = if (state.isRunning) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConsolePanel(
    isRunning: Boolean,
    modelName: String,
    errorMessage: String?,
    metrics: BenchmarkMetrics?
) {
    val terminalLogs = remember(isRunning, errorMessage, metrics, modelName) {
        buildList {
            if (isRunning) {
                add(">> INITIALIZING NEURAL BENCHMARK...")
                add(">> CONNECTING ENGINE: ${modelName.uppercase()}...")
                add(">> ACQUIRING SYSTEM MEMORY STATS...")
                add(">> EXECUTING STANDARDIZED PROMPT...")
                add(">> MEASURING TIME TO FIRST TOKEN (TTFT)...")
            } else if (errorMessage != null) {
                add(">> ERROR: ${errorMessage.uppercase()}")
                add(">> BENCHMARK RUN ABORTED.")
            } else if (metrics != null) {
                add(">> DIAGNOSTICS COMPLETED.")
                add(">> HARDWARE: ${metrics.accelText.uppercase()}")
                add(">> MEMORY INFO: ${metrics.systemRamText}")
                add(">> TOKENS: IN ${metrics.inputTokenCount} / OUT ${metrics.outputTokenCount}")
                add(">> EXECUTION COMPLETED IN ${metrics.totalTimeMs} MS.")
            } else {
                add(">> NEURAL CONSOLE IDLE.")
                add(">> SYSTEM READY FOR PERFORMANCE CALIBRATION.")
            }
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (errorMessage != null) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            } else if (isRunning) {
                Color.Cyan.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                terminalLogs.forEach { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (errorMessage != null) {
                            Color(0xFFFF5252)
                        } else if (isRunning) {
                            Color.Cyan
                        } else if (metrics != null) {
                            Color(0xFF00E676)
                        } else {
                            Color(0xFF81C784)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isRunning) {
                QuantumThinkingIndicator(
                    visible = true,
                    statusMessage = stringResource(R.string.benchmarking),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricsTable(metrics: BenchmarkMetrics) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "CALIBRATION READINGS",
                style = Typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Table Rows
            MetricRow(
                label = stringResource(R.string.benchmark_ttft_label),
                value = "${metrics.ttftMs} ms",
                isMonospace = true
            )
            MetricRow(
                label = stringResource(R.string.benchmark_input_tokens_label),
                value = "${metrics.inputTokenCount} tokens",
                isMonospace = true,
                isGlow = true
            )
            MetricRow(
                label = stringResource(R.string.benchmark_output_tokens_label),
                value = "${metrics.outputTokenCount} tokens",
                isMonospace = true,
                isGlow = true
            )
            MetricRow(
                label = stringResource(R.string.benchmark_load_label),
                value = "${metrics.loadTimeMs} ms",
                isMonospace = true
            )
            MetricRow(
                label = stringResource(R.string.benchmark_total_label),
                value = "${metrics.totalTimeMs} ms",
                isMonospace = true
            )
            MetricRow(
                label = stringResource(R.string.benchmark_accel_label),
                value = metrics.accelText
            )
            MetricRow(
                label = stringResource(R.string.benchmark_ram_label),
                value = metrics.systemRamText,
                isLast = true
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isGlow: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = Typography.bodyMedium.copy(
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (isGlow) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isGlow) {
                Color.Cyan
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End
        )
    }

    if (!isLast) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun BenchmarkScreenPreview() {
    com.neo.chevere.ui.designsystem.HighTechAiTheme(darkTheme = true) {
        BenchmarkContent(
            state = BenchmarkState(
                isRunning = false,
                modelName = "gemma-2b.litertlm",
                result = BenchmarkMetrics(
                    loadTimeMs = 120L,
                    ttftMs = 380L,
                    inputTokenCount = 10,
                    outputTokenCount = 128,
                    totalTimeMs = 3200L,
                    systemRamText = "12.4 GB Available",
                    accelText = "GPU (NNAPI) Acceleration"
                )
            ),
            onIntent = {},
            onBackClick = {}
        )
    }
}

