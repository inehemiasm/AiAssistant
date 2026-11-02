package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.chat.DocumentFoldersState

/** Shows explicit saved-folder access and snapshot refresh/removal controls. */
@Composable
fun DocumentFoldersDialog(
    state: DocumentFoldersState.Open,
    onAdd: () -> Unit,
    onRefresh: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val idle = state.canInteract
    AlertDialog(
        onDismissRequest = { if (idle) onDismiss() },
        title = { Text(stringResource(R.string.document_folders)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.folder_explanation))
                if (!idle) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.folder_working))
                }
                state.message?.let { Text(it) }
                if (state.folders.isEmpty() && idle) Text(stringResource(R.string.folder_empty))
                state.folders.forEach { folder ->
                    HorizontalDivider()
                    Text(folder.name, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onRefresh(folder.reference) }, enabled = idle) {
                            Text(stringResource(R.string.folder_refresh))
                        }
                        TextButton(onClick = { onDisconnect(folder.reference) }, enabled = idle) {
                            Text(stringResource(R.string.folder_disconnect))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd, enabled = idle) { Text(stringResource(R.string.folder_add)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = idle) { Text(stringResource(R.string.folder_done)) } },
    )
}
