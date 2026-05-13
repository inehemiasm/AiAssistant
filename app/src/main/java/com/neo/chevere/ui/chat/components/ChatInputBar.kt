package com.neo.chevere.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * A bottom input bar for the chat screen.
 *
 * Provides a text field for message input, an attachment menu for selecting
 * images from the gallery or camera, and a send button. It also displays a
 * preview of any currently selected image.
 *
 * @param text The current text in the input field.
 * @param onTextChange Callback triggered when the text changes.
 * @param onSend Callback triggered when the send button is clicked.
 * @param selectedImageUri The URI of the currently selected image, if any.
 * @param onGalleryClick Callback triggered when the "gallery" option is selected.
 * @param onCameraClick Callback triggered when the "camera" option is selected.
 * @param onRemoveImage Callback triggered when the user wants to remove the selected image.
 * @param enabled Whether the input bar is enabled for user interaction.
 * @param modifier The modifier to be applied to the input bar.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    selectedImageUri: Uri?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveImage: () -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (selectedImageUri != null) {
            Box(Modifier.size(112.dp).padding(bottom = 10.dp, start = 16.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .border(
                    BorderStroke(
                        1.dp,
                        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom 
        ) {
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                IconButton(
                    onClick = { showAttachmentMenu = true },
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f))
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
                    onDismissRequest = { showAttachmentMenu = false },
                    modifier = Modifier
                        .width(188.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gallery), color = MaterialTheme.colorScheme.onSurface) },
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
                            showAttachmentMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.camera), color = MaterialTheme.colorScheme.onSurface) },
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
                            showAttachmentMenu = false
                        }
                    )
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        style = Typography.bodyMedium
                    )
                },
                // We now strictly disable the TextField when the model is busy to provide clear feedback.
                // The keyboard is explicitly dismissed by a HideKeyboard effect from the ViewModel.
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

            val isSendEnabled = enabled && (text.isNotBlank() || selectedImageUri != null)
            val actionEnabled = isBusy || isSendEnabled
            Surface(
                color = when {
                    isBusy -> MaterialTheme.colorScheme.primary
                    isSendEnabled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSendEnabled || isBusy) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 1.dp, end = 1.dp)
                    .size(52.dp)
                    .align(Alignment.CenterVertically),
                shadowElevation = if (actionEnabled) 3.dp else 0.dp
            ) {
                IconButton(
                    onClick = {
                        if (isBusy) {
                            onStop()
                        } else if (isSendEnabled) {
                            onSend()
                        }
                    },
                    enabled = actionEnabled
                ) {
                    Icon(
                        if (isBusy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        stringResource(if (isBusy) R.string.stop_response else R.string.send),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
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
