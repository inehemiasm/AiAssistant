package com.neo.chevere.domain

/** Repository result containing readable text and optional structured domain content, never JSON DTOs. */
data class AssistantResponse(val text: String, val agentUiContent: AgentUiContent? = null)
