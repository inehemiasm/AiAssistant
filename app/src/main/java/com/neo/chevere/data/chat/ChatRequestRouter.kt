package com.neo.chevere.data.chat

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the routing categories for prompt classification.
 */
enum class RoutingCategory {
    IMAGE_GENERATION,
    LIVE_INFORMATION,
    DEVICE_ACTION,
    MODEL_MANAGEMENT,
    TASK_REGISTRY,
    SENSORS,
    DIRECT_CHAT
}

/**
 * Classifies chat prompts before they reach the repository orchestration layer.
 *
 * This keeps capability copy and lightweight routing heuristics out of
 * [com.neo.chevere.data.ChatRepositoryImpl], where they otherwise obscure the
 * actual data flow between context, inference, tools, and model management.
 */
@Singleton
class ChatRequestRouter @Inject constructor() {

    fun buildDirectChatPrompt(contextualPrompt: String): String =
        "$DIRECT_CHAT_CAPABILITY_CONTEXT\n\n$contextualPrompt"

    fun buildVisionChatPrompt(contextualPrompt: String): String =
        "$VISION_CHAT_CAPABILITY_CONTEXT\n\n$contextualPrompt"

    fun classifyRequest(prompt: String): RoutingCategory {
        val normalized = prompt.normalized()
        if (normalized.isBlank() || isCapabilityOnlyQuestion(normalized)) {
            return RoutingCategory.DIRECT_CHAT
        }

        return when {
            looksLikeImageGenerationRequest(normalized) -> RoutingCategory.IMAGE_GENERATION
            looksLikeSensorsRequest(normalized) -> RoutingCategory.SENSORS
            looksLikeLiveInformationRequest(normalized) -> RoutingCategory.LIVE_INFORMATION
            looksLikeDeviceActionRequest(normalized) -> RoutingCategory.DEVICE_ACTION
            looksLikeModelManagementRequest(normalized) -> RoutingCategory.MODEL_MANAGEMENT
            looksLikeTaskRegistryRequest(normalized) -> RoutingCategory.TASK_REGISTRY
            else -> RoutingCategory.DIRECT_CHAT
        }
    }

    fun shouldUseAgent(prompt: String): Boolean {
        return classifyRequest(prompt) != RoutingCategory.DIRECT_CHAT
    }

    fun capabilityResponseFor(prompt: String): String? {
        val normalized = prompt.normalized()
        if (normalized.isBlank()) return null

        return when {
            isCapabilityOverviewQuestion(normalized) -> CAPABILITY_OVERVIEW_RESPONSE
            isImageCapabilityQuestion(normalized) -> IMAGE_CAPABILITY_RESPONSE
            else -> null
        }
    }

    private fun looksLikeSensorsRequest(text: String): Boolean {
        val hasSensorKeyword = ChatRoutingLexicon.sensorKeywords.any { it in text }
        val hasAmbientPhrase = ChatRoutingLexicon.ambientPhrases.any { it in text }
        return hasSensorKeyword || hasAmbientPhrase
    }

    private fun looksLikeTaskRegistryRequest(text: String): Boolean {
        val hasTaskKeyword = ChatRoutingLexicon.taskKeywords.any { text.contains(it) }
        val hasActionVerb = ChatRoutingLexicon.taskActionVerbs.any { text.hasWordBoundaryMatch(it) }
        val isReminder = text.startsWith("remind me to ") || text.startsWith("remember to ")

        return (hasTaskKeyword && hasActionVerb) || isReminder ||
                text.contains("todo") || text.contains("to-do") || text.contains("checklist")
    }

    private fun isCapabilityOnlyQuestion(text: String): Boolean =
        isCapabilityOverviewQuestion(text) ||
                isImageCapabilityQuestion(text)

    private fun isCapabilityOverviewQuestion(text: String): Boolean =
        text == "what can you do" ||
                text == "what can you do?" ||
                text == "what are your capabilities" ||
                text == "what are your capabilities?" ||
                text == "what can chevere do" ||
                text == "what can chevere do?"

    private fun isImageCapabilityQuestion(text: String): Boolean {
        if (!ChatRoutingLexicon.capabilityQuestionPrefixes.any { text.startsWith(it) }) return false
        if (!ChatRoutingLexicon.imageRequestVerbs.any { it in text }) return false
        if (!ChatRoutingLexicon.imageRequestNouns.any { it in text }) return false
        return !hasConcreteImageDescription(text)
    }

    private fun hasConcreteImageDescription(text: String): Boolean =
        ChatRoutingLexicon.concreteImageDescriptionMarkers.any { it in text }

    private fun looksLikeImageGenerationRequest(text: String): Boolean {
        val words = text.split(Regex("[\\s,\\.\\?!]+"))
        val hasSpecificImageVerb = words.any {
            it in setOf("draw", "paint", "imagine", "depict", "sketch")
        }
        if (hasSpecificImageVerb) return true

        val hasImageNoun = ChatRoutingLexicon.imageRequestNouns.any { it in text }
        val hasCreateVerb = ChatRoutingLexicon.imageRequestVerbs.any { it in text }
        if (hasImageNoun && hasCreateVerb) return true

        // Also match visual prompts like "create a wolf..." or "generate a sunset..."
        val isVisualCreation = listOf("create a", "generate a", "make a", "render a").any { it in text } &&
                listOf(
                    "event",
                    "calendar",
                    "email",
                    "code",
                    "file",
                    "text",
                    "app",
                    "playlist",
                    "reminder",
                    "alarm",
                    "timer",
                    "note"
                ).none { it in text }

        return isVisualCreation
    }

    private fun looksLikeLiveInformationRequest(text: String): Boolean {
        val asksForWeather = ChatRoutingLexicon.liveInformationWeatherTerms.any { it in text }
        val asksForFreshInfo = ChatRoutingLexicon.freshInformationPhrases.any { it in text }
        return asksForWeather || asksForFreshInfo
    }

    private fun looksLikeDeviceActionRequest(text: String): Boolean {
        return ChatRoutingLexicon.deviceActionVerbs.any { text.hasWordBoundaryMatch(it) } &&
                ChatRoutingLexicon.deviceActionTargets.any { it in text }
    }

    private fun looksLikeModelManagementRequest(text: String): Boolean {
        val mentionsModels = ChatRoutingLexicon.modelManagementPhrases.any { it in text }
        val asksForModelAction =
            ChatRoutingLexicon.modelManagementActionVerbs.any { text.hasWordBoundaryMatch(it) }
        return mentionsModels && asksForModelAction
    }

    private fun String.normalized(): String = lowercase(Locale.ROOT).trim()

    private fun String.hasWordBoundaryMatch(word: String): Boolean =
        startsWith("$word ") || " $word " in this

    private companion object {
        const val DIRECT_CHAT_CAPABILITY_CONTEXT =
            "You are Chevere AI running inside an Android app. You can answer questions, explain and write code, grade answers, summarize, translate, brainstorm, and help with Android/software work. App tools can handle image generation when the user describes the desired image, image analysis when an image is attached and supported, web/current-info search, weather, sharing/copying text, opening URLs/maps/apps, drafting email, creating calendar events, managing tasks/to-do list, and model/runtime management. If the user asks whether you can do something, answer the capability question and ask for missing details; do not perform the action or invent missing content."
        const val VISION_CHAT_CAPABILITY_CONTEXT =
            "You are Chevere AI running inside an Android app. The current user message includes an attached image. Answer by analyzing or describing that attached image and the user's question. Do not generate, create, edit, or replace an image from an attachment. If the user asks for image generation while an image is attached, explain what you can infer from the attached image and ask them to send a text-only image-generation prompt if they want a new image."
        const val CAPABILITY_OVERVIEW_RESPONSE =
            "I can chat, explain code, help with Android/Kotlin work, summarize, translate, brainstorm, grade answers, and reason through plans. In this app I can also use local tools for image generation, image analysis when you attach an image, web/current-info search, weather, sharing or copying text, opening URLs/maps/apps, drafting email, creating calendar events, managing tasks/to-do list, and checking local model/runtime info. Tell me what you want to do and I will either answer directly or use the right tool."
        const val IMAGE_CAPABILITY_RESPONSE =
            "Yes, I can generate images when an image-generation model is installed. Tell me what you want the image to show, plus any style, mood, lighting, or composition details, and I will create it."
    }
}
