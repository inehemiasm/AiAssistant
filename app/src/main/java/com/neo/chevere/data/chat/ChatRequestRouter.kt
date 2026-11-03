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
    DOCUMENTS,
    MCP,
    DIRECT_CHAT
}

/** Identifies app-authored capability answers independently of their display language. */
internal enum class CapabilityAnswer { OVERVIEW, IMAGE, SENSORS }

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
        "${com.neo.chevere.domain.AiSafetyPolicy.INSTRUCTION}\n$DIRECT_CHAT_CAPABILITY_CONTEXT\n${com.neo.chevere.core.NumericResponsePolicy.INSTRUCTION}\n\n$contextualPrompt"

    fun buildVisionChatPrompt(contextualPrompt: String): String =
        "${com.neo.chevere.domain.AiSafetyPolicy.INSTRUCTION}\n$VISION_CHAT_CAPABILITY_CONTEXT\n${com.neo.chevere.core.NumericResponsePolicy.INSTRUCTION}\n\n$contextualPrompt"

    fun classifyRequest(prompt: String): RoutingCategory {
        val normalized = prompt.normalized()
        if (normalized.isBlank() || isCapabilityOnlyQuestion(normalized)) {
            return RoutingCategory.DIRECT_CHAT
        }

        return when {
            normalized.startsWith("/mcp") || Regex("""\bmcp\b""").containsMatchIn(normalized) -> RoutingCategory.MCP
            normalized.startsWith("/docs") || listOf("my documents", "my files", "indexed documents", "document search", "mis documentos", "document_rag").any { it in normalized } -> RoutingCategory.DOCUMENTS
            looksLikeImageGenerationRequest(normalized) -> RoutingCategory.IMAGE_GENERATION
            looksLikeSensorsRequest(normalized) -> RoutingCategory.SENSORS
            looksLikeLiveInformationRequest(normalized) -> RoutingCategory.LIVE_INFORMATION
            looksLikeDeviceActionRequest(normalized) -> RoutingCategory.DEVICE_ACTION
            looksLikeModelManagementRequest(normalized) -> RoutingCategory.MODEL_MANAGEMENT
            looksLikeTaskRegistryRequest(normalized) -> RoutingCategory.TASK_REGISTRY
            else -> RoutingCategory.DIRECT_CHAT
        }
    }

    private fun looksLikeInvoiceRequest(text: String): Boolean {
        return text.contains("invoice") || text.contains("receipt") || text.contains("bill") ||
                text.contains("invoices") || text.contains("receipts") || text.contains("bills")
    }

    fun shouldUseAgent(prompt: String): Boolean {
        return classifyRequest(prompt) != RoutingCategory.DIRECT_CHAT
    }

    fun capabilityResponseFor(prompt: String): String? {
        return when (capabilityAnswerFor(prompt)) {
            CapabilityAnswer.OVERVIEW -> CAPABILITY_OVERVIEW_RESPONSE
            CapabilityAnswer.IMAGE -> IMAGE_CAPABILITY_RESPONSE
            CapabilityAnswer.SENSORS -> SENSORS_CAPABILITY_RESPONSE
            null -> null
        }
    }

    internal fun capabilityAnswerFor(prompt: String): CapabilityAnswer? {
        val normalized = prompt.normalized()
        if (normalized.isBlank()) return null

        return when {
            isCapabilityOverviewQuestion(normalized) -> CapabilityAnswer.OVERVIEW
            isImageCapabilityQuestion(normalized) -> CapabilityAnswer.IMAGE
            isSensorsCapabilityQuestion(normalized) -> CapabilityAnswer.SENSORS
            else -> null
        }
    }

    /** Selects factual room-light requests, while leaving image questions, advice, and UI actions alone. */
    fun isAmbientLightReadingRequest(prompt: String): Boolean {
        val text = prompt.normalized().replace("’", "'").replace("how's", "how is").replace("hows ", "how is ")
        if (isCapabilityOnlyQuestion(text)) return false
        if (Regex("""\b(photo|image|picture|photograph|screen|display|flashlight)\b""").containsMatchIn(text)) return false
        if (Regex("""\b(open|show|launch|navigate|improve|install|buy|recommend|explain)\b""").containsMatchIn(text)) return false
        if (Regex("""^(what is|what's|define) ambient light\??$""").matches(text)) return false
        return Regex("""\b(how is the light|ambient light|light levels?|room lighting)\b""").containsMatchIn(text) ||
            (Regex("""\b(how (bright|dark)|is it (bright|dark))\b""").containsMatchIn(text) &&
                Regex("""\b(room|here|environment|surroundings|it)\b""").containsMatchIn(text)) ||
            (Regex("""\b(light|lighting)\b""").containsMatchIn(text) &&
                Regex("""\b(room|here|environment|surroundings)\b""").containsMatchIn(text) &&
                Regex("""\b(check|measure|read|how|what)\b""").containsMatchIn(text))
    }

    /**
     * Selects factual ambient sound / background noise / microphone decibel requests,
     * while leaving visual screen navigation, device volume adjustments, and definitions alone.
     */
    fun isAmbientSoundReadingRequest(prompt: String): Boolean {
        val text = prompt.normalized()
            .replace("’", "'")
            .replace("how's", "how is")
            .replace("hows ", "how is ")
            .replace("what's", "what is")
            .replace("whats ", "what is ")
        if (isCapabilityOnlyQuestion(text)) return false
        if (Regex("""\b(open|show|launch|navigate)\b.*\b(meter|visualizer|radar|screen|dashboard)\b""").containsMatchIn(text)) return false
        if (Regex("""\b(set|turn|change|increase|decrease|raise|lower|mute|unmute)\b.*\b(volume|sound|audio)\b""").containsMatchIn(text)) return false
        if (Regex("""^(what is|define) (ambient (sound|noise)|background (sound|noise)|decibels?)\??$""").matches(text)) return false
        // Exclude compound multi-sensor requests (e.g. "check temperature and noise") so the full sensor tool handles them
        if (Regex("""\b(temperature|pressure|barometer|battery|thermal|gyro|compass|proximity|light|lux|stud|metal)\b""").containsMatchIn(text)) return false

        val englishMatch =
            Regex("""\b(ambient (sound|noise)|background (sound|noise|audio)|noise levels?|sound levels?|decibels?|db spl|room noise)\b""").containsMatchIn(text) ||
            Regex("""\b(how (loud|noisy|quiet|silent)|is it (loud|noisy|quiet|silent))\b""").containsMatchIn(text) ||
            Regex("""\b(how is the (sound|noise|audio))\b""").containsMatchIn(text) ||
            (Regex("""\b(listen|listening|hear|check|measure|read|detect|sample)\b""").containsMatchIn(text) &&
                Regex("""\b(sound|noise|audio|mic|microphone|decibels?)\b""").containsMatchIn(text))

        val multilingualMatch = listOf(
            "cuánto ruido", "cuanto ruido", "qué tan ruidoso", "que tan ruidoso", "nivel de ruido",
            "ruido ambiental", "cómo está el sonido", "como esta el sonido", "está silencioso", "decibeles",
            "niveau sonore", "niveau de bruit", "bruit ambiant", "c'est bruyant", "quel bruit",
            "nível de ruído", "nivel de ruido", "quão barulhento", "quao barulhento", "quanto barulho",
            "como está o som", "como esta o som",
            "wie laut ist es", "wie leise ist es", "lärmpegel", "larmpegel", "geräuschpegel", "gerauschpegel",
            "音量はどれくらい", "うるさいですか", "騒音レベル"
        ).any { it in text }

        return englishMatch || multilingualMatch
    }

    private fun looksLikeSensorsRequest(text: String): Boolean {
        val hasSensorKeyword = ChatRoutingLexicon.sensorKeywords.any { it in text }
        val hasAmbientPhrase = ChatRoutingLexicon.ambientPhrases.any { it in text }
        return isAmbientLightReadingRequest(text) || isAmbientSoundReadingRequest(text) || hasSensorKeyword || hasAmbientPhrase
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
                isImageCapabilityQuestion(text) ||
                isSensorsCapabilityQuestion(text)

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
        // If the prompt mentions invoices/receipts/bills, it is NOT an image-generation question.
        if (looksLikeInvoiceRequest(text)) return false
        return !hasConcreteImageDescription(text)
    }

    private fun isSensorsCapabilityQuestion(text: String): Boolean {
        if (!ChatRoutingLexicon.capabilityQuestionPrefixes.any { text.startsWith(it) }) return false
        val asksAboutSensorCapability =
            ChatRoutingLexicon.sensorCapabilityTerms.any { it in text } ||
                    ChatRoutingLexicon.sensorScreenTerms.any { it in text }
        val asksForLiveReading = ChatRoutingLexicon.sensorReadingActionTerms.any { it in text }
        return asksAboutSensorCapability && !asksForLiveReading
    }

    private fun hasConcreteImageDescription(text: String): Boolean =
        ChatRoutingLexicon.concreteImageDescriptionMarkers.any { it in text }

    private fun looksLikeImageGenerationRequest(text: String): Boolean {
        val words = text.split(Regex("[\\s,\\.\\?!]+"))
        val hasSpecificImageVerb = words.any {
            it in setOf("draw", "paint", "imagine", "depict", "sketch")
        }
        if (hasSpecificImageVerb) return true

        // An invoice based on an image is a document request, not image generation.
        if (looksLikeInvoiceRequest(text)) return false

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
                    "note",
                    "invoice",
                    "receipt",
                    "bill"
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
            ChatCapabilities.MODEL_CONTEXT
        const val VISION_CHAT_CAPABILITY_CONTEXT =
            "You are Chevere AI, a general-purpose assistant. The current user message includes an attached image. Answer by analyzing or describing that attached image and the user's question. Do not generate, create, edit, or replace an image from an attachment. If the user asks for image generation while an image is attached, explain that image generation is not available in this build; you can analyze the attached image with a compatible vision model."
        const val CAPABILITY_OVERVIEW_RESPONSE =
            ChatCapabilities.OVERVIEW
        const val IMAGE_CAPABILITY_RESPONSE =
            ChatCapabilities.IMAGE_UNAVAILABLE
        const val SENSORS_CAPABILITY_RESPONSE =
            "Yes. I can read live device sensors like ambient light, pressure, battery and thermal status, accelerometer motion, gyroscope rotation, compass heading, proximity, posture, flatness, metal or magnetic field strength, and microphone-based ambient noise when microphone permission is granted. I can also open dedicated sensor screens: /sensors for the full dashboard, /stud for stud finder or metal detector, /level for spirit level, /light for light meter, and /proximity for proximity detection."
    }
}
