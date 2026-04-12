package com.neo.aiassistant.ui.marketplace.details

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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.aiassistant.R
import com.neo.aiassistant.domain.InstallStatus
import com.neo.aiassistant.ui.designsystem.AmbientGlow
import com.neo.aiassistant.ui.designsystem.Typography
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailsScreen(
    viewModel: ModelDetailsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MODEL DETAILS", style = Typography.titleLarge, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
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
            AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.TopEnd).size(300.dp))
            
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
                    
                    ActionSection(state, onIntent = viewModel::onIntent)
                    
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun HeaderSection(state: ModelDetailsState) {
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
                text = state.displayName.uppercase(),
                style = Typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = state.modelEntry?.description ?: "Local neural engine component.",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusSection(state: ModelDetailsState) {
    SectionHeader("RUNTIME STATUS")
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(
                label = "Install State",
                value = state.installedModel?.installStatus?.name ?: "NOT INSTALLED",
                color = if (state.isInstalled) Color.Green else MaterialTheme.colorScheme.primary
            )
            
            if (state.isActive) {
                StatusRow(label = "Engine Role", value = "ACTIVE ENGINE", color = MaterialTheme.colorScheme.primary)
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
                    Text("Downloading: $progress%", style = Typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CompatibilitySection(state: ModelDetailsState) {
    SectionHeader("HARDWARE COMPATIBILITY")
    
    val isCompatible = state.modelEntry?.runtimeType == "LiteRT" || state.installedModel?.runtime?.name == "LITERT"
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CompatibilityRow(
                icon = Icons.Default.CheckCircle,
                label = "Neural Engine",
                value = "LiteRT Optimized",
                isOk = isCompatible
            )
            CompatibilityRow(
                icon = Icons.Default.Visibility,
                label = "Multimodal Support",
                value = if (state.modelEntry?.supportsVision == true || state.installedModel?.capabilities?.contains(com.neo.aiassistant.domain.ModelCapability.VISION) == true) "Vision Enabled" else "Text Only",
                isOk = true
            )
        }
    }
}

@Composable
fun MetadataSection(state: ModelDetailsState) {
    SectionHeader("TECHNICAL SPECIFICATIONS")
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetadataRow("Model ID", state.modelId)
        MetadataRow("Provider", state.modelEntry?.provider ?: state.installedModel?.source?.name ?: "Local")
        MetadataRow("Format", state.installedModel?.format?.name ?: "LITERTLM")
        MetadataRow("Size", formatFileSize(state.installedModel?.sizeBytes ?: state.modelEntry?.sizeBytes ?: 0))
        state.modelEntry?.license?.let { MetadataRow("License", it) }
    }
}

@Composable
fun ActionSection(state: ModelDetailsState, onIntent: (ModelDetailsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.isInstalled && !state.isActionInProgress) {
            PrimaryAction(
                label = "DOWNLOAD ENGINE",
                icon = Icons.Default.Download,
                onClick = { onIntent(ModelDetailsIntent.Download) }
            )
        }
        
        if (state.isInstalled && !state.isActive && !state.isActionInProgress) {
            PrimaryAction(
                label = "ACTIVATE ENGINE",
                icon = Icons.Default.Sync,
                onClick = { onIntent(ModelDetailsIntent.ConfirmSwitch) }
            )
        }
        
        if (state.isInstalled && !state.isActive && !state.isActionInProgress) {
            SecondaryAction(
                label = "DELETE COMPONENT",
                icon = Icons.Default.Delete,
                onClick = { onIntent(ModelDetailsIntent.Delete) }
            )
        }
        
        if (state.isActionInProgress) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = Typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompatibilityRow(icon: ImageVector, label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, 
            null, 
            modifier = Modifier.size(16.dp), 
            tint = if (isOk) Color.Green.copy(0.7f) else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(label, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
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
    if (size <= 0) return "Unknown size"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return "%.1f %s".format(size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
