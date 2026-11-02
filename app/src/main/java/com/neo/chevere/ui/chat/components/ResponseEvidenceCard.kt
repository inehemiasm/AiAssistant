package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.chat.*

/** Shows retrieved evidence without claiming it independently validates model output. */
@Composable
fun ResponseEvidenceCard(details: ResponseDetailsUi, onToggleSource: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (details.outcome) {
            OutcomeUi.INCOMPLETE -> Text(stringResource(R.string.agent_incomplete), color = MaterialTheme.colorScheme.error)
            OutcomeUi.CANCELLED -> Text(stringResource(R.string.agent_cancelled))
            OutcomeUi.FAILED -> Text(stringResource(R.string.ui_stopped_with_an_error), color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        when (val evidence = details.evidence) {
            EvidenceUi.None -> Unit
            EvidenceUi.NoMatches -> Text(stringResource(R.string.sources_empty), style = MaterialTheme.typography.labelLarge)
            is EvidenceUi.Sources -> {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.sources_disclaimer), style = MaterialTheme.typography.bodySmall)
                evidence.excerpts.forEachIndexed { index, source ->
                    TextButton(onClick = { onToggleSource(index) }) {
                        Text(stringResource(R.string.source_excerpt_label, source.name, source.number))
                    }
                    if (source.expanded) Text(source.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
