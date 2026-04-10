package com.neo.aiassistant.ui.chat.components

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neo.aiassistant.R
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.ui.common.parseMarkdown
import com.neo.aiassistant.ui.designsystem.Typography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
