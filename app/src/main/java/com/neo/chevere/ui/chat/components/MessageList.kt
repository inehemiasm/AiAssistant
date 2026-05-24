package com.neo.chevere.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neo.chevere.R
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.agent.AgentStep
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.ui.common.MarkdownContent
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
 * @param onShareMessage Called when the user wants to share an assistant message.
 * @param onSaveImage Called when the user wants to save an assistant image.
 * @param onReadMessageAloud Called when the user wants the assistant response read aloud.
 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    streamingText: String = "",
    streamingModelName: String = "",
    agentState: AgentState = AgentState.Idle,
    onToggleExplicitImageMask: (Int) -> Unit = {},
    onShareMessage: (Int) -> Unit = {},
    onSaveImage: (Int) -> Unit = {},
    onReadMessageAloud: (Int) -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        itemsIndexed(messages) { index, message ->
            FuturisticChatBubble(
                message = message,
                onToggleExplicitImageMask = { onToggleExplicitImageMask(index) },
                onShareMessage = { onShareMessage(index) },
                onSaveImage = { onSaveImage(index) },
                onReadMessageAloud = { onReadMessageAloud(index) }
            )
        }

        if (agentState !is AgentState.Idle) {
            item {
                AgentThoughtCard(agentState = agentState)
            }
        }

        if (streamingText.isNotBlank()) {
            item {
                FuturisticChatBubble(
                    message = ChatMessage(
                        text = streamingText,
                        isUser = false,
                        modelName = streamingModelName.ifBlank { "CHEVERE AI" }
                    ),
                    showCursor = false
                )
            }
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
 * @param onShareMessage Called when the share button is tapped.
 * @param onSaveImage Called when the save image button is tapped.
 * @param onReadMessageAloud Called when the read-aloud button is tapped.
 */
@Composable
fun FuturisticChatBubble(
    message: ChatMessage,
    onToggleExplicitImageMask: () -> Unit = {},
    onShareMessage: () -> Unit = {},
    onSaveImage: () -> Unit = {},
    onReadMessageAloud: () -> Unit = {},
    showCursor: Boolean = false
) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val onBubbleColor = MaterialTheme.colorScheme.onSurface
    val borderColor = if (isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
    }
    val bubbleShape = RoundedCornerShape(
        topStart = if (isUser) 24.dp else 10.dp,
        topEnd = if (isUser) 10.dp else 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

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
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        CircleShape
                    )
                    .padding(5.dp)
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                color = bubbleColor,
                contentColor = onBubbleColor,
                shape = bubbleShape,
                tonalElevation = if (isUser) 2.dp else 0.dp,
                shadowElevation = if (isUser) 2.dp else 5.dp,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .shadow(
                        elevation = if (isUser) 3.dp else 8.dp,
                        shape = bubbleShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                    )
                    .border(1.dp, borderColor, bubbleShape)
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
                        MarkdownContent(
                            text = message.text,
                            textStyle = Typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = onBubbleColor
                            ),
                            textColor = onBubbleColor,
                            showCursor = showCursor
                        )
                    }

                    if (!isUser) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                message.inferenceTimeMs?.let { timeMs ->
                                    val seconds = timeMs / 1000.0
                                    val tps =
                                        if (seconds > 0) message.text.length / (seconds * 4) else 0.0

                                    Badge(text = "%.2fs".format(Locale.US, seconds))
                                    Badge(text = "%.1f tk/s".format(Locale.US, tps))
                                } ?: run {
                                    Badge(text = stringResource(R.string.hardware_accel))
                                    Badge(text = stringResource(R.string.privacy_lock))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (message.imageUri != null) {
                                    IconButton(
                                        onClick = onSaveImage,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = stringResource(R.string.save_image),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            ),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onReadMessageAloud,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = stringResource(R.string.read_message_aloud),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onShareMessage,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.share_message),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val footerText = if (isUser) {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                "$time - ${stringResource(R.string.sent_status)}"
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
                        style = Typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(24.dp),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = Typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

@Composable
fun AgentThoughtCard(
    agentState: AgentState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val steps = agentState.steps
    val isBusy = agentState is AgentState.Planning || agentState is AgentState.ExecutingTool
    val transition = rememberInfiniteTransition(label = "AgentThoughtPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isBusy) 0.78f else 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AgentThoughtPulseAlpha"
    )
    val glowRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 4200)),
        label = "AgentThoughtGlowRotation"
    )
    val accentColor by transition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary,
        targetValue = MaterialTheme.colorScheme.secondary,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AgentThoughtAccent"
    )
    val statusText = agentStatusText(agentState)
    val latestActivity = latestAgentActivity(agentState)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            accentColor.copy(alpha = pulseAlpha),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                            accentColor.copy(alpha = pulseAlpha)
                        )
                    )
                )
                .rotate(glowRotation)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ),
            border = BorderStroke(
                1.dp,
                accentColor.copy(alpha = if (isBusy) pulseAlpha else 0.24f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
                .shadow(
                    elevation = if (isBusy) 10.dp else 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                )
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, accentColor.copy(alpha = pulseAlpha)),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Agent Thought Process",
                                style = Typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusText,
                                style = Typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentStatusIcon(agentState, isBusy)
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse thought process" else "Expand thought process",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !expanded && latestActivity.isNotBlank(),
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    Text(
                        text = latestActivity,
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp, start = 44.dp)
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (steps.isEmpty()) {
                            ThinkingPlaceholder()
                        } else {
                            steps.forEachIndexed { index, step ->
                                StepItem(index = index + 1, step = step)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentStatusIcon(agentState: AgentState, isBusy: Boolean) {
    when {
        isBusy -> CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(16.dp)
        )

        agentState is AgentState.Completed -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Completed",
            tint = Color(0xFF00C853),
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp)
        )

        agentState is AgentState.Error -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp)
        )
    }
}

@Composable
private fun ThinkingPlaceholder() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Planning next steps...",
            style = Typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepItem(index: Int, step: AgentStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f)
                    )
                ),
                RoundedCornerShape(12.dp)
            )
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        CircleShape
                    )
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (step.toolCall != null) {
                    "Calling ${step.toolCall.toolName}"
                } else {
                    "Reasoning"
                },
                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (step.thought != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.thought,
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (step.toolCall != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(6.dp)
            ) {
                Text(
                    text = "Arguments:",
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                step.toolCall.arguments.forEach { (name, value) ->
                    Text(
                        text = "$name = $value",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val result = step.result
        if (result != null) {
            Spacer(modifier = Modifier.height(6.dp))
            val resultColor = when (result) {
                is ToolResult.Success -> Color(0xFF00C853)
                is ToolResult.Error -> MaterialTheme.colorScheme.error
                is ToolResult.NeedsConfirmation -> MaterialTheme.colorScheme.secondary
            }
            val resultText = when (result) {
                is ToolResult.Success -> "Observed: ${result.data}"
                is ToolResult.Error -> "Error: ${result.message}"
                is ToolResult.NeedsConfirmation -> "Needs Confirmation: ${result.message}"
            }
            Text(
                text = resultText,
                style = Typography.bodySmall,
                color = resultColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun agentStatusText(agentState: AgentState): String = when (agentState) {
    is AgentState.Planning -> "Planning the next move"
    is AgentState.ExecutingTool -> "Using ${agentState.toolName}"
    is AgentState.WaitingForConfirmation -> "Waiting for confirmation"
    is AgentState.Completed -> "${agentState.steps.size} step${if (agentState.steps.size == 1) "" else "s"} completed"
    is AgentState.Error -> "Stopped with an error"
    AgentState.Idle -> "Idle"
}

private fun latestAgentActivity(agentState: AgentState): String {
    val latest = agentState.steps.lastOrNull()
    return when {
        latest?.thought?.isNotBlank() == true -> latest.thought
        latest?.toolCall != null -> "Let me call ${latest.toolCall.toolName}..."
        agentState is AgentState.ExecutingTool -> "Let me call ${agentState.toolName}..."
        agentState is AgentState.Planning -> "I am deciding which local tool, if any, is needed."
        agentState is AgentState.WaitingForConfirmation -> agentState.message
        else -> ""
    }
}
