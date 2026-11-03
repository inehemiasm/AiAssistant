package com.neo.chevere.domain

/** Runtime-reported counts, including the prompt actually submitted to the engine. */
data class InferenceUsage(val inputTokens: Int, val outputTokens: Int)

/** Distinguishes persisted historical estimates from runtime measurements. */
enum class TokenCountKind { ESTIMATED, MEASURED }

/** Retrieved text is evidence made available to the model, not proof it used it correctly. */
data class DocumentExcerpt(val fileName: String, val chunkIndex: Int, val text: String)

/** Explicit retrieval outcome; no-match responses must not imply a document-backed answer. */
sealed interface DocumentEvidence {
    data object NoMatches : DocumentEvidence
    data class Matches(val excerpts: List<DocumentExcerpt>) : DocumentEvidence
}

/** A finished response is distinct from independently verified task completion. */
enum class AgentOutcome { ANSWERED, INCOMPLETE, CANCELLED, FAILED }
