package com.neo.chevere.ui.marketplace

import androidx.compose.ui.platform.LocalResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.neo.chevere.ui.common.localizedLabel
import com.neo.chevere.R
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.ui.common.CatalogState
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.hapticForFeedbackMessage
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.designsystem.AmbientGlow
import com.neo.chevere.ui.designsystem.Typography
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ModelMarketplaceScreen(
    viewModel: MarketplaceViewModel,
    onBack: () -> Unit,
    onModelClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ModelMarketplaceContent(
        state = state,
        effects = viewModel.effect,
        onIntent = { viewModel.onIntent(it) },
        onBack = onBack,
        onModelClick = onModelClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelMarketplaceContent(
    state: MarketplaceState,
    effects: kotlinx.coroutines.flow.Flow<MarketplaceEffect>,
    onIntent: (MarketplaceIntent) -> Unit,
    onBack: () -> Unit,
    onModelClick: (String) -> Unit
) {
    val viewModel = remember(state, effects, onIntent) {
        object {
            fun onIntent(intent: MarketplaceIntent) {
                onIntent(intent)
            }
            val effect = effects
            val uiState = object {
                val value = state
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val hapticView = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDownloadModel by remember { mutableStateOf<ModelEntry?>(null) }
    val baseDir = context.filesDir.absolutePath

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingDownloadModel?.let { model ->
            viewModel.onIntent(MarketplaceIntent.DownloadModel(model, baseDir))
            pendingDownloadModel = null
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MarketplaceEffect.ShowToast -> {
                    hapticView.performChevereHaptic(effect.message.hapticForFeedbackMessage())
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ai_models).uppercase(),
                        style = Typography.titleLarge,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Action)
                        viewModel.onIntent(MarketplaceIntent.FetchModels)
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            AmbientGlow(
                MaterialTheme.colorScheme.primary,
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(400.dp)
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ModelGuidanceBanner(state.modelGuidance, viewModel::onIntent)
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
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.1f
                            )
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            selectedTab = 0
                        },
                        text = {
                            Text(
                                stringResource(R.string.discover),
                                style = Typography.labelSmall
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            selectedTab = 1
                        },
                        text = {
                            Text(
                                stringResource(R.string.installed),
                                style = Typography.labelSmall
                            )
                        }
                    )
                }

                when (selectedTab) {
                    0 -> DiscoverModelsList(
                        state = state,
                        onIntent = { intent ->
                            if (intent is MarketplaceIntent.DownloadModel) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingDownloadModel = intent.model
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.onIntent(intent)
                                }
                            } else {
                                viewModel.onIntent(intent)
                            }
                        },
                        baseDir = baseDir,
                        onModelClick = onModelClick
                    )

                    1 -> InstalledModelsList(state, viewModel::onIntent, onModelClick)
                }
            }
        }
    }
}

private data class ExpandableModelSection<T>(
    val category: ModelSectionCategory,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val models: List<T>,
    val highlightAccent: Boolean = false
)

@Composable
fun DiscoverModelsList(
    state: MarketplaceState,
    onIntent: (MarketplaceIntent) -> Unit,
    baseDir: String,
    onModelClick: (String) -> Unit
) {
    val textResources = LocalResources.current
    val hapticView = LocalView.current
    if (state.catalogState is CatalogState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        val sections = listOf(
            ExpandableModelSection(
                category = ModelSectionCategory.AUDIO_VOICE,
                title = textResources.getString(R.string.ui_audio_and_voice_models),
                subtitle = textResources.getString(R.string.ui_audio_models_recommendation_body),
                icon = Icons.Default.Mic,
                models = state.audioRemoteModels,
                highlightAccent = true
            ),
            ExpandableModelSection(
                category = ModelSectionCategory.VISION,
                title = textResources.getString(R.string.ui_vision_and_multimodal_models),
                subtitle = textResources.getString(R.string.ui_vision_models_section_subtitle),
                icon = Icons.Default.Visibility,
                models = state.visionRemoteModels
            ),
            ExpandableModelSection(
                category = ModelSectionCategory.CHAT,
                title = textResources.getString(R.string.ui_chat_and_text_models),
                subtitle = textResources.getString(R.string.ui_chat_models_section_subtitle),
                icon = Icons.Default.Memory,
                models = state.textOnlyRemoteModels
            )
        ).filter { it.models.isNotEmpty() }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sections.forEach { section ->
                val isExpanded = section.category in state.expandedDiscoverSections
                item(key = "discover_header_${section.category.name}") {
                    ExpandableSectionHeaderCard(
                        title = section.title,
                        subtitle = section.subtitle,
                        icon = section.icon,
                        count = section.models.size,
                        isExpanded = isExpanded,
                        highlightAccent = section.highlightAccent,
                        onClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            onIntent(MarketplaceIntent.ToggleDiscoverSection(section.category))
                        }
                    )
                }

                if (isExpanded) {
                    items(
                        items = section.models,
                        key = { "discover_${section.category.name}_${it.effectiveFileName}" }
                    ) { model ->
                        val installedVersion = state.localModels.find {
                            it.fileName == model.effectiveFileName ||
                                    it.id == model.effectiveInstalledId ||
                                    it.fileName == model.effectiveInstalledId
                        }
                        val activeDownload = state.activeDownloads[model.effectiveFileName]
                            ?: state.activeDownloads[model.effectiveInstalledId]
                        val isDownloading = activeDownload is DownloadProgress.Progress
                        val downloadProgress = (activeDownload as? DownloadProgress.Progress)?.percent

                        RemoteModelCard(
                            model = model,
                            installedModel = installedVersion,
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress,
                            deviceTotalRamGb = state.deviceTotalRamGb,
                            onDownload = {
                                onIntent(MarketplaceIntent.DownloadModel(model, baseDir))
                            },
                            onClick = { onModelClick(model.effectiveFileName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledModelsList(
    state: MarketplaceState,
    onIntent: (MarketplaceIntent) -> Unit,
    onModelClick: (String) -> Unit
) {
    val textResources = LocalResources.current
    val hapticView = LocalView.current
    if (state.chatLocalModels.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(32.dp), contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.no_local_models),
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val installedSections = listOf(
            ExpandableModelSection(
                category = ModelSectionCategory.AUDIO_VOICE,
                title = textResources.getString(R.string.ui_audio_and_voice_models),
                subtitle = textResources.getString(R.string.ui_audio_models_recommendation_body),
                icon = Icons.Default.Mic,
                models = state.audioLocalModels,
                highlightAccent = true
            ),
            ExpandableModelSection(
                category = ModelSectionCategory.VISION,
                title = textResources.getString(R.string.ui_vision_and_multimodal_models),
                subtitle = textResources.getString(R.string.ui_vision_models_section_subtitle),
                icon = Icons.Default.Visibility,
                models = state.visionLocalModels
            ),
            ExpandableModelSection(
                category = ModelSectionCategory.CHAT,
                title = textResources.getString(R.string.ui_chat_and_text_models),
                subtitle = textResources.getString(R.string.ui_chat_models_section_subtitle),
                icon = Icons.Default.Memory,
                models = state.textOnlyLocalModels
            )
        ).filter { it.models.isNotEmpty() }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                installedSections.forEach { section ->
                    val isExpanded = section.category in state.expandedInstalledSections
                    item(key = "installed_header_${section.category.name}") {
                        ExpandableSectionHeaderCard(
                            title = section.title,
                            subtitle = section.subtitle,
                            icon = section.icon,
                            count = section.models.size,
                            isExpanded = isExpanded,
                            highlightAccent = section.highlightAccent,
                            onClick = {
                                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                onIntent(MarketplaceIntent.ToggleInstalledSection(section.category))
                            }
                        )
                    }

                    if (isExpanded) {
                        items(
                            items = section.models,
                            key = { "installed_${section.category.name}_${it.id}" }
                        ) { model ->
                            val isActive = state.activeModelId == model.id
                            val isPending = state.pendingModelId == model.id
                            val isSwitching =
                                (state.switchState as? ModelSwitchState.Switching)?.toModelId == model.id ||
                                        (state.switchState as? ModelSwitchState.WarmingUp)?.modelId == model.id
                            val activeDownload = state.activeDownloads[model.id]
                                ?: state.activeDownloads[model.fileName]
                            val downloadProgress = (activeDownload as? DownloadProgress.Progress)?.percent

                            LocalModelCard(
                                model = model,
                                isActive = isActive,
                                isPending = isPending,
                                isSwitching = isSwitching,
                                warmupStatus = if (isSwitching) (state.switchState as? ModelSwitchState.WarmingUp)?.progress else null,
                                downloadProgress = downloadProgress,
                                onSelect = { onIntent(MarketplaceIntent.SelectModel(model.id)) },
                                onDelete = { onIntent(MarketplaceIntent.DeleteModel(model.id)) },
                                onClick = { onModelClick(model.id) }
                            )
                        }
                    }
                }
            }

            // Confirm Button Area
            AnimatedVisibility(
                visible = state.pendingModelId != null && state.pendingModelId != state.activeModelId,
                modifier = Modifier.fillMaxWidth()
            ) {
                val pendingModel = state.localModels.find { it.id == state.pendingModelId }
                if (pendingModel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                onIntent(
                                    MarketplaceIntent.ConfirmSwitch(
                                        pendingModel.id,
                                        pendingModel.filePath
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSwitching,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (state.isSwitching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(textResources.getString(R.string.ui_switching_engine), style = Typography.labelLarge)
                            } else {
                                Icon(Icons.Default.Sync, null)
                                Spacer(Modifier.width(8.dp))
                                Text(textResources.getString(R.string.ui_confirm_engine_switch), style = Typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSectionHeaderCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isExpanded: Boolean,
    highlightAccent: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        highlightAccent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
    }
    val containerColor = when {
        isExpanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        highlightAccent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title.uppercase(),
                        style = Typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Badge(
                        text = count.toString(),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun RemoteModelCard(
    model: ModelEntry,
    installedModel: InstalledModel?,
    isDownloading: Boolean,
    downloadProgress: Int?,
    deviceTotalRamGb: Double,
    onDownload: () -> Unit,
    onClick: () -> Unit
) {
    val textResources = LocalResources.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                alpha = 0.5f
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.75f)
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        model.name.uppercase(),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (deviceTotalRamGb > 0.0) {
                    Spacer(Modifier.height(6.dp))
                    RamCompatibilityBadge(model = model, deviceRamGb = deviceTotalRamGb)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    model.description,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoItem(Icons.Default.Storage, formatFileSize(model.sizeBytes))
                            Spacer(Modifier.width(16.dp))
                            InfoItem(Icons.Default.Memory, model.runtimeType)

                            if (model.supportsVision) {
                                Spacer(Modifier.width(16.dp))
                                InfoItem(Icons.Default.Visibility, textResources.getString(R.string.ui_vision))
                            }

                            if (model.isAudioModel) {
                                Spacer(Modifier.width(16.dp))
                                InfoItem(Icons.Default.Mic, textResources.getString(R.string.ui_audio_voice))
                            }
                        }

                        model.license?.let { license ->
                            InfoItem(Icons.Default.Balance, license)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (installedModel != null && installedModel.isHealthy) {
                        Icon(
                            Icons.Default.FileDownloadDone,
                            stringResource(R.string.installed),
                            tint = Color.Green.copy(alpha = 0.7f)
                        )
                    } else if (isDownloading || installedModel?.installStatus == InstallStatus.DOWNLOADING) {
                        CircularProgressIndicator(
                            progress = { (downloadProgress ?: 0) / 100f },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (installedModel?.installStatus == InstallStatus.VERIFYING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Download,
                                stringResource(R.string.download_required),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            ProviderBadge(
                provider = model.provider,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun LocalModelCard(
    model: InstalledModel,
    isActive: Boolean,
    isPending: Boolean,
    isSwitching: Boolean,
    warmupStatus: InitializationStatus?,
    downloadProgress: Int? = null,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val textResources = LocalResources.current
    val infiniteTransition = rememberInfiniteTransition(label = "warmup")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warmup_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                isSwitching -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                !model.isHealthy -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                isPending -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                isPending -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.75f)
                ) {
                    Icon(
                        when {
                            isSwitching -> Icons.Default.Sync
                            !model.isHealthy -> Icons.Default.Error
                            isActive -> Icons.Default.Check
                            else -> Icons.Default.FileDownloadDone
                        },
                        null,
                        tint = when {
                            !model.isHealthy -> MaterialTheme.colorScheme.error
                            isActive || isSwitching -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = if (isSwitching) Modifier.alpha(alpha) else Modifier
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        model.displayName.uppercase(),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (!model.isHealthy) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }

                if (isSwitching || model.isTransitioning) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(8.dp))

                        val statusText = when (warmupStatus) {
                            is InitializationStatus.Initializing -> warmupStatus.message
                            is InitializationStatus.Failure -> textResources.getString(R.string.ui_failed_1_s, warmupStatus.message)
                            InitializationStatus.Ready -> textResources.getString(R.string.ui_ready)
                            InitializationStatus.Uninitialized -> textResources.getString(R.string.ui_starting)
                            null -> {
                                if (model.installStatus == InstallStatus.DOWNLOADING && downloadProgress != null) {
                                    textResources.getString(R.string.ui_downloading_1_s, downloadProgress)
                                } else {
                                    model.installStatus.localizedLabel()
                                }
                            }
                        }

                        Text(
                            statusText,
                            style = Typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(alpha)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoItem(Icons.Default.Storage, formatFileSize(model.sizeBytes ?: 0))

                        model.license?.let { license ->
                            InfoItem(Icons.Default.Balance, license)
                        }

                        if (!model.isHealthy && !model.isTransitioning) {
                            Text(
                                textResources.getString(R.string.ui_status_1_s, model.installStatus.localizedLabel()),
                                style = Typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = onDelete, enabled = !isActive && !isSwitching) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.delete),
                            tint = if (!isActive && !isSwitching) MaterialTheme.colorScheme.error.copy(
                                alpha = 0.7f
                            ) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            val isImageModel = model.activationCategory() == ModelActivationCategory.IMAGE_GENERATION
            val showReadyBadge = isImageModel && model.isHealthy && !isSwitching

            if (isActive) {
                Badge(
                    text = stringResource(R.string.active),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            } else if (showReadyBadge) {
                Badge(
                    text = textResources.getString(R.string.ui_ready),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            } else if (isPending && !isSwitching) {
                Badge(
                    text = textResources.getString(R.string.ui_selected),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun ProviderBadge(provider: String, modifier: Modifier = Modifier) {
    Badge(
        text = provider.uppercase(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun Badge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = Typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return "%.1f %s".format(size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun RamCompatibilityBadge(model: ModelEntry, deviceRamGb: Double) {
    val (compatibility, labelRes) = model.getRamCompatibility(deviceRamGb)
    val label = stringResource(labelRes)

    val badgeColor = when (compatibility) {
        RamCompatibility.OPTIMAL -> Color(0xFF00E676)
        RamCompatibility.COMPATIBLE -> Color(0xFF29B6F6)
        RamCompatibility.WARNING -> Color(0xFFFFB74D)
        RamCompatibility.RISK -> Color(0xFFEF5350)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            Icons.Default.Memory,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = badgeColor
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 10.sp),
            color = badgeColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ModelMarketplaceScreenPreview() {
    com.neo.chevere.ui.designsystem.HighTechAiTheme(darkTheme = true) {
        ModelMarketplaceContent(
            state = MarketplaceState(
                remoteModels = listOf(
                    ModelEntry(
                        name = "Gemma 2B",
                        description = "Lightweight local language model optimized for mobile devices.",
                        sizeBytes = 1600000000L,
                        runtimeType = "LiteRT",
                        supportsVision = false,
                        provider = "Google",
                        fileName = "gemma-2b.litertlm"
                    ),
                    ModelEntry(
                        name = "Stable Diffusion Turbo",
                        description = "Ultra-fast local text-to-image generator.",
                        sizeBytes = 2100000000L,
                        runtimeType = "ONNX",
                        supportsVision = false,
                        provider = "StabilityAI",
                        fileName = "sd-turbo.zip"
                    )
                ),
                localModels = listOf(
                    InstalledModel(
                        id = "gemma-2b.litertlm",
                        displayName = "Gemma 2B",
                        fileName = "gemma-2b.litertlm",
                        filePath = "/data/user/0/dev.neo.chevereai/files/gemma-2b.litertlm",
                        source = com.neo.chevere.domain.ModelSource.LOCAL,
                        format = com.neo.chevere.domain.ModelFormat.LITERTLM,
                        runtime = com.neo.chevere.domain.ModelRuntime.LITERT,
                        taskType = com.neo.chevere.domain.ModelTaskType.CHAT,
                        capabilities = setOf(com.neo.chevere.domain.ModelCapability.TEXT),
                        installStatus = InstallStatus.INSTALLED,
                        sizeBytes = 1600000000L
                    )
                ),
                activeModelId = "gemma-2b.litertlm"
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
            onModelClick = {}
        )
    }
}

