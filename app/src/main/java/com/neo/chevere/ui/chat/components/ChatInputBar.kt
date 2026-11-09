package com.neo.chevere.ui.chat.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neo.chevere.R
import com.neo.chevere.core.Constants
import com.neo.chevere.ui.chat.ComposerActionButtonState
import com.neo.chevere.ui.chat.VoiceMemoAction
import com.neo.chevere.ui.chat.VoiceRecordingState
import com.neo.chevere.ui.chat.HandsFreeState
import com.neo.chevere.ui.designsystem.Typography
import java.util.Locale
import kotlin.math.sin

/**
 * Bottom chat composer with attachment actions, text entry, Whisper-style continuous voice
 * listener, post-recording voice memo actions, busy status, and stop/send control.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    selectedImageUri: Uri?,
    onDocumentClick: () -> Unit = {},
    onFoldersClick: () -> Unit = {},
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveImage: () -> Unit,
    enabled: Boolean,
    buttonState: ComposerActionButtonState,
    busyMessage: String = Constants.UiStatus.THINKING,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    voiceRecordingState: VoiceRecordingState = VoiceRecordingState.Idle,
    onStartVoiceClick: () -> Unit = {},
    onStopVoiceClick: () -> Unit = {},
    onCancelVoiceClick: () -> Unit = {},
    onVoiceMemoActionClick: (VoiceMemoAction) -> Unit = {},
    handsFreeState: HandsFreeState = HandsFreeState.Off,
    onStartHandsFreeClick: () -> Unit = {},
    onStopHandsFreeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val isBusy = buttonState is ComposerActionButtonState.Stop
    val isListening = voiceRecordingState is VoiceRecordingState.Listening

    // Slash command autocomplete: filter whenever text starts with '/'
    val slashSuggestions: List<SlashCommand> by remember(text) {
        derivedStateOf {
            if (!text.startsWith("/")) return@derivedStateOf emptyList()
            val query = text.trim().lowercase()
            chatSlashCommands.filter { it.command.startsWith(query) }
        }
    }

    LaunchedEffect(isBusy) {
        if (isBusy) showAttachmentMenu = false
    }

    Column(modifier = modifier) {
        if (selectedImageUri != null) {
            SelectedImagePreview(
                uri = selectedImageUri,
                onRemoveImage = onRemoveImage
            )
        }

        // Active Whisper-style continuous voice listening banner with reactive waveform
        AnimatedVisibility(
            visible = voiceRecordingState is VoiceRecordingState.Listening && !isBusy,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            val listeningState = voiceRecordingState as? VoiceRecordingState.Listening
                ?: VoiceRecordingState.Listening()
            VoiceRecordingBanner(
                state = listeningState,
                onCancel = onCancelVoiceClick,
                onDone = onStopVoiceClick
            )
        }

        // Post-recording Whisper Memo quick actions (Summarize, Extract Tasks, Save to /docs)
        AnimatedVisibility(
            visible = voiceRecordingState is VoiceRecordingState.Recorded && text.isNotBlank() && !isBusy,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            VoiceMemoActionRow(
                onActionClick = onVoiceMemoActionClick
            )
        }

        // Hands-free conversation mode banner with live status and waveform
        AnimatedVisibility(
            visible = handsFreeState.isActive,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            HandsFreeConversationBanner(
                state = handsFreeState,
                onEnd = onStopHandsFreeClick
            )
        }

        if (suggestions.isNotEmpty() && !isBusy && slashSuggestions.isEmpty() && voiceRecordingState is VoiceRecordingState.Idle) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        suggestion = suggestion,
                        onClick = { onSuggestionClick(suggestion) }
                    )
                }
            }
        }

        // Slash command autocomplete popup (appears above the input bar)
        AnimatedVisibility(
            visible = slashSuggestions.isNotEmpty() && !isBusy && !isListening,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            SlashCommandMenu(
                commands = slashSuggestions,
                onSelect = { command ->
                    if (command == "/voice") {
                        onTextChange("")
                        onStartVoiceClick()
                    } else if (command == "/conversation") {
                        onTextChange("")
                        onStartHandsFreeClick()
                    } else {
                        onTextChange("$command ")
                    }
                }
            )
        }

        val inputShape = RoundedCornerShape(34.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = inputShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                )
                .clip(inputShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        if (isListening) 1.5.dp else 1.dp,
                        if (isListening) MaterialTheme.colorScheme.primary
                        else if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    inputShape
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AnimatedContent(
                targetState = isBusy,
                transitionSpec = {
                    fadeIn(animationSpec = tween(160)).togetherWith(
                        fadeOut(animationSpec = tween(120))
                    )
                },
                label = "InputContent",
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) { busy ->
                if (busy) {
                    InputBusyIndicator(
                        message = busyMessage,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ComposerContent(
                        text = text,
                        onTextChange = onTextChange,
                        enabled = enabled,
                        isListening = isListening,
                        onVoiceButtonClick = {
                            if (isListening) onStopVoiceClick() else onStartVoiceClick()
                        },
                        showAttachmentMenu = showAttachmentMenu,
                        onShowAttachmentMenu = { showAttachmentMenu = true },
                        onDismissAttachmentMenu = { showAttachmentMenu = false },
                        onDocumentClick = onDocumentClick,
                        onFoldersClick = onFoldersClick,
                        onGalleryClick = onGalleryClick,
                        onCameraClick = onCameraClick
                    )
                }
            }

            ComposerActionButton(
                state = buttonState,
                onSend = onSend,
                onStop = onStop,
                onHandsFree = onStartHandsFreeClick
            )
        }
    }
}

@Composable
private fun VoiceRecordingBanner(
    state: VoiceRecordingState.Listening,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val minutes = state.elapsedSeconds / 60
    val seconds = state.elapsedSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel recording button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.voice_cancel),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.voice_listening_status),
                        style = Typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formattedTime,
                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(6.dp))

                VoiceWaveformBars(
                    amplitude = state.amplitude,
                    tick = state.elapsedSeconds
                )
            }

            Spacer(Modifier.width(10.dp))

            // Done / Commit recording button
            IconButton(
                onClick = onDone,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.voice_done),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveformBars(
    amplitude: Float,
    tick: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 16
        for (i in 0 until barCount) {
            val waveOffset = ((sin((i * 0.7f) + (tick * 1.3f)) + 1f) * 0.5f).toFloat()
            val targetFraction = (amplitude * (0.45f + 0.55f * waveOffset)).coerceIn(0.15f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 120),
                label = "WaveformBar$i"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((22f * animatedFraction).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = (0.45f + 0.55f * animatedFraction).coerceIn(0.4f, 1f)
                        )
                    )
            )
        }
    }
}

@Composable
private fun VoiceMemoActionRow(
    onActionClick: (VoiceMemoAction) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            VoiceMemoActionChip(
                icon = Icons.Default.Summarize,
                label = stringResource(R.string.voice_memo_summarize),
                onClick = { onActionClick(VoiceMemoAction.SUMMARIZE) }
            )
        }
        item {
            VoiceMemoActionChip(
                icon = Icons.Default.Checklist,
                label = stringResource(R.string.voice_memo_extract_tasks),
                onClick = { onActionClick(VoiceMemoAction.EXTRACT_TASKS) }
            )
        }
        item {
            VoiceMemoActionChip(
                icon = Icons.Default.Description,
                label = stringResource(R.string.voice_memo_save_docs),
                onClick = { onActionClick(VoiceMemoAction.SAVE_TO_DOCS) }
            )
        }
    }
}

@Composable
private fun VoiceMemoActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SelectedImagePreview(
    uri: Uri,
    onRemoveImage: () -> Unit
) {
    Box(
        Modifier
            .size(112.dp)
            .padding(bottom = 10.dp, start = 16.dp)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp),
            shadowElevation = 2.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            IconButton(
                onClick = onRemoveImage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposerContent(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isListening: Boolean,
    onVoiceButtonClick: () -> Unit,
    showAttachmentMenu: Boolean,
    onShowAttachmentMenu: () -> Unit,
    onDismissAttachmentMenu: () -> Unit,
    onDocumentClick: () -> Unit = {},
    onFoldersClick: () -> Unit = {},
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.Bottom) {
        AttachmentButton(
            enabled = enabled,
            showAttachmentMenu = showAttachmentMenu,
            onShowAttachmentMenu = onShowAttachmentMenu,
            onDismissAttachmentMenu = onDismissAttachmentMenu,
            onDocumentClick = onDocumentClick,
            onFoldersClick = onFoldersClick,
            onGalleryClick = onGalleryClick,
            onCameraClick = onCameraClick
        )

        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            placeholder = {
                Text(
                    stringResource(
                        if (isListening) R.string.voice_listening_status else R.string.input_placeholder
                    ),
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
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
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
        )

        IconButton(
            onClick = onVoiceButtonClick,
            enabled = enabled || isListening,
            modifier = Modifier
                .padding(end = 4.dp, bottom = 2.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else Color.Transparent
                )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Check else Icons.Default.Mic,
                contentDescription = stringResource(
                    if (isListening) R.string.voice_done else R.string.voice_input_button
                ),
                tint = if (isListening) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AttachmentButton(
    enabled: Boolean,
    showAttachmentMenu: Boolean,
    onShowAttachmentMenu: () -> Unit,
    onDismissAttachmentMenu: () -> Unit,
    onDocumentClick: () -> Unit = {},
    onFoldersClick: () -> Unit = {},
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val textResources = LocalResources.current
    Box {
        IconButton(
            onClick = onShowAttachmentMenu,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        )
                    )
                )
        ) {
            Icon(
                Icons.Default.Add,
                stringResource(R.string.add_attachment),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        DropdownMenu(
            expanded = showAttachmentMenu,
            onDismissRequest = onDismissAttachmentMenu,
            modifier = Modifier
                .width(188.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_folders)) },
                onClick = { onFoldersClick(); onDismissAttachmentMenu() }
            )
            DropdownMenuItem(
                text = { Text(textResources.getString(R.string.ui_import_document)) },
                onClick = { onDocumentClick(); onDismissAttachmentMenu() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.gallery),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    AttachmentMenuIcon {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                onClick = {
                    onGalleryClick()
                    onDismissAttachmentMenu()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.camera),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    AttachmentMenuIcon {
                        Icon(
                            Icons.Default.PhotoCamera,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                onClick = {
                    onCameraClick()
                    onDismissAttachmentMenu()
                }
            )
        }
    }
}

@Composable
private fun ComposerActionButton(
    state: ComposerActionButtonState,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onHandsFree: () -> Unit = {}
) {
    val actionEnabled = state !is ComposerActionButtonState.Disabled
    val isBusy = state is ComposerActionButtonState.Stop
    val isHandsFree = state is ComposerActionButtonState.HandsFree
    Surface(
        color = when {
            isBusy -> MaterialTheme.colorScheme.errorContainer
            isHandsFree -> MaterialTheme.colorScheme.primaryContainer
            actionEnabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            isBusy -> MaterialTheme.colorScheme.onErrorContainer
            isHandsFree -> MaterialTheme.colorScheme.primary
            actionEnabled -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        shape = CircleShape,
        modifier = Modifier
            .padding(bottom = 1.dp, end = 1.dp)
            .size(52.dp),
        shadowElevation = if (actionEnabled) 8.dp else 0.dp
    ) {
        IconButton(
            onClick = {
                when (state) {
                    ComposerActionButtonState.Stop -> onStop()
                    ComposerActionButtonState.Send -> onSend()
                    ComposerActionButtonState.HandsFree -> onHandsFree()
                    ComposerActionButtonState.Disabled -> {}
                }
            },
            enabled = actionEnabled
        ) {
            Icon(
                when {
                    isBusy -> Icons.Default.Stop
                    isHandsFree -> Icons.Default.GraphicEq
                    else -> Icons.AutoMirrored.Filled.Send
                },
                stringResource(
                    when {
                        isBusy -> R.string.stop_response
                        isHandsFree -> R.string.hands_free_start
                        else -> R.string.send
                    }
                ),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun HandsFreeConversationBanner(
    state: HandsFreeState,
    onEnd: () -> Unit
) {
    val statusText = when (state) {
        is HandsFreeState.Listening -> stringResource(R.string.hands_free_listening)
        is HandsFreeState.Thinking -> stringResource(R.string.hands_free_thinking)
        is HandsFreeState.Speaking -> stringResource(R.string.hands_free_speaking)
        else -> stringResource(R.string.hands_free_mode_title)
    }

    val amplitude = when (state) {
        is HandsFreeState.Listening -> state.amplitude
        is HandsFreeState.Speaking -> 0.45f
        is HandsFreeState.Thinking -> 0.15f
        else -> 0.1f
    }

    val accentColor = when (state) {
        is HandsFreeState.Listening -> MaterialTheme.colorScheme.primary
        is HandsFreeState.Thinking -> MaterialTheme.colorScheme.tertiary
        is HandsFreeState.Speaking -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusText.uppercase(Locale.getDefault()),
                        style = Typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = accentColor
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.hands_free_mode_title),
                        style = Typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                VoiceWaveformBars(
                    amplitude = amplitude,
                    tick = if (state is HandsFreeState.Speaking || state is HandsFreeState.Thinking) 1 else 0
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.hands_free_hint),
                    style = Typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.width(12.dp))

            IconButton(
                onClick = onEnd,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.hands_free_end),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputBusyIndicator(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(34.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                indicatorColor = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message.uppercase(),
                style = Typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
private fun AttachmentMenuIcon(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = CircleShape,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun SlashCommandMenu(
    commands: List<SlashCommand>,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .heightIn(max = 280.dp)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(commands, key = { it.command }) { cmd ->
                SlashCommandRow(
                    command = cmd,
                    onClick = { onSelect(cmd.command) }
                )
            }
        }
    }
}

@Composable
private fun SlashCommandRow(
    command: SlashCommand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = command.command,
                style = Typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(command.labelRes),
                style = Typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(command.descriptionRes),
                style = Typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SuggestionChip(
    suggestion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textResources = LocalResources.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .semantics { contentDescription = textResources.getString(R.string.ui_suggested_next_message_1_s, suggestion) }
            .clickable(onClick = onClick)
    ) {
        Text(
            text = suggestion,
            style = Typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}
