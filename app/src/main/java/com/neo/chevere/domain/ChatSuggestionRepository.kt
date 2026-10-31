package com.neo.chevere.domain

/** Predicts optional user follow-ups from a bounded conversation snapshot, without executing tools. */
interface ChatSuggestionRepository {
    suspend fun predict(messages: List<ChatMessage>, hasImage: Boolean): List<String>
}
