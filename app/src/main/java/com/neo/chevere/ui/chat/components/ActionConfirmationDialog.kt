package com.neo.chevere.ui.chat.components

import androidx.compose.ui.platform.LocalResources
import com.neo.chevere.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ActionConfirmationDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val textResources = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(textResources.getString(R.string.ui_confirm_action)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(textResources.getString(R.string.ui_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(textResources.getString(R.string.ui_cancel))
            }
        }
    )
}
