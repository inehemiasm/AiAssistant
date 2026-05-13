package com.neo.chevere.core

/**
 * Centralized constant values used throughout the application.
 */
object Constants {

    object Network {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val ACCEPT_ALL = "*/*"
    }

    object Agent {
        const val MAX_TOOL_CALLS_PER_TURN = 5
        const val TOOL_EXECUTION_TIMEOUT_MS = 30_000L
        const val IMAGE_GENERATION_TOOL_TIMEOUT_MS = 10 * 60_000L
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

        val QUALCOMM_REQUIRED_FILES = listOf(
            "metadata.json",
            "text_encoder.onnx",
            "text_encoder_qairt_context.bin",
            "unet.onnx",
            "unet_qairt_context.bin",
            "vae.onnx",
            "vae_qairt_context.bin"
        )
    }

    object Download {
        const val INPUT_URL = "url"
        const val INPUT_MODEL_NAME = "modelName"
        const val INPUT_MODEL_ID = "modelId"
        const val INPUT_SHA256 = "sha256"
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
    }

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
        const val MAX_NUM_TOKENS = 4096
        const val MAX_NUM_IMAGES = 1
        const val NEURAL_CACHE_DIR = "neural_cache"
        const val MIN_MODEL_FILE_SIZE_BYTES = 1024 * 1024L // 1MB
    }

    object AppActions {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        const val MAPS_QUERY_URI = "geo:0,0?q="
        const val MAILTO_SCHEME = "mailto:"
        const val HTTP_SCHEME = "http://"
        const val HTTPS_SCHEME = "https://"
    }
}
