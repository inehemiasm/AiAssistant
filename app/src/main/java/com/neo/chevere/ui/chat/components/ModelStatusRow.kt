package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.chat.*

/** Renders a single explicit model state and routes recovery through MVI callbacks. */
@Composable
fun ModelStatusRow(status: ChatModelStatus, onModels: () -> Unit, onRecover: (ModelRecovery) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        when (status) {
            ChatModelStatus.NeedsModel -> TextButton(onClick = onModels) { Text(stringResource(R.string.model_status_choose)) }
            is ChatModelStatus.Downloading -> {
                Text(stringResource(R.string.model_status_downloading, status.name, status.percent), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is ChatModelStatus.Loading -> Text(stringResource(R.string.model_status_loading, status.detail), style = MaterialTheme.typography.bodySmall)
            is ChatModelStatus.Ready -> Text(stringResource(R.string.model_status_ready, status.name), style = MaterialTheme.typography.bodySmall)
            ChatModelStatus.Generating -> Text(stringResource(R.string.model_status_generating), style = MaterialTheme.typography.bodySmall)
            ChatModelStatus.AwaitingConfirmation -> Text(stringResource(R.string.ui_waiting_for_confirmation), style = MaterialTheme.typography.bodySmall)
            is ChatModelStatus.Failed -> {
                Text(status.detail, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onRecover(status.recovery) }) { Text(stringResource(R.string.model_status_retry)) }
            }
        }
    }
}
