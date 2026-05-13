package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * Brand-forward chat top bar with model capability status and quick actions.
 */
@Composable
fun ChatTopBar(
    isInteractionEnabled: Boolean,
    isChatReady: Boolean,
    isImageReady: Boolean,
    onClearChat: () -> Unit,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIconButton(onClick = onModelsClick) {
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
                    CapabilityChip(label = "CHAT", ready = isChatReady)
                    CapabilityChip(label = "IMAGE", ready = isImageReady)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopBarIconButton(onClick = onClearChat, enabled = isInteractionEnabled) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        stringResource(R.string.clear_chat),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TopBarIconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Person,
                        stringResource(R.string.profile),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        ),
        modifier = Modifier.size(44.dp)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}

@Composable
private fun CapabilityChip(label: String, ready: Boolean) {
    val contentColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (ready) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(contentColor, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
            letterSpacing = 1.2.sp
        )
    }
}
