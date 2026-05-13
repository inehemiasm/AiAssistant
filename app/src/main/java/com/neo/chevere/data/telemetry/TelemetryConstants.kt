package com.neo.chevere.data.telemetry

/**
 * Stable analytics taxonomy for Chevere AI.
 *
 * Keep event names and parameter keys here so dashboards, funnels, and alerts
 * are not coupled to string literals scattered through feature code.
 */
object TelemetryConstants {
    object Event {
        const val SCREEN_VIEW = "screen_view"
        const val CLICK = "click"
        const val MODEL_INIT_START = "model_init_start"
        const val MODEL_INIT_FINISH = "model_init_finish"
        const val CHAT_TURN_START = "chat_turn_start"
        const val CHAT_TURN_FINISH = "chat_turn_finish"
        const val IMAGE_GENERATION_START = "image_gen_start"
        const val IMAGE_GENERATION_FINISH = "image_gen_finish"
        const val MODEL_DOWNLOAD_START = "model_download_start"
        const val MODEL_DOWNLOAD_FINISH = "model_download_finish"
        const val GENERATION_STOP = "generation_stop"
    }

    object Param {
        const val ACTION = "action"
        const val ACTIVE_WORK = "active_work"
        const val CONTENT_MODE = "content_mode"
        const val DURATION_MS = "duration_ms"
        const val ERROR_TYPE = "error_type"
        const val FILE_TYPE = "file_type"
        const val INPUT_TYPE = "input_type"
        const val MODEL_ID = "model_id"
        const val PRODUCT_AREA = "product_area"
        const val PROMPT_LENGTH_BUCKET = "prompt_length_bucket"
        const val SCREEN_NAME = "screen_name"
        const val STATUS = "status"
    }

    object CrashKey {
        const val ACTIVE_MODEL = "active_model"
        const val BUILD_TYPE = "build_type"
        const val NON_FATAL_CONTEXT = "non_fatal_context"
        const val VERSION_CODE = "version_code"
        const val VERSION_NAME = "version_name"
    }

    object ProductArea {
        const val CHAT = "chat"
        const val IMAGE_GENERATION = "image_generation"
        const val MODEL_MANAGEMENT = "model_management"
        const val SETTINGS = "settings"
        const val SYSTEM = "system"
    }

    object Screen {
        const val CHAT = "chat"
        const val MODEL_MARKETPLACE = "model_marketplace"
        const val MODEL_DETAILS = "model_details"
        const val SETTINGS = "settings"
        const val UNKNOWN = "unknown"
    }

    object Action {
        const val BOTTOM_NAV_CHAT = "bottom_nav_chat"
        const val BOTTOM_NAV_MODELS = "bottom_nav_models"
        const val BOTTOM_NAV_SETTINGS = "bottom_nav_settings"
    }

    object Value {
        const val AGE_RESTRICTED_DEBUG = "age_restricted_debug"
        const val CONDITIONED = "conditioned"
        const val EMPTY = "empty"
        const val FAILURE = "failure"
        const val IMAGE_TEXT = "image_text"
        const val LONG = "long"
        const val MEDIUM = "medium"
        const val SHORT = "short"
        const val STANDARD = "standard"
        const val SUCCESS = "success"
        const val TEXT = "text"
        const val VERY_LONG = "very_long"
    }

    object Context {
        const val CHAT_TURN = "chat_turn"
        const val IMAGE_GENERATION = "image_generation"
        const val MODEL_DOWNLOAD = "model_download"
        const val MODEL_INIT = "model_init"
    }
}
