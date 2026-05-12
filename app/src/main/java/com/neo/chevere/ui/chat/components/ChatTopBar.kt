package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * A top app bar for the chat screen.
 *
 * Displays the Chevere brand and the available local AI capabilities, with
 * actions for clearing chat and navigating to model/settings screens.
 *
 * @param isInteractionEnabled Whether user interaction with the bar's elements is allowed.
 * @param isChatReady Whether a chat or vision chat model is available.
 * @param isImageReady Whether an image generation model is available.
 * @param onClearChat Callback triggered when the "clear chat" button is clicked.
 * @param onModelsClick Callback triggered when the navigation icon (menu) is clicked.
 * @param onSettingsClick Callback triggered when the profile icon is clicked.
 * @param modifier The modifier to be applied to the top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onModelsClick) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu_library), tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CHEVERE",
                    style = Typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CapabilityChip(label = "CHAT", ready = isChatReady)
                    CapabilityChip(label = "IMAGE", ready = isImageReady)
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
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(contentColor, CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
            letterSpacing = 1.sp
        )
    }
}
