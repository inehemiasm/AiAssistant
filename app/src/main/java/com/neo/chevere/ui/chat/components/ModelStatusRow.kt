package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.chat.ChatModelStatus
import com.neo.chevere.ui.chat.ModelRecovery

/**
 * Renders an actionable banner below the top bar only when a model is actively
 * downloading (progress bar) or failed (error details + retry button).
 * Normal steady states (Ready, Generating, Loading, NeedsModel) are displayed
 * directly inside the [ChatTopBar] pill.
 */
@Composable
fun ModelStatusRow(
    status: ChatModelStatus,
    onModels: () -> Unit,
    onRecover: (ModelRecovery) -> Unit
) {
    when (status) {
        is ChatModelStatus.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.model_status_downloading, status.name, status.percent),
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { status.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is ChatModelStatus.Failed -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = status.detail,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onRecover(status.recovery) }) {
                    Text(stringResource(R.string.model_status_retry))
                }
            }
        }
        ChatModelStatus.NeedsModel,
        is ChatModelStatus.Loading,
        is ChatModelStatus.Ready,
        ChatModelStatus.Generating,
        ChatModelStatus.AwaitingConfirmation -> Unit
    }
}
