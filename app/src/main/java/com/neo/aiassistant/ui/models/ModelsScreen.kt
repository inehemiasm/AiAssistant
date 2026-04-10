package com.neo.aiassistant.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.neo.aiassistant.ChatIntent
import com.neo.aiassistant.ChatViewModel
import com.neo.aiassistant.R
import com.neo.aiassistant.ui.common.DownloadProgressView
import com.neo.aiassistant.ui.designsystem.Typography
import com.neo.aiassistant.ui.models.components.BeautifulModelMissingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ChatViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.neural_cores), style = Typography.titleLarge, letterSpacing = 2.sp) },
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
                    selectedModel = state.selectedModel,
                    localModels = state.localModels,
                    remoteModels = state.remoteModels,
                    availableDownloads = state.availableDownloads,
                    catalogState = state.catalogState,
                    metrics = state.metrics,
                    onDownloadClick = { modelName ->
                        viewModel.onIntent(ChatIntent.DownloadModel(modelName, context.filesDir.absolutePath))
                    },
                    onClearError = {
                        viewModel.onIntent(ChatIntent.ClearError)
                    }
                )
            }
        }
    }
}
