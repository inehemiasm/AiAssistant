package com.neo.chevere.ui.chat

import com.neo.chevere.domain.*

/** Presentation models for evidence and runtime status; composables do not decode stored payloads. */
data class ExcerptUi(val name: String, val number: Int, val text: String, val expanded: Boolean)
sealed interface EvidenceUi {
    data object None : EvidenceUi
    data object NoMatches : EvidenceUi
    data class Sources(val excerpts: List<ExcerptUi>) : EvidenceUi
}
enum class OutcomeUi { ANSWERED, INCOMPLETE, CANCELLED, FAILED }
data class ResponseDetailsUi(val evidence: EvidenceUi = EvidenceUi.None, val outcome: OutcomeUi? = null)

internal fun ChatMessage.toDetailsUi(expanded: Set<Int>) = ResponseDetailsUi(
    evidence = when (val evidence = documentEvidence) {
        null -> EvidenceUi.None
        DocumentEvidence.NoMatches -> EvidenceUi.NoMatches
        is DocumentEvidence.Matches -> EvidenceUi.Sources(evidence.excerpts.mapIndexed { index, source ->
            ExcerptUi(source.fileName, source.chunkIndex + 1, source.text, index in expanded)
        })
    },
    outcome = agentOutcome?.let { OutcomeUi.valueOf(it.name) },
)

sealed interface DownloadStatusUi {
    data object None : DownloadStatusUi
    data class Active(val name: String, val percent: Int) : DownloadStatusUi
    data class Failed(val name: String, val reason: String) : DownloadStatusUi
}

sealed interface ChatModelStatus {
    data object NeedsModel : ChatModelStatus
    data class Downloading(val name: String, val percent: Int) : ChatModelStatus
    data class Loading(val detail: String) : ChatModelStatus
    data class Ready(val name: String) : ChatModelStatus
    data object Generating : ChatModelStatus
    data object AwaitingConfirmation : ChatModelStatus
    data class Failed(val detail: String, val recovery: ModelRecovery) : ChatModelStatus
}
enum class ModelRecovery { RELOAD, RETRY_RESPONSE, OPEN_MODELS }
