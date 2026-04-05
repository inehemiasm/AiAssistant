package com.neo.aiassistant.domain

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val inferenceTimeMs: Long? = null
)
