package com.neo.aiassistant.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neo.aiassistant.CatalogState
import com.neo.aiassistant.PerformanceMetrics
import com.neo.aiassistant.R
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.ui.designsystem.AmbientGlow
import com.neo.aiassistant.ui.designsystem.HighTechPrimaryButton
import com.neo.aiassistant.ui.designsystem.ModelSelectorCard
import com.neo.aiassistant.ui.designsystem.StatCard
import com.neo.aiassistant.ui.designsystem.Typography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadProgressView(modelName: String, progress: Int) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.Center).size(400.dp))
        
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(120.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = "$progress%", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    style = Typography.headlineMedium.copy(fontSize = 24.sp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                stringResource(R.string.synthesizing_core), 
                color = MaterialTheme.colorScheme.onSurface, 
                style = Typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            
            Text(
                modelName.replace(".litertlm", "").uppercase(), 
                color = MaterialTheme.colorScheme.primary, 
                style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                stringResource(R.string.synthesizing_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun ErrorSnackbar(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss), color = MaterialTheme.colorScheme.inversePrimary)
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = Typography.bodySmall)
        }
    }
}

@Composable
fun QuantumThinkingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "thinking")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(
                        color = Color.Cyan.copy(alpha = alpha * 0.2f),
                        radius = size.minDimension / 1.5f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = Color.Cyan.copy(alpha = alpha),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = (statusMessage ?: stringResource(R.string.thinking)).uppercase(),
                style = Typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Cyan.copy(alpha = alpha)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    isInteractionEnabled: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    onClearChat: () -> Unit,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onModelsClick) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu_library), tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().clickable { if (isInteractionEnabled) showMenu = true }
            ) {
                Text(
                    text = selectedModel.replace(".litertlm", "").uppercase(),
                    style = Typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Text(
                    text = stringResource(R.string.nebula_core_private_ai),
                    style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    model.replace(".litertlm", "").uppercase(), 
                                    color = if (model == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = { onModelSelected(model); showMenu = false }
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onClearChat, enabled = isInteractionEnabled) {
                Icon(Icons.Default.DeleteSweep, stringResource(R.string.clear_chat), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Person, 
                    stringResource(R.string.profile), 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape).padding(4.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
    )
}

@Composable
fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier, listState: LazyListState) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        items(messages) { message -> FuturisticChatBubble(message) }
    }
}

@Composable
fun FuturisticChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
    val onBubbleColor = MaterialTheme.colorScheme.onSurface
    val borderColor = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 12.dp, top = 8.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(4.dp)
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                color = bubbleColor,
                contentColor = onBubbleColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (message.imageUri != null) {
                        AsyncImage(
                            model = message.imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    SelectionContainer {
                        val styledText = parseMarkdown(message.text)
                        Text(
                            text = styledText,
                            style = Typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = onBubbleColor
                            )
                        )
                    }
                    
                    if (!isUser) {
                        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            message.inferenceTimeMs?.let { time ->
                                Badge(text = "${time}ms")
                            } ?: Badge(text = stringResource(R.string.hardware_accel))
                            
                            Badge(text = stringResource(R.string.privacy_lock))
                        }
                    }
                }
            }
            
            val footerText = if (isUser) {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                "$time • ${stringResource(R.string.sent_status)}"
            } else {
                stringResource(R.string.model_optimization_info)
            }
            
            Text(
                text = footerText.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = Typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    selectedImageUri: Uri?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveImage: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        if (selectedImageUri != null) {
            Box(Modifier.padding(bottom = 8.dp).size(80.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)), RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(
                    onClick = { showAttachmentMenu = true },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        stringResource(R.string.add_attachment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gallery), color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onGalleryClick()
                            showAttachmentMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.camera), color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onCameraClick()
                            showAttachmentMenu = false
                        }
                    )
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        style = Typography.bodyMedium
                    )
                },
                enabled = enabled,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                maxLines = 4
            )

            val isSendEnabled = enabled && (text.isNotBlank() || selectedImageUri != null)
            IconButton(
                onClick = { if (isSendEnabled) onSend() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                enabled = isSendEnabled
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    stringResource(R.string.send),
                    tint = if (isSendEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BeautifulModelMissingView(
    selectedModel: String,
    localModels: List<LocalModel>,
    remoteModels: List<com.neo.aiassistant.domain.ModelEntry>,
    availableDownloads: List<com.neo.aiassistant.domain.ModelEntry> = emptyList(),
    catalogState: CatalogState,
    metrics: PerformanceMetrics,
    onDownloadClick: (String) -> Unit,
    onClearError: () -> Unit
) {
    var internalSelectedModel by remember { mutableStateOf(selectedModel) }

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
                                params = if (model.sizeBytes > 0) "%.1fGB".format(model.sizeBytes / 1e9) else "N/A",
                                vram = if (model.sizeBytes > 0) "%.1fGB".format(model.sizeBytes * 2 / 1e9) else "N/A",
                                isSelected = internalSelectedModel == model.effectiveFileName,
                                icon = if (model.name.contains("2b", ignoreCase = true)) Icons.Default.Speed else Icons.Default.Bolt,
                                onClick = { internalSelectedModel = model.effectiveFileName }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        ModelSelectorCard(
                            title = stringResource(R.string.gemma_4_e4b_it),
                            description = stringResource(R.string.gemma_4_e4b_desc),
                            params = "4.2",
                            vram = "8.4GB",
                            isSelected = internalSelectedModel.contains("E4B"),
                            icon = Icons.Default.Bolt,
                            onClick = { internalSelectedModel = "gemma-4-E4B-it.litertlm" }
                        )

                        Spacer(Modifier.height(16.dp))

                        ModelSelectorCard(
                            title = stringResource(R.string.gemma_4_e2b_it),
                            description = stringResource(R.string.gemma_4_e2b_desc),
                            params = "2.1",
                            vram = "3.2GB",
                            isSelected = internalSelectedModel.contains("E2B"),
                            icon = Icons.Default.Speed,
                            onClick = { internalSelectedModel = "gemma-4-E2B-it.litertlm" }
                        )
                    }

                    if (availableDownloads.isNotEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Available Downloads",
                            style = Typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        availableDownloads.forEach { model ->
                            ModelSelectorCard(
                                title = model.name,
                                description = model.description,
                                params = if (model.sizeBytes > 0) "%.1fGB".format(model.sizeBytes / 1e9) else "N/A",
                                vram = if (model.sizeBytes > 0) "%.1fGB".format(model.sizeBytes * 2 / 1e9) else "N/A",
                                isSelected = internalSelectedModel == model.effectiveFileName,
                                icon = if (model.name.contains("2b", ignoreCase = true)) Icons.Default.Speed else Icons.Default.CloudDownload,
                                onClick = { internalSelectedModel = model.effectiveFileName }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    Spacer(Modifier.height(48.dp))
                    
                    val isModelDownloaded = localModels.any { it.fileName == internalSelectedModel }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.engine_status), style = Typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isModelDownloaded) MaterialTheme.colorScheme.primary else Color.Gray))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isModelDownloaded) stringResource(R.string.core_ready) else stringResource(R.string.download_required), color = MaterialTheme.colorScheme.onSurface, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    HighTechPrimaryButton(
                        onClick = { onDownloadClick(internalSelectedModel) },
                        text = if (isModelDownloaded) stringResource(R.string.confirm_engine_switch) else stringResource(R.string.download_sync_core)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_settings), style = Typography.titleLarge, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
            AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.BottomEnd).offset(x = 100.dp, y = 100.dp).size(400.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    stringResource(R.string.interface_configuration), 
                    style = Typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.high_tech_mode), style = Typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (isDarkMode) stringResource(R.string.neural_dark_active) else stringResource(R.string.standard_light_active),
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onThemeChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                Text(
                    stringResource(R.string.system_info), 
                    style = Typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                
                SystemInfoRow(stringResource(R.string.version_label), "1.0.0-STABLE")
                SystemInfoRow(stringResource(R.string.engine_label), stringResource(R.string.gemma_4_edge))
                SystemInfoRow(stringResource(R.string.protocol_label), stringResource(R.string.on_device_inference))
            }
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
    }
}

fun parseMarkdownLogic(
    text: String,
    codeBackground: Color,
    primaryColor: Color
): AnnotatedString {
    // Remove surrounding brackets if they exist
    var cleanText = text.trim()
    if (cleanText.startsWith("[") && cleanText.endsWith("]")) {
        cleanText = cleanText.substring(1, cleanText.length - 1).trim()
    }

    // Replace multiline bullet points
    cleanText = cleanText.replace(Regex("^\\s*[*+]\\s+", RegexOption.MULTILINE), " • ")
    cleanText = cleanText.replace(Regex("^\\s*-\\s+", RegexOption.MULTILINE), " • ")

    return buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        val italicRegex = Regex("""\*(?!\*)(.*?)\*""")
        val codeRegex = Regex("""`(.*?)`""")
        val highlightRegex = Regex("(Nebula Core)") // Example of keyword highlighting from design

        var currentPos = 0
        
        while (currentPos < cleanText.length) {
            val bMatch = boldRegex.find(cleanText, currentPos)
            val iMatch = italicRegex.find(cleanText, currentPos)
            val cMatch = codeRegex.find(cleanText, currentPos)
            val hMatch = highlightRegex.find(cleanText, currentPos)

            val matches = listOfNotNull(bMatch, iMatch, cMatch, hMatch)
                .sortedWith(compareBy({ it.range.first }, { -it.value.length }))

            if (matches.isEmpty()) {
                append(cleanText.substring(currentPos))
                break
            }

            val match = matches.first()
            
            if (match.range.first > currentPos) {
                append(cleanText.substring(currentPos, match.range.first))
            }

            val start = length
            val content = if (match.groupValues.size > 1) match.groupValues[1] else ""
            append(content)
            val end = length

            when {
                match.value.startsWith("**") -> {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                match.value.startsWith("`") -> {
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = primaryColor
                        ), start, end
                    )
                }
                match.value.startsWith("*") -> {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                match.value == "Nebula Core" -> {
                    addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), start, end)
                }
            }
            
            currentPos = match.range.last + 1
        }
    }
}

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
    val primaryColor = MaterialTheme.colorScheme.primary
    return parseMarkdownLogic(text, codeBackground, primaryColor)
}
