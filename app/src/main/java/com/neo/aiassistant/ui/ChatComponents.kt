package com.neo.aiassistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neo.aiassistant.ui.designsystem.Typography

/**
 * Base components for the UI.
 * Most specific components have been moved to their respective feature packages:
 * - com.neo.aiassistant.ui.chat.components
 * - com.neo.aiassistant.ui.models.components
 * - com.neo.aiassistant.ui.common
 */

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 16.dp)
    )
}
