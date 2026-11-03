package com.neo.chevere.data

import com.neo.chevere.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned data-layer envelope in the existing text column; legacy plain text/A2UI remain readable. */
internal object ChatMessageMetadataCodec {
    private const val PREFIX = "CHEVERE_MESSAGE_V1:"
    private val json = Json { ignoreUnknownKeys = true }

    data class Content(val body: String, val evidence: DocumentEvidence? = null,
        val outcome: AgentOutcome? = null, val counts: TokenCountKind = TokenCountKind.ESTIMATED)

    fun encode(content: Content): String {
        if (content.evidence == null && content.outcome == null && content.counts == TokenCountKind.ESTIMATED) return content.body
        val excerpts = (content.evidence as? DocumentEvidence.Matches)?.excerpts.orEmpty()
        val dto = StoredContent(body = content.body, retrieval = when (content.evidence) {
            DocumentEvidence.NoMatches -> "empty"
            is DocumentEvidence.Matches -> "matches"
            null -> null
        }, excerpts = excerpts.map { StoredExcerpt(it.fileName, it.chunkIndex, it.text) },
            outcome = content.outcome?.name, measured = content.counts == TokenCountKind.MEASURED)
        return PREFIX + json.encodeToString(dto)
    }

    /** Malformed envelopes never expose protocol JSON as chat content. */
    fun decodeOrPlain(raw: String): Content {
        if (!raw.startsWith(PREFIX)) return Content(raw)
        if (raw.length > 131_072) return Content("")
        return runCatching {
            val dto = json.decodeFromString<StoredContent>(raw.removePrefix(PREFIX))
            require(dto.version == 1 && dto.body.length <= 65_536)
            require(dto.excerpts.size <= 3 && dto.excerpts.all {
                it.name.isNotBlank() && it.name.length <= 256 && '\u0000' !in it.name &&
                    it.chunk >= 0 && it.text.isNotBlank() && it.text.length <= 800
            })
            val evidence = when (dto.retrieval) {
                null -> { require(dto.excerpts.isEmpty()); null }
                "empty" -> { require(dto.excerpts.isEmpty()); DocumentEvidence.NoMatches }
                "matches" -> { require(dto.excerpts.isNotEmpty()); DocumentEvidence.Matches(dto.excerpts.map {
                    DocumentExcerpt(it.name, it.chunk, it.text)
                }) }
                else -> error("Unsupported retrieval state")
            }
            Content(dto.body, evidence, dto.outcome?.let(AgentOutcome::valueOf),
                if (dto.measured) TokenCountKind.MEASURED else TokenCountKind.ESTIMATED)
        }.getOrElse { Content("") }
    }
}

@Serializable
private data class StoredContent(val version: Int = 1, val body: String, val retrieval: String? = null,
    val excerpts: List<StoredExcerpt> = emptyList(), val outcome: String? = null, val measured: Boolean = false)

@Serializable
private data class StoredExcerpt(val name: String, val chunk: Int, val text: String)
