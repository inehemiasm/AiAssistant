package com.neo.chevere.data.context

import android.net.Uri
import com.neo.chevere.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains a compact, deterministic conversation memory for small on-device
 * models. Instead of relying on the runtime to keep an unbounded conversation,
 * each turn is rebuilt from a short memory slice, recent verbatim turns, and
 * the current request.
 */
@Singleton
class ConversationContextManager @Inject constructor() {
    private val turns = mutableListOf<ConversationTurn>()

    /**
     * Builds a prompt that preserves useful history while keeping the runtime
     * context small enough for local models.
     */
    fun buildPrompt(currentPrompt: String, currentImageUri: Uri? = null): String {
        if (turns.isEmpty()) return currentPrompt

        val context = buildContextPrefix()
        if (context.isBlank()) return currentPrompt

        return listOf(
            context,
            buildCurrentRequest(currentPrompt, currentImageUri)
        ).joinToString(separator = "\n\n")
    }

    /**
     * Builds only the retained context, without appending a current request.
     */
    fun buildContextPrefix(): String {
        if (turns.isEmpty()) return ""

        val recentTurns = turns.takeLast(Constants.ContextWindow.RECENT_TURN_COUNT)
        val memoryTurns = turns.dropLast(recentTurns.size)

        val sections = mutableListOf(Constants.ContextWindow.CONTEXT_INSTRUCTION)
        buildMemorySection(memoryTurns)?.let(sections::add)
        buildRecentSection(recentTurns)?.let(sections::add)

        return sections.joinToString(separator = "\n\n")
    }

    /**
     * Records a completed user/assistant exchange after a successful response.
     */
    fun recordExchange(userPrompt: String, imageUri: Uri?, assistantResponse: String) {
        turns += ConversationTurn(
            role = Constants.ContextWindow.USER_ROLE,
            content = userPrompt,
            hasImage = imageUri != null
        )
        turns += ConversationTurn(
            role = Constants.ContextWindow.ASSISTANT_ROLE,
            content = assistantResponse,
            hasImage = false
        )
    }

    /**
     * Clears all retained conversation memory.
     */
    fun clear() {
        turns.clear()
    }

    private fun buildMemorySection(memoryTurns: List<ConversationTurn>): String? {
        if (memoryTurns.isEmpty()) return null

        val bullets = memoryTurns
            .map { turn -> "- ${turn.role}: ${turn.compactContent()}" }
            .joinToString("\n")
            .takeLast(Constants.ContextWindow.MEMORY_CHAR_BUDGET)
            .trim()

        return bullets.takeIf { it.isNotEmpty() }?.let {
            "${Constants.ContextWindow.MEMORY_HEADER}:\n$it"
        }
    }

    private fun buildRecentSection(recentTurns: List<ConversationTurn>): String? {
        if (recentTurns.isEmpty()) return null

        val transcript = recentTurns
            .joinToString("\n") { turn -> "${turn.role}: ${turn.compactContent()}" }
            .takeLast(Constants.ContextWindow.RECENT_CHAR_BUDGET)
            .trim()

        return transcript.takeIf { it.isNotEmpty() }?.let {
            "${Constants.ContextWindow.RECENT_HEADER}:\n$it"
        }
    }

    fun buildCurrentRequest(prompt: String, imageUri: Uri?): String {
        val imageNote = if (imageUri != null) " [current message includes an image]" else ""
        return "${Constants.ContextWindow.CURRENT_REQUEST_HEADER}$imageNote:\n" +
            "${Constants.ContextWindow.CURRENT_REQUEST_INSTRUCTION}\n$prompt"
    }

    private fun ConversationTurn.compactContent(): String {
        val normalized = content
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "(empty)" }
        val imageSuffix = if (hasImage) " [image attached]" else ""
        val limit = if (role == Constants.ContextWindow.ASSISTANT_ROLE) {
            Constants.ContextWindow.ASSISTANT_TURN_CHAR_LIMIT
        } else {
            Constants.ContextWindow.TURN_CHAR_LIMIT
        }
        return normalized.take(limit) + imageSuffix
    }
}

private data class ConversationTurn(
    val role: String,
    val content: String,
    val hasImage: Boolean
)
