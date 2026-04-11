package com.neo.aiassistant.ui.marketplace

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.aiassistant.R
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.ui.common.CatalogState
import com.neo.aiassistant.ui.designsystem.AmbientGlow
import com.neo.aiassistant.ui.designsystem.Typography

/**
 * The screen for the AI Model Marketplace.
 *
 * This screen allows users to discover new AI models from a remote catalog and
 * manage their locally installed models. It features a tabbed interface for
 * "Discover" and "Installed" models.
 *
 * @param viewModel The ViewModel providing state and handling marketplace actions.
 * @param onBack Callback for navigating back to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelMarketplaceScreen(
    viewModel: MarketplaceViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.neural_repository), style = Typography.titleLarge, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(MarketplaceIntent.FetchModels) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding)) {
            AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.BottomEnd).size(400.dp))

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.discover), style = Typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.installed), style = Typography.labelSmall) }
                    )
                }

                when (selectedTab) {
                    0 -> DiscoverModelsList(state, viewModel::onIntent, context.filesDir.absolutePath)
                    1 -> InstalledModelsList(state, viewModel::onIntent, context.filesDir.absolutePath)
                }
            }
        }
    }
}

/**
 * A list of models available for discovery and download.
 *
 * @param state The current marketplace state.
 * @param onIntent Callback for emitting marketplace intents.
 * @param baseDir The base directory for model file operations.
 */
@Composable
fun DiscoverModelsList(state: MarketplaceState, onIntent: (MarketplaceIntent) -> Unit, baseDir: String) {
    if (state.catalogState is CatalogState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.remoteModels) { model ->
                val isInstalled = state.localModels.any { it.fileName == model.effectiveFileName }
                val isDownloading = state.downloadingModelName == model.effectiveFileName
                
                RemoteModelCard(
                    model = model,
                    isInstalled = isInstalled,
                    isDownloading = isDownloading,
                    downloadProgress = if (isDownloading) state.downloadProgress else null,
                    onDownload = {
                        onIntent(MarketplaceIntent.DownloadModel(model.name, baseDir)) 
                    }
                )
            }
        }
    }
}

/**
 * A list of models already installed on the device.
 *
 * @param state The current marketplace state.
 * @param onIntent Callback for emitting marketplace intents.
 * @param baseDir The base directory for model file operations.
 */
@Composable
fun InstalledModelsList(state: MarketplaceState, onIntent: (MarketplaceIntent) -> Unit, baseDir: String) {
    if (state.localModels.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_local_models),
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.localModels) { model ->
                LocalModelCard(
                    model = model,
                    isActive = false, 
                    isReady = false,
                    onSelect = { onIntent(MarketplaceIntent.SwitchModel(model.fileName, baseDir)) },
                    onDelete = { onIntent(MarketplaceIntent.DeleteModel(model.fileName)) }
                )
            }
        }
    }
}

/**
 * A card representing a remote model in the discovery list.
 *
 * @param model The model entry from the catalog.
 * @param isInstalled Whether the model is already downloaded.
 * @param isDownloading Whether the model is currently being downloaded.
 * @param downloadProgress The current download progress percentage.
 * @param onDownload Callback triggered when the download button is clicked.
 */
@Composable
fun RemoteModelCard(
    model: ModelEntry,
    isInstalled: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int?,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(model.name.uppercase(), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ProviderBadge(model.provider)
            }
            
            Spacer(Modifier.height(8.dp))
            Text(model.description, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoItem(Icons.Default.Storage, formatFileSize(model.sizeBytes))
                Spacer(Modifier.width(16.dp))
                InfoItem(Icons.Default.Memory, model.runtimeType)
                
                if (model.supportsVision) {
                    Spacer(Modifier.width(16.dp))
                    InfoItem(Icons.Default.Visibility, "Vision")
                }
                
                Spacer(Modifier.weight(1f))
                
                if (isInstalled) {
                    Icon(Icons.Default.FileDownloadDone, stringResource(R.string.installed), tint = Color.Green.copy(alpha = 0.7f))
                } else if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { (downloadProgress ?: 0) / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Download, stringResource(R.string.download_required), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * A card representing a locally installed model.
 *
 * @param model The local model information.
 * @param isActive Whether the model is the one currently selected in preferences.
 * @param isReady Whether the model is currently initialized in memory.
 * @param onSelect Callback triggered when the activate/play button is clicked.
 * @param onDelete Callback triggered when the delete button is clicked.
 */
@Composable
fun LocalModelCard(
    model: LocalModel,
    isActive: Boolean,
    isReady: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp, 
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                             else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isReady) Icons.Default.PlayArrow else Icons.Default.FileDownloadDone, 
                    null, 
                    tint = if (isReady) Color.Green else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(model.displayName.uppercase(), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.active), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoItem(Icons.Default.Storage, formatFileSize(model.sizeBytes))
                
                Spacer(Modifier.weight(1f))
                
                if (!isActive) {
                    IconButton(onClick = onSelect) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.activate), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/**
 * A small badge showing the provider of a model.
 *
 * @param provider The name of the model provider.
 */
@Composable
fun ProviderBadge(provider: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(provider.uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

/**
 * A reusable row item for displaying a single piece of model metadata with an icon.
 *
 * @param icon The icon to display.
 * @param text The metadata text.
 */
@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = Typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Formats a file size in bytes into a human-readable string (e.g., "1.2 GB").
 */
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
