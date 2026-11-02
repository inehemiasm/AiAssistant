package com.neo.chevere.ui.marketplace.details

import androidx.compose.ui.platform.LocalResources
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neo.chevere.ui.common.localizedLabel
import com.neo.chevere.R
import com.neo.chevere.core.Constants
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.hapticForFeedbackMessage
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.designsystem.AmbientGlow
import com.neo.chevere.ui.designsystem.Typography
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ModelDetailsScreen(
    viewModel: ModelDetailsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ModelDetailsContent(
        state = state,
        effects = viewModel.effect,
        onIntent = { viewModel.onIntent(it) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailsContent(
    state: ModelDetailsState,
    effects: kotlinx.coroutines.flow.Flow<ModelDetailsEffect>,
    onIntent: (ModelDetailsIntent) -> Unit,
    onBack: () -> Unit
) {
    val textResources = LocalResources.current
    val viewModel = remember(state, effects, onIntent) {
        object {
            fun onIntent(intent: ModelDetailsIntent) {
                onIntent(intent)
            }
            val effect = effects
            val uiState = object {
                val value = state
            }
        }
    }
    val context = LocalContext.current
    val hapticView = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ModelDetailsEffect.ShowToast -> {
                    hapticView.performChevereHaptic(effect.message.hapticForFeedbackMessage())
                    snackbarHostState.showSnackbar(effect.message)
                }

                ModelDetailsEffect.NavigateBack -> {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        textResources.getString(R.string.ui_model_details),
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
                    .align(Alignment.TopEnd)
                    .size(300.dp)
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    HeaderSection(state)

                    Spacer(Modifier.height(32.dp))

                    StatusSection(state)

                    Spacer(Modifier.height(32.dp))

                    CompatibilitySection(state)

                    Spacer(Modifier.height(32.dp))

                    MetadataSection(state)

                    Spacer(Modifier.height(48.dp))

                    ActionSection(
                        state = state,
                        onIntent = { intent ->
                            hapticView.performChevereHaptic(
                                when (intent) {
                                    ModelDetailsIntent.CancelDownload,
                                    ModelDetailsIntent.Delete -> ChevereHaptic.Warning

                                    else -> ChevereHaptic.Action
                                }
                            )
                            viewModel.onIntent(intent)
                        }
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }

            if (state.showDownloadRequirements) {
                DownloadRequirementsDialog(
                    state = state,
                    availableBytes = getAvailableBytes(context.filesDir.absolutePath),
                    onConfirm = {
                        hapticView.performChevereHaptic(ChevereHaptic.Action)
                        viewModel.onIntent(ModelDetailsIntent.ConfirmDownload)
                    },
                    onDismiss = {
                        hapticView.performChevereHaptic(ChevereHaptic.Warning)
                        viewModel.onIntent(ModelDetailsIntent.DismissDownloadRequirements)
                    }
                )
            }
        }
    }
}

@Composable
fun HeaderSection(state: ModelDetailsState) {
    val textResources = LocalResources.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = state.displayName.ifBlank { textResources.getString(R.string.ui_unknown_model) }.uppercase(),
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = state.modelEntry?.description ?: textResources.getString(R.string.ui_local_neural_engine_component),
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusSection(state: ModelDetailsState) {
    val textResources = LocalResources.current
    SectionHeader(textResources.getString(R.string.ui_runtime_status))

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(
                label = textResources.getString(R.string.ui_install_state),
                value = state.installedModel?.installStatus?.localizedLabel() ?: textResources.getString(R.string.ui_not_installed),
                color = if (state.isInstalled) Color.Green else MaterialTheme.colorScheme.primary
            )

            if (state.isActive) {
                StatusRow(
                    label = textResources.getString(R.string.ui_engine_role),
                    value = textResources.getString(R.string.ui_active_engine),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            state.downloadProgress?.let { progress ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        textResources.getString(R.string.ui_downloading_1_s_f942e0, progress),
                        style = Typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CompatibilitySection(state: ModelDetailsState) {
    val textResources = LocalResources.current
    SectionHeader(textResources.getString(R.string.ui_hardware_compatibility))

    val isCompatible =
        state.modelEntry?.runtimeType == "LiteRT" || state.installedModel?.runtime?.name == "LITERT"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CompatibilityRow(
                icon = Icons.Default.CheckCircle,
                label = textResources.getString(R.string.ui_neural_engine),
                value = textResources.getString(R.string.ui_litert_optimized),
                isOk = isCompatible
            )
            CompatibilityRow(
                icon = Icons.Default.Visibility,
                label = textResources.getString(R.string.ui_multimodal_support),
                value = if (state.modelEntry?.supportsVision == true || state.installedModel?.capabilities?.contains(
                        com.neo.chevere.domain.ModelCapability.VISION
                    ) == true
                ) textResources.getString(R.string.ui_vision_enabled) else textResources.getString(R.string.ui_text_only),
                isOk = true
            )
            CompatibilityRow(
                icon = Icons.Default.Storage,
                label = textResources.getString(R.string.ui_download_space),
                value = recommendedStorageText(state),
                isOk = true
            )
        }
    }
}

@Composable
fun MetadataSection(state: ModelDetailsState) {
    val textResources = LocalResources.current
    SectionHeader(textResources.getString(R.string.ui_technical_specifications))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetadataRow(textResources.getString(R.string.ui_model_id), state.modelId)
        MetadataRow(
            textResources.getString(R.string.ui_provider),
            state.modelEntry?.provider ?: state.installedModel?.source?.name ?: textResources.getString(R.string.ui_local)
        )
        MetadataRow(textResources.getString(R.string.ui_format), state.installedModel?.format?.name ?: "LITERTLM")
        MetadataRow(
            textResources.getString(R.string.ui_size),
            formatFileSize(state.installedModel?.sizeBytes ?: state.modelEntry?.sizeBytes ?: 0)
        )
        state.modelEntry?.license?.let { MetadataRow(textResources.getString(R.string.ui_license), it) }
    }
}

@Composable
fun ActionSection(state: ModelDetailsState, onIntent: (ModelDetailsIntent) -> Unit) {
    val textResources = LocalResources.current
    val isImageModel = state.installedModel?.let {
        it.taskType == com.neo.chevere.domain.ModelTaskType.IMAGE_GENERATION ||
                com.neo.chevere.domain.ModelCapability.IMAGE_GEN in it.capabilities
    } ?: state.modelEntry?.isImageGenerationModel ?: false

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.isInstalled && !state.isActionInProgress) {
            PrimaryAction(
                label = if (isImageModel) textResources.getString(R.string.ui_download_image_generator) else textResources.getString(R.string.ui_download_engine),
                icon = Icons.Default.Download,
                onClick = { onIntent(ModelDetailsIntent.Download) }
            )
        }

        if (state.downloadProgress != null) {
            SecondaryAction(
                label = textResources.getString(R.string.ui_cancel_download),
                icon = Icons.Default.Delete,
                onClick = { onIntent(ModelDetailsIntent.CancelDownload) }
            )
        }

        if (state.isInstalled && !state.isActive && !state.isActionInProgress) {
            PrimaryAction(
                label = if (isImageModel) textResources.getString(R.string.ui_activate_image_generator) else textResources.getString(R.string.ui_activate_engine),
                icon = Icons.Default.Sync,
                onClick = { onIntent(ModelDetailsIntent.ConfirmSwitch) }
            )
        } else if (state.isActive) {
            PrimaryAction(
                label = if (isImageModel) textResources.getString(R.string.ui_image_generator_active) else textResources.getString(R.string.ui_engine_active),
                icon = Icons.Default.CheckCircle,
                onClick = { },
                enabled = false
            )
        }

        if (state.isInstalled && !state.isActive && !state.isActionInProgress) {
            SecondaryAction(
                label = if (isImageModel) textResources.getString(R.string.ui_delete_image_generator) else textResources.getString(R.string.ui_delete_component),
                icon = Icons.Default.Delete,
                onClick = { onIntent(ModelDetailsIntent.Delete) }
            )
        }

        if (state.isActionInProgress) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun DownloadRequirementsDialog(
    state: ModelDetailsState,
    availableBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val textResources = LocalResources.current
    val modelSize = state.modelEntry?.sizeBytes ?: state.installedModel?.sizeBytes ?: 0L
    val requiredBytes =
        recommendedRequiredBytes(modelSize, state.modelEntry?.effectiveFileName.orEmpty())
    val hasEnoughSpace = modelSize <= 0L || availableBytes >= requiredBytes
    val modelKind = when {
        state.modelEntry?.runtimeType?.contains(
            "ONNX",
            ignoreCase = true
        ) == true -> textResources.getString(R.string.ui_image_generation_models_are_large_and_can_take_several_minutes_p)

        state.modelEntry?.supportsVision == true -> textResources.getString(R.string.ui_vision_models_need_extra_memory_when_processing_images)
        else -> textResources.getString(R.string.ui_chat_models_run_locally_and_may_be_slow_on_older_devices)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(textResources.getString(R.string.ui_download_requirements)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(textResources.getString(R.string.ui_model_size_1_s, formatFileSize(modelSize)))
                Text(textResources.getString(R.string.ui_recommended_free_space_1_s, formatFileSize(requiredBytes)))
                Text(textResources.getString(R.string.ui_available_space_1_s, formatFileSize(availableBytes)))
                Text(modelKind)
                if (!hasEnoughSpace) {
                    Text(
                        textResources.getString(R.string.ui_free_more_storage_before_downloading_this_model),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = hasEnoughSpace) {
                Text(textResources.getString(R.string.ui_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(textResources.getString(R.string.ui_cancel))
            }
        }
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = Typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun StatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = Typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompatibilityRow(icon: ImageVector, label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(16.dp),
            tint = if (isOk) Color.Green.copy(0.7f) else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = Typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = Typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = Typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return "%.1f %s".format(size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun recommendedStorageText(state: ModelDetailsState): String {
    val textResources = LocalResources.current
    val modelSize = state.modelEntry?.sizeBytes ?: state.installedModel?.sizeBytes ?: 0L
    return if (modelSize <= 0L) {
        textResources.getString(R.string.ui_check_device_storage)
    } else {
        textResources.getString(R.string.ui_1_s_free_recommended,
            formatFileSize(
                recommendedRequiredBytes(
                    modelSize,
                    state.modelEntry?.effectiveFileName.orEmpty()
                )
            )
        )
    }
}

private fun recommendedRequiredBytes(modelSize: Long, fileName: String): Long {
    if (modelSize <= 0L) return 0L
    val extractionMultiplier =
        if (fileName.endsWith(Constants.ModelFiles.ZIP_EXTENSION, ignoreCase = true)) 2.5 else 1.25
    return (modelSize * extractionMultiplier).toLong()
}

private fun getAvailableBytes(path: String): Long {
    return runCatching {
        StatFs(path).availableBytes
    }.getOrDefault(0L)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ModelDetailsScreenPreview() {
    com.neo.chevere.ui.designsystem.HighTechAiTheme(darkTheme = true) {
        ModelDetailsContent(
            state = ModelDetailsState(
                modelId = "gemma-2b.litertlm",
                modelEntry = ModelEntry(
                    name = "Gemma 2B",
                    description = "Lightweight local language model optimized for mobile devices.",
                    sizeBytes = 1600000000L,
                    runtimeType = "LiteRT",
                    supportsVision = false,
                    provider = "Google",
                    fileName = "gemma-2b.litertlm"
                ),
                isLoading = false,
                isActive = false
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {}
        )
    }
}

