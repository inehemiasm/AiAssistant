package com.neo.chevere.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * A custom snackbar for displaying error messages.
 *
 * Features an error icon, the error message, and a dismiss button.
 * Uses the theme's error container colors for high visibility.
 *
 * @param message The error message to display.
 * @param onDismiss Callback triggered when the dismiss button is clicked.
 */
@Composable
fun ErrorSnackbar(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss), color = MaterialTheme.colorScheme.inversePrimary)
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = Typography.bodySmall)
        }
    }
}
