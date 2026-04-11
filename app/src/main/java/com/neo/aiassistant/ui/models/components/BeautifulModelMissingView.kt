package com.neo.aiassistant.ui.models.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.aiassistant.R
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState
import com.neo.aiassistant.ui.common.PerformanceMetrics
import com.neo.aiassistant.ui.designsystem.AmbientGlow
import com.neo.aiassistant.ui.designsystem.HighTechPrimaryButton
import com.neo.aiassistant.ui.designsystem.ModelSelectorCard
import com.neo.aiassistant.ui.designsystem.StatCard
import com.neo.aiassistant.ui.designsystem.Typography
import com.neo.aiassistant.ui.marketplace.ModelSwitchState

/**
 * A visually rich view displayed when no AI model is currently loaded or selected.
 */
@Composable
fun BeautifulModelMissingView(
    activeModel: String,
    pendingModel: String?,
    localModels: List<InstalledModel>,
    remoteModels: List<ModelEntry>,
    availableDownloads: List<ModelEntry> = emptyList(),
    catalogState: CatalogState,
    metrics: PerformanceMetrics,
    switchState: ModelSwitchState,
    onModelSelect: (String) -> Unit,
    onConfirmSwitch: (String, String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onClearError: () -> Unit
) {
    val displayModel = pendingModel ?: activeModel
    val isSwitching = switchState is ModelSwitchState.Switching || switchState is ModelSwitchState.WarmingUp
    val warmupStatus = (switchState as? ModelSwitchState.WarmingUp)?.progress

    val infiniteTransition = rememberInfiniteTransition(label = "warmup")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-100).dp).size(400.dp))
        AmbientGlow(MaterialTheme.colorScheme.primaryContainer, Modifier.align(Alignment.BottomStart).offset(x = (-100).dp, y = 100.dp).size(300.dp))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.local_compute_engine), 
                color = MaterialTheme.colorScheme.onSurface, 
                style = Typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                stringResource(R.string.engine_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = Typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(48.dp))

            when (catalogState) {
                CatalogState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.scanning_repositories), color = MaterialTheme.colorScheme.onSurfaceVariant, style = Typography.labelSmall)
                }
                is CatalogState.Error -> {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.mission_failure), color = MaterialTheme.colorScheme.error, style = Typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        catalogState.message, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        fontSize = 14.sp, 
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(32.dp))
                    HighTechPrimaryButton(
                        onClick = onClearError, 
                        text = stringResource(R.string.retry_uplink)
                    )
                }
                CatalogState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RadioButtonChecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.architecture), color = MaterialTheme.colorScheme.onSurface, style = Typography.titleLarge)
                            Text(stringResource(R.string.selector), color = MaterialTheme.colorScheme.onSurface, style = Typography.titleLarge)
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Text(
                                stringResource(R.string.models_detected),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = Typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (remoteModels.isNotEmpty()) {
                        remoteModels.forEach { model ->
                            ModelSelectorCard(
                                title = model.name,
                                description = model.description,
                                params = if (model.sizeBytes > 0) "%.1fB".format(model.sizeBytes / 1e9 * 4) else "N/A", // Approximation for display
                                vram = if (model.sizeBytes > 0) "%.1fGB".format(model.sizeBytes * 2 / 1e9) else "N/A",
                                isSelected = displayModel == model.effectiveFileName,
                                icon = if (model.name.contains("2b", ignoreCase = true)) Icons.Default.Speed else Icons.Default.Bolt,
                                onClick = { if (!isSwitching) onModelSelect(model.effectiveFileName) }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        // Fallback static models if remote list fails
                        ModelSelectorCard(
                            title = stringResource(R.string.gemma_4_e4b_it),
                            description = stringResource(R.string.gemma_4_e4b_desc),
                            params = "4.2",
                            vram = "8.4GB",
                            isSelected = displayModel.contains("E4B"),
                            icon = Icons.Default.Bolt,
                            onClick = { if (!isSwitching) onModelSelect("gemma-4-E4B-it.litertlm") }
                        )

                        Spacer(Modifier.height(16.dp))

                        ModelSelectorCard(
                            title = stringResource(R.string.gemma_4_e2b_it),
                            description = stringResource(R.string.gemma_4_e2b_desc),
                            params = "2.1",
                            vram = "3.2GB",
                            isSelected = displayModel.contains("E2B"),
                            icon = Icons.Default.Speed,
                            onClick = { if (!isSwitching) onModelSelect("gemma-4-E2B-it.litertlm") }
                        )
                    }

                    Spacer(Modifier.height(48.dp))
                    
                    val currentTargetModel = displayModel
                    val targetInstalledModel = localModels.find { it.fileName == currentTargetModel }
                    val isTargetDownloaded = targetInstalledModel != null

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            if (isSwitching) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.engine_status), style = Typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(
                                    when {
                                        isSwitching -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                        isTargetDownloaded -> MaterialTheme.colorScheme.primary
                                        else -> Color.Gray
                                    }
                                ))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isSwitching -> warmupStatus ?: "Warming up engine..."
                                        isTargetDownloaded -> stringResource(R.string.core_ready)
                                        else -> stringResource(R.string.download_required)
                                    }, 
                                    color = if (isSwitching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, 
                                    style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = if (isSwitching) Modifier.alpha(pulseAlpha) else Modifier
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    HighTechPrimaryButton(
                        onClick = { 
                            if (isTargetDownloaded && targetInstalledModel != null) {
                                onConfirmSwitch(targetInstalledModel.id, targetInstalledModel.filePath)
                            } else {
                                onDownloadClick(currentTargetModel) 
                            }
                        },
                        enabled = !isSwitching,
                        text = when {
                            isSwitching -> "SWITCHING ENGINE..."
                            isTargetDownloaded -> stringResource(R.string.confirm_engine_switch)
                            else -> stringResource(R.string.download_sync_core)
                        }
                    )

                    Spacer(Modifier.height(48.dp))

                    StatCard(label = stringResource(R.string.latency), value = if (metrics.lastLatencyMs > 0) "${metrics.lastLatencyMs}ms" else "--")
                    Spacer(Modifier.height(12.dp))
                    StatCard(label = stringResource(R.string.vram_usage), value = "${metrics.vramUsagePercent}%")
                    Spacer(Modifier.height(12.dp))
                    StatCard(label = stringResource(R.string.throughput), value = if (metrics.throughputTks > 0) "%.1f tk/s".format(metrics.throughputTks) else "--")
                }
            }
        }
    }
}
