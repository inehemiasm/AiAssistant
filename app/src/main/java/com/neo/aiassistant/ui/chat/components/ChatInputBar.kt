package com.neo.aiassistant.ui.chat.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.neo.aiassistant.R
import com.neo.aiassistant.ui.designsystem.Typography

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
    selectedImageUri: Uri?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveImage: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (selectedImageUri != null) {
            Box(Modifier.size(80.dp).padding(bottom = 8.dp, start = 16.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)), RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom 
        ) {
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
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
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
            IconButton(
                onClick = { if (isSendEnabled) onSend() },
                modifier = Modifier
                    .padding(bottom = 2.dp, end = 2.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .align(Alignment.CenterVertically),
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
