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

    private fun looksLikeSensorsRequest(text: String): Boolean {
        val sensorKeywords = listOf(
            // English
            "sensor", "sensors", "barometer", "pressure", "lux", "brightness",
            "battery", "thermal", "thermals", "cpu temp", "device temp", "internal temp",
            "room temp", "ambient temp", "temperature",
            // Spanish
            "sensores", "presión", "presion", "brillo", "batería", "bateria", "térmico", "termico", "temperatura",
            // Portuguese
            "sensores", "pressão", "pressao", "brilho", "bateria", "térmico", "termico", "temperatura",
            // French
            "capteur", "capteurs", "pression", "luminosité", "luminosite", "batterie", "thermique", "température", "temperature",
            // German
            "sensoren", "druck", "helligkeit", "akku", "thermisch", "temperatur"
        )
        val hasSensorKeyword = sensorKeywords.any { it in text }

        val ambientPhrases = listOf(
            // English
            "how hot", "how cold", "how warm", "how bright", "is it hot", "is it cold",
            "room temperature", "ambient temperature", "device temperature", "internal temperature",
            "room condition", "room conditions",
            // Spanish
            "qué calor", "que calor", "qué frío", "que frio", "temperatura ambiente",
            "temperatura del cuarto", "temperatura de la habitación", "temperatura de la habitacion",
            "temperatura del dispositivo", "temperatura interna",
            // Portuguese
            "que calor", "que frio", "temperatura ambiente",
            "temperatura do quarto", "temperatura do dispositivo", "temperatura interna",
            // French
            "fait chaud", "fait froid", "fait-il chaud", "fait-il froid", "fait il chaud", "fait il froid",
            "température ambiante", "temperature ambiante",
            "température de la pièce", "temperature de la piece", "température de la chambre", "temperature de la chambre",
            "température de l'appareil", "temperature de l'appareil",
            // German
            "wie warm", "wie kalt", "wie heiss", "wie heiß", "raumtemperatur",
            "umgebungstemperatur", "gerätetemperatur", "geratetemperatur", "innentemperatur"
        )
        val hasAmbientPhrase = ambientPhrases.any { it in text }

        return hasSensorKeyword || hasAmbientPhrase
    }

    private fun looksLikeTaskRegistryRequest(text: String): Boolean {
        val taskKeywords = listOf("task", "tasks", "todo", "todos", "to-do", "to-dos", "checklist")
        val actionVerbs = listOf(
            "create", "add", "new", "list", "show", "get", "view",
            "update", "change", "complete", "finish", "done",
            "delete", "remove", "clear", "mark"
        )
        val hasTaskKeyword = taskKeywords.any { text.contains(it) }
        val hasActionVerb = actionVerbs.any { text.startsWith("$it ") || " $it " in text }

        val isReminder = text.startsWith("remind me to ") || text.startsWith("remember to ")

        return (hasTaskKeyword && hasActionVerb) || isReminder ||
                (text.contains("todo") || text.contains("to-do") || text.contains("checklist"))
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

    private fun String.normalized(): String = lowercase(Locale.ROOT).trim()

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
        if (!capabilityQuestionPrefixes.any { text.startsWith(it) }) return false
        if (!imageRequestVerbs.any { it in text }) return false
        if (!imageRequestNouns.any { it in text }) return false
        return !hasConcreteImageDescription(text)
    }

    private fun hasConcreteImageDescription(text: String): Boolean =
        listOf(
            " of ",
            " showing ",
            " with ",
            " in ",
            " at ",
            " wearing ",
            " holding "
        ).any { it in text }

    private fun looksLikeImageGenerationRequest(text: String): Boolean {
        val words = text.split(Regex("[\\s,\\.\\?!]+"))
        val hasSpecificImageVerb = words.any { it in listOf("draw", "paint", "imagine", "depict", "sketch") }
        if (hasSpecificImageVerb) return true

        val hasImageNoun = imageRequestNouns.any { it in text }
        val hasCreateVerb = imageRequestVerbs.any { it in text }
        if (hasImageNoun && hasCreateVerb) return true

        // Also match visual prompts like "create a wolf..." or "generate a sunset..."
        val isVisualCreation = (text.contains("create a") || text.contains("generate a") || text.contains("make a") || text.contains("render a")) &&
                !text.contains("event") && !text.contains("calendar") && !text.contains("email") &&
                !text.contains("code") && !text.contains("file") && !text.contains("text") && !text.contains("app") &&
                !text.contains("playlist") && !text.contains("reminder") && !text.contains("alarm") &&
                !text.contains("timer") && !text.contains("note")

        return isVisualCreation
    }

    private fun looksLikeLiveInformationRequest(text: String): Boolean {
        val asksForWeather = listOf("weather", "forecast", "temperature").any { it in text }
        val asksForFreshInfo = listOf(
            "search the web",
            "look up",
            "latest",
            "current news",
            "today's news",
            "right now",
            "breaking news",
            "current price",
            "stock price"
        ).any { it in text }
        return asksForWeather || asksForFreshInfo
    }

    private fun looksLikeDeviceActionRequest(text: String): Boolean {
        val actionVerbs = listOf(
            "copy",
            "share",
            "open",
            "launch",
            "draft",
            "email",
            "map",
            "navigate",
            "calendar"
        )
        val actionTargets =
            listOf("clipboard", "share sheet", "browser", "url", "app", "maps", "email", "calendar")
        return actionVerbs.any { text.startsWith("$it ") || " $it " in text } &&
                actionTargets.any { it in text }
    }

    private fun looksLikeModelManagementRequest(text: String): Boolean {
        val mentionsModels = listOf(
            "installed model",
            "installed models",
            "active model",
            "runtime status",
            "switch model",
            "select model"
        ).any { it in text }
        val asksForModelAction =
            listOf("list", "show", "what", "which", "switch", "select", "recommend", "status").any {
                text.startsWith("$it ") || " $it " in text
            }
        return mentionsModels && asksForModelAction
    }

    private companion object {
        val imageRequestVerbs = listOf("create", "generate", "make", "draw", "render", "paint")
        val imageRequestNouns =
            listOf("image", "picture", "photo", "art", "illustration", "portrait")
        val capabilityQuestionPrefixes = listOf(
            "can you",
            "could you",
            "are you able to",
            "do you know how to",
            "do you have the ability to"
        )
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
