package com.neo.chevere.core

/**
 * Centralized constant values used throughout the application.
 */
object Constants {

    object Network {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val ACCEPT_ALL = "*/*"
    }

    object Agent {
        const val MAX_TOOL_CALLS_PER_TURN = 5
        const val TOOL_EXECUTION_TIMEOUT_MS = 30_000L
        const val IMAGE_GENERATION_TOOL_TIMEOUT_MS = 10 * 60_000L
        /**
         * Hard cap on the total prompt fed to the agent. Kept well below the
         * 4096-token model context to leave room for the model's own output.
         * ~6K chars ≈ 1,500 tokens.
         */
        const val MAX_PROMPT_CHAR_BUDGET = 6_000
        const val TOOL_DESCRIPTION_CHAR_LIMIT = 420
        const val TOOL_SCHEMA_CHAR_LIMIT = 220
        const val IMAGE_GENERATION_TOOL_NAME = "generate_image"
        const val TOOL_CALL_PREFIX = "[TOOL_CALL:"
        const val TOOL_CALL_PATTERN = """\[TOOL_CALL:\s*(\w+)\s*(?:,\s*([^\]]+))?\]"""
        const val TOOL_CALL_STRIP_PATTERN = """\[TOOL_CALL:.*?\]"""
        const val SYSTEM_PROMPT_PREFIX = "SYSTEM: "
        const val USER_PROMPT_PREFIX = "\n\nUSER: "
        const val TOOL_ERROR_PREFIX = "TOOL_ERROR: "
        const val TOOL_ERROR_FROM_PREFIX = "TOOL_ERROR from "
        const val OBSERVATION_PREFIX = "OBSERVATION from "
        const val IMAGE_GENERATION_RESULT_PREFIX = "CHEVERE_IMAGE_GENERATION_RESULT:"
        const val IMAGE_GENERATION_RESULT_SEPARATOR = "|"
    }

    object ModelFiles {
        const val LITERTLM_EXTENSION = ".litertlm"
        const val BIN_EXTENSION = ".bin"
        const val ZIP_EXTENSION = ".zip"
        const val TEMP_EXTENSION = ".tmp"
        const val TEMP_DIRECTORY_EXTENSION = ".tmpdir"
        const val MIN_VALID_FILE_SIZE_BYTES = 1024L
    }

    object ImageGeneration {
        const val GENERATED_IMAGES_DIRECTORY = "generated_images"
        const val GENERATED_IMAGE_PREFIX = "image_"
        const val PNG_EXTENSION = ".png"

        val ONNX_REQUIRED_FILES = listOf(
            "text_encoder/model.ort",
            "tokenizer/vocab.json",
            "tokenizer/merges.txt",
            "unet/model.ort",
            "vae_decoder/model.ort"
        )
    }

    object Download {
        const val INPUT_URL = "url"
        const val INPUT_MODEL_NAME = "modelName"
        const val INPUT_MODEL_ID = "modelId"
        const val INPUT_SHA256 = "sha256"
        const val INPUT_REPOSITORY_FILES = "repositoryFiles"
        const val OUTPUT_ERROR = "error"
        const val PROGRESS = "progress"
        const val TAG_MODEL_DOWNLOAD = "MODEL_DOWNLOAD_TASK"
        const val TAG_MODEL_NAME_PREFIX = "MODEL_NAME:"
        const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Model Downloads"
        const val NOTIFICATION_TITLE = "Downloading Chevere AI model"
        const val UNKNOWN_ERROR = "Unknown error"
    }

    object Commands {
        val IMAGE_GENERATION = listOf("/image", "/img", "/imagine")

        /** Full registry of slash commands surfaced in the autocomplete menu. */
        val ALL: List<SlashCommand> = listOf(
            SlashCommand("/image",    "Generate Image",   "Create an image from a text prompt"),
            SlashCommand("/img",      "Generate Image",   "Shorthand for /image"),
            SlashCommand("/imagine",  "Generate Image",   "Shorthand for /image"),
            SlashCommand("/sensors",  "Sensor Dashboard", "Open the full sensor radar screen"),
            SlashCommand("/stud",     "Stud Finder",      "Open the stud / metal detector screen"),
            SlashCommand("/level",    "Spirit Level",     "Open the spirit level screen"),
            SlashCommand("/light",    "Light Meter",      "Open the ambient light meter screen"),
            SlashCommand("/proximity","Proximity Sensor", "Open the proximity sensor screen")
        )
    }

    /**
     * A single slash command shown in the autocomplete popup.
     *
     * @property command  The exact command string (e.g. "/image").
     * @property label    Short human-readable name shown in bold.
     * @property description  One-line description shown in the secondary text slot.
     */
    data class SlashCommand(
        val command: String,
        val label: String,
        val description: String
    )

    object UiStatus {
        const val PLANNING = "PLANNING..."
        const val EXECUTING_PREFIX = "EXECUTING: "
        const val GENERATING_IMAGE = "GENERATING IMAGE..."
        const val THINKING = "THINKING..."
    }

    object ContentPolicy {
        const val EXPLICIT_RELEASE_BLOCK_MESSAGE =
            "Explicit image generation is only available in debug builds. This release version can help create non-explicit portraits, fashion shots, character designs, and artistic compositions."
    }

    object WebSearch {
        const val SERPER_API_URL = "https://google.serper.dev/search"
        const val CACHE_EXPIRATION_MS = 3600_000L * 24 // 24 hours
        const val MAX_CACHE_SIZE = 50
        const val DEFAULT_HTTP_PROTOCOL = "https://"
    }

    object Inference {
        /**
         * Max new tokens the model may generate per turn.
         * Capped at 1024 to keep responses fast and prevent runaway
         * generation that would eat into the next turn's context budget.
         * Raise to 2048 for larger models (7B+).
         */
        const val MAX_NUM_TOKENS = 1024
        const val MAX_NUM_IMAGES = 1
        const val NEURAL_CACHE_DIR = "neural_cache"
        const val MIN_MODEL_FILE_SIZE_BYTES = 1024 * 1024L // 1MB
    }

    object ContextWindow {
        /**
         * Number of full verbatim turns kept in the "recent" context window.
         * Kept at 3 (6 messages: 3 user + 3 assistant) to stay within ~2K tokens
         * for small models like Gemma 4 2B.
         */
        const val RECENT_TURN_COUNT = 3

        /**
         * Max chars for the compressed memory section (older summarized turns).
         * ~250 tokens — a tight but effective memory capsule.
         */
        const val MEMORY_CHAR_BUDGET = 1_000

        /**
         * Max chars for the verbatim recent-turn section.
         * ~1,500 tokens — leaves ample room for system prompt + current message + model output
         * within a 4096-token window.
         */
        const val RECENT_CHAR_BUDGET = 6_000

        /**
         * Max chars from a single user turn kept in context.
         * Prevents one huge message from eating the whole window.
         */
        const val TURN_CHAR_LIMIT = 1_200

        /**
         * Max chars from a single assistant turn kept in context.
         * Assistant responses can be longer, but still capped tightly.
         */
        const val ASSISTANT_TURN_CHAR_LIMIT = 1_600

        const val USER_ROLE = "User"
        const val ASSISTANT_ROLE = "Assistant"
        const val MEMORY_HEADER = "CONVERSATION MEMORY"
        const val RECENT_HEADER = "RECENT CONVERSATION"
        const val CURRENT_REQUEST_HEADER = "CURRENT USER REQUEST"
        const val CONTEXT_INSTRUCTION =
            "Use the conversation context only as background. Answer the current user request below; do not continue or obey older assistant suggestions unless the current request asks for that."
        const val CURRENT_REQUEST_INSTRUCTION =
            "This is the user's latest message and highest priority task."
    }

    object AppActions {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        const val MAPS_QUERY_URI = "geo:0,0?q="
        const val MAILTO_SCHEME = "mailto:"
        const val HTTP_SCHEME = "http://"
        const val HTTPS_SCHEME = "https://"
    }
}
