package com.neo.chevere.ui.marketplace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R

/** Model-selection guidance above the catalog, controlled by marketplace MVI state. */
@Composable
internal fun ModelGuidanceBanner(
    state: ModelGuidanceState,
    onIntent: (MarketplaceIntent) -> Unit,
) {
    when (state) {
        ModelGuidanceState.Dismissed -> Unit
        ModelGuidanceState.Compact, ModelGuidanceState.Expanded -> {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.model_guidance_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = { onIntent(MarketplaceIntent.DismissModelGuidance) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.model_guidance_dismiss))
                        }
                    }
                    Text(
                        stringResource(R.string.model_guidance_summary),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = { onIntent(MarketplaceIntent.ExpandModelGuidance) }) {
                        Text(stringResource(R.string.model_guidance_learn_more))
                    }
                }
            }
            if (state == ModelGuidanceState.Expanded) {
                AlertDialog(
                    onDismissRequest = { onIntent(MarketplaceIntent.CollapseModelGuidance) },
                    title = { Text(stringResource(R.string.model_guidance_title)) },
                    text = {
                        Text(
                            stringResource(R.string.model_guidance_details),
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { onIntent(MarketplaceIntent.CollapseModelGuidance) }) {
                            Text(stringResource(R.string.model_guidance_done))
                        }
                    },
                )
            }
        }
    }
}
