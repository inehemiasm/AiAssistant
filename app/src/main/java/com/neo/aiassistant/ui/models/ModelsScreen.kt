package com.neo.aiassistant.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.aiassistant.R
import com.neo.aiassistant.ui.common.DownloadProgressView
import com.neo.aiassistant.ui.designsystem.Typography
import com.neo.aiassistant.ui.models.components.BeautifulModelMissingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    onMarketplaceClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.neural_cores), style = Typography.titleLarge, letterSpacing = 2.sp) },
                actions = {
                    IconButton(onClick = onMarketplaceClick) {
                        Icon(Icons.Default.Storefront, contentDescription = "Marketplace", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.isDownloading) {
                DownloadProgressView(
                    modelName = state.selectedModel,
                    progress = state.downloadProgress ?: 0
                )
            } else {
                BeautifulModelMissingView(
                    activeModel = state.selectedModel,
                    pendingModel = state.pendingModel,
                    localModels = state.localModels,
                    remoteModels = state.remoteModels,
                    availableDownloads = state.availableDownloads,
                    catalogState = state.catalogState,
                    metrics = state.metrics,
                    switchState = state.switchState,
                    onModelSelect = { modelName ->
                        viewModel.onIntent(ModelsIntent.SelectModel(modelName))
                    },
                    onConfirmSwitch = { modelName, modelPath ->
                        viewModel.onIntent(ModelsIntent.ConfirmSwitch(modelName, modelPath))
                    },
                    onDownloadClick = { modelName ->
                        viewModel.onIntent(ModelsIntent.DownloadModel(modelName, context.filesDir.absolutePath))
                    },
                    onClearError = {
                        viewModel.onIntent(ModelsIntent.ClearError)
                    }
                )
            }
        }
    }
}
