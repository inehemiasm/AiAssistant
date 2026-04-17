package com.neo.chevere.core

/**
 * Centralized constant values used throughout the application.
 */
object Constants {

    object Network {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val KAGGE_REFERER = "https://www.kaggle.com/"
        const val ACCEPT_ALL = "*/*"
        const val HEADER_REFERER = "Referer"
    }

    object Agent {
        const val MAX_TOOL_CALLS_PER_TURN = 5
        const val TOOL_EXECUTION_TIMEOUT_MS = 30_000L
        const val SYSTEM_PROMPT_PREFIX = "SYSTEM: "
        const val USER_PROMPT_PREFIX = "\n\nUSER: "
        const val TOOL_ERROR_PREFIX = "TOOL_ERROR: "
        const val OBSERVATION_PREFIX = "OBSERVATION from "
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
