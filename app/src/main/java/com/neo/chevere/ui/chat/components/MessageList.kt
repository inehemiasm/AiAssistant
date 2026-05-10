package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neo.chevere.R
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.ui.common.parseMarkdown
import com.neo.chevere.ui.designsystem.Typography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A scrollable list of chat messages.
 *
 * @param messages The list of [ChatMessage] objects to display.
 * @param modifier The modifier to be applied to the list.
 * @param listState The state object to be used to control or observe the list's scroll position.
 * @param onToggleExplicitImageMask Called when the user reveals or hides a masked explicit image.
 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    onToggleExplicitImageMask: (Int) -> Unit = {}
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        itemsIndexed(messages) { index, message ->
            FuturisticChatBubble(
                message = message,
                onToggleExplicitImageMask = { onToggleExplicitImageMask(index) }
            )
        }
    }
}

/**
 * A visually styled chat bubble for a single message.
 *
 * Displays the message text (with markdown support), any associated image,
 * and metadata like inference time and model name.
 *
 * @param message The [ChatMessage] to display.
 * @param onToggleExplicitImageMask Called when the explicit image visibility button is tapped.
 */
@Composable
fun FuturisticChatBubble(
    message: ChatMessage,
    onToggleExplicitImageMask: () -> Unit = {}
) {
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
                        GeneratedMessageImage(
                            imageUri = message.imageUri,
                            isExplicitImage = message.isExplicitImage,
                            isMasked = message.isImageMasked,
                            onToggleMask = onToggleExplicitImageMask
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
                            message.inferenceTimeMs?.let { timeMs ->
                                val seconds = timeMs / 1000.0
                                val tps = if (seconds > 0) message.text.length / (seconds * 4) else 0.0
                                
                                Badge(text = "%.2fs".format(Locale.US, seconds))
                                Badge(text = "%.1f tk/s".format(Locale.US, tps))
                            } ?: run {
                                Badge(text = stringResource(R.string.hardware_accel))
                                Badge(text = stringResource(R.string.privacy_lock))
                            }
                        }
                    }
                }
            }
            
            val footerText = if (isUser) {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                "$time • ${stringResource(R.string.sent_status)}"
            } else {
                message.modelName ?: stringResource(R.string.model_optimization_info)
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

/**
 * Renders a generated image, optionally hidden behind an explicit-content mask
 * that can be toggled by the user.
 */
@Composable
private fun GeneratedMessageImage(
    imageUri: String,
    isExplicitImage: Boolean,
    isMasked: Boolean,
    onToggleMask: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .fillMaxWidth()
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExplicitImage && isMasked) Modifier.blur(18.dp) else Modifier),
            contentScale = ContentScale.FillWidth
        )

        if (isExplicitImage) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isMasked) 0.78f else 0.64f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(onClick = onToggleMask, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isMasked) "Show explicit image" else "Hide explicit image",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isMasked) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "EXPLICIT IMAGE",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * A small badge used to display metadata within a chat bubble.
 *
 * @param text The text to display in the badge.
 */
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
