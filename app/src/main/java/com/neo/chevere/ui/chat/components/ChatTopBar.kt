package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.chat.ChatModelStatus
import com.neo.chevere.ui.designsystem.Typography

/**
 * Brand-forward chat top bar displaying the active model or runtime status pill
 * directly beneath the title. Tapping the status pill opens the Models Library.
 */
@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    isChatReady: Boolean,
    isImageReady: Boolean,
    modelStatus: ChatModelStatus = ChatModelStatus.NeedsModel,
    onModelStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textResources = LocalResources.current
    val topBarShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

    val (chipLabel, chipTone) = when (modelStatus) {
        is ChatModelStatus.Ready -> formatModelBadgeName(modelStatus.name).uppercase() to StatusPillTone.READY
        ChatModelStatus.Generating -> textResources.getString(R.string.model_status_generating).uppercase() to StatusPillTone.ACTIVE
        is ChatModelStatus.Loading -> textResources.getString(
            R.string.model_status_loading,
            formatModelBadgeName(modelStatus.detail)
        ).uppercase() to StatusPillTone.ACTIVE
        is ChatModelStatus.Downloading -> textResources.getString(
            R.string.model_status_downloading,
            formatModelBadgeName(modelStatus.name),
            modelStatus.percent
        ).uppercase() to StatusPillTone.ACTIVE
        ChatModelStatus.AwaitingConfirmation -> textResources.getString(R.string.ui_waiting_for_confirmation).uppercase() to StatusPillTone.ACTIVE
        is ChatModelStatus.Failed -> textResources.getString(R.string.model_status_retry).uppercase() to StatusPillTone.ERROR
        ChatModelStatus.NeedsModel -> {
            if (isChatReady) {
                textResources.getString(R.string.ui_chat).uppercase() to StatusPillTone.READY
            } else {
                textResources.getString(R.string.model_status_choose).uppercase() to StatusPillTone.IDLE
            }
        }
    }

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = topBarShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = topBarShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
                        )
                    )
                )
                .statusBarsPadding()
                .height(72.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    stringResource(R.string.menu_library),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "CHEVERE AI",
                    style = Typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CapabilityChip(
                        label = chipLabel,
                        tone = chipTone,
                        onClick = onModelStatusClick
                    )
                    if (isImageReady) {
                        CapabilityChip(
                            label = textResources.getString(R.string.ui_image),
                            tone = StatusPillTone.READY,
                            onClick = onModelStatusClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(44.dp))
        }
    }
}

private enum class StatusPillTone {
    READY,
    ACTIVE,
    IDLE,
    ERROR
}

internal fun formatModelBadgeName(rawName: String): String {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) return rawName
    return trimmed
        .removeSuffix(".litertlm")
        .removeSuffix(".bin")
        .removeSuffix("-web")
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ),
        shadowElevation = 3.dp,
        modifier = Modifier.size(44.dp)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    tone: StatusPillTone,
    onClick: () -> Unit
) {
    val contentColor = when (tone) {
        StatusPillTone.READY, StatusPillTone.ACTIVE -> MaterialTheme.colorScheme.primary
        StatusPillTone.ERROR -> MaterialTheme.colorScheme.error
        StatusPillTone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when (tone) {
        StatusPillTone.READY, StatusPillTone.ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        StatusPillTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        StatusPillTone.IDLE -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(contentColor, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
            letterSpacing = 1.0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
