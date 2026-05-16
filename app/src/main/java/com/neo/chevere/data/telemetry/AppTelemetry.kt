package com.neo.chevere.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.PiiUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-conscious telemetry facade for coarse app health and performance signals.
 *
 * This class must not receive raw prompts, image URIs, generated image paths, or
 * other user content. Keep event values categorical or numeric.
 */
interface AppTelemetry {
    fun logScreenViewed(screenName: String, productArea: String)
    fun logClick(action: String, screenName: String, productArea: String)
    fun setActiveModel(modelId: String)
    fun logModelInitStarted(modelId: String)
    fun logModelInitFinished(
        modelId: String,
        success: Boolean,
        durationMs: Long,
        errorType: String? = null
    )

    fun logChatTurnStarted(hasImage: Boolean, promptLength: Int)
    fun logChatTurnFinished(
        hasImage: Boolean,
        success: Boolean,
        durationMs: Long,
        errorType: String? = null
    )

    fun logImageGenerationStarted(hasConditionImage: Boolean, isExplicit: Boolean)
    fun logImageGenerationFinished(
        success: Boolean,
        durationMs: Long,
        isExplicit: Boolean,
        errorType: String? = null
    )

    fun logModelDownloadStarted(modelId: String, fileType: String)
    fun logModelDownloadFinished(
        modelId: String,
        success: Boolean,
        durationMs: Long,
        fileType: String,
        errorType: String? = null
    )

    fun logStopRequested(activeWork: Boolean)
    fun recordNonFatal(throwable: Throwable, context: String)
}

@Singleton
class FirebaseAppTelemetry @Inject constructor(
    @ApplicationContext context: Context
) : AppTelemetry {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    init {
        crashlytics.setCustomKey(TelemetryConstants.CrashKey.BUILD_TYPE, BuildConfig.BUILD_TYPE)
        crashlytics.setCustomKey(TelemetryConstants.CrashKey.VERSION_NAME, BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey(TelemetryConstants.CrashKey.VERSION_CODE, BuildConfig.VERSION_CODE)
    }

    override fun logScreenViewed(screenName: String, productArea: String) {
        logEvent(TelemetryConstants.Event.SCREEN_VIEW) {
            putString(TelemetryConstants.Param.SCREEN_NAME, screenName.safeValue())
            putString(TelemetryConstants.Param.PRODUCT_AREA, productArea.safeValue())
        }
    }

    override fun logClick(action: String, screenName: String, productArea: String) {
        logEvent(TelemetryConstants.Event.CLICK) {
            putString(TelemetryConstants.Param.ACTION, action.safeValue())
            putString(TelemetryConstants.Param.SCREEN_NAME, screenName.safeValue())
            putString(TelemetryConstants.Param.PRODUCT_AREA, productArea.safeValue())
        }
    }

    override fun setActiveModel(modelId: String) {
        crashlytics.setCustomKey(TelemetryConstants.CrashKey.ACTIVE_MODEL, modelId.safeValue())
    }

    override fun logModelInitStarted(modelId: String) {
        logEvent(TelemetryConstants.Event.MODEL_INIT_START) {
            putString(TelemetryConstants.Param.MODEL_ID, modelId.safeValue())
        }
    }

    override fun logModelInitFinished(
        modelId: String,
        success: Boolean,
        durationMs: Long,
        errorType: String?
    ) {
        logEvent(TelemetryConstants.Event.MODEL_INIT_FINISH) {
            putString(TelemetryConstants.Param.MODEL_ID, modelId.safeValue())
            putString(TelemetryConstants.Param.STATUS, success.status())
            putLong(TelemetryConstants.Param.DURATION_MS, durationMs)
            putOptionalString(TelemetryConstants.Param.ERROR_TYPE, errorType)
        }
    }

    override fun logChatTurnStarted(hasImage: Boolean, promptLength: Int) {
        logEvent(TelemetryConstants.Event.CHAT_TURN_START) {
            putString(
                TelemetryConstants.Param.INPUT_TYPE,
                if (hasImage) TelemetryConstants.Value.IMAGE_TEXT else TelemetryConstants.Value.TEXT
            )
            putString(TelemetryConstants.Param.PROMPT_LENGTH_BUCKET, promptLength.lengthBucket())
            putString(TelemetryConstants.Param.PRODUCT_AREA, TelemetryConstants.ProductArea.CHAT)
        }
    }

    override fun logChatTurnFinished(
        hasImage: Boolean,
        success: Boolean,
        durationMs: Long,
        errorType: String?
    ) {
        logEvent(TelemetryConstants.Event.CHAT_TURN_FINISH) {
            putString(
                TelemetryConstants.Param.INPUT_TYPE,
                if (hasImage) TelemetryConstants.Value.IMAGE_TEXT else TelemetryConstants.Value.TEXT
            )
            putString(TelemetryConstants.Param.STATUS, success.status())
            putLong(TelemetryConstants.Param.DURATION_MS, durationMs)
            putString(TelemetryConstants.Param.PRODUCT_AREA, TelemetryConstants.ProductArea.CHAT)
            putOptionalString(TelemetryConstants.Param.ERROR_TYPE, errorType)
        }
    }

    override fun logImageGenerationStarted(hasConditionImage: Boolean, isExplicit: Boolean) {
        logEvent(TelemetryConstants.Event.IMAGE_GENERATION_START) {
            putString(
                TelemetryConstants.Param.INPUT_TYPE,
                if (hasConditionImage) TelemetryConstants.Value.CONDITIONED else TelemetryConstants.Value.TEXT
            )
            putString(
                TelemetryConstants.Param.CONTENT_MODE,
                if (isExplicit) TelemetryConstants.Value.AGE_RESTRICTED_DEBUG else TelemetryConstants.Value.STANDARD
            )
            putString(
                TelemetryConstants.Param.PRODUCT_AREA,
                TelemetryConstants.ProductArea.IMAGE_GENERATION
            )
        }
    }

    override fun logImageGenerationFinished(
        success: Boolean,
        durationMs: Long,
        isExplicit: Boolean,
        errorType: String?
    ) {
        logEvent(TelemetryConstants.Event.IMAGE_GENERATION_FINISH) {
            putString(TelemetryConstants.Param.STATUS, success.status())
            putLong(TelemetryConstants.Param.DURATION_MS, durationMs)
            putString(
                TelemetryConstants.Param.CONTENT_MODE,
                if (isExplicit) TelemetryConstants.Value.AGE_RESTRICTED_DEBUG else TelemetryConstants.Value.STANDARD
            )
            putString(
                TelemetryConstants.Param.PRODUCT_AREA,
                TelemetryConstants.ProductArea.IMAGE_GENERATION
            )
            putOptionalString(TelemetryConstants.Param.ERROR_TYPE, errorType)
        }
    }

    override fun logModelDownloadStarted(modelId: String, fileType: String) {
        logEvent(TelemetryConstants.Event.MODEL_DOWNLOAD_START) {
            putString(TelemetryConstants.Param.MODEL_ID, modelId.safeValue())
            putString(TelemetryConstants.Param.FILE_TYPE, fileType.safeValue())
            putString(
                TelemetryConstants.Param.PRODUCT_AREA,
                TelemetryConstants.ProductArea.MODEL_MANAGEMENT
            )
        }
    }

    override fun logModelDownloadFinished(
        modelId: String,
        success: Boolean,
        durationMs: Long,
        fileType: String,
        errorType: String?
    ) {
        logEvent(TelemetryConstants.Event.MODEL_DOWNLOAD_FINISH) {
            putString(TelemetryConstants.Param.MODEL_ID, modelId.safeValue())
            putString(TelemetryConstants.Param.FILE_TYPE, fileType.safeValue())
            putString(TelemetryConstants.Param.STATUS, success.status())
            putLong(TelemetryConstants.Param.DURATION_MS, durationMs)
            putString(
                TelemetryConstants.Param.PRODUCT_AREA,
                TelemetryConstants.ProductArea.MODEL_MANAGEMENT
            )
            putOptionalString(TelemetryConstants.Param.ERROR_TYPE, errorType)
        }
    }

    override fun logStopRequested(activeWork: Boolean) {
        logEvent(TelemetryConstants.Event.GENERATION_STOP) {
            putString(TelemetryConstants.Param.ACTIVE_WORK, activeWork.toString())
            putString(TelemetryConstants.Param.PRODUCT_AREA, TelemetryConstants.ProductArea.CHAT)
        }
    }

    override fun recordNonFatal(throwable: Throwable, context: String) {
        crashlytics.setCustomKey(TelemetryConstants.CrashKey.NON_FATAL_CONTEXT, context.safeValue())
        crashlytics.recordException(throwable)
    }

    private fun logEvent(name: String, build: Bundle.() -> Unit) {
        analytics.logEvent(name, Bundle().apply(build))
    }

    private fun Bundle.putOptionalString(key: String, value: String?) {
        if (!value.isNullOrBlank()) putString(key, value.safeValue())
    }

    private fun Boolean.status(): String =
        if (this) TelemetryConstants.Value.SUCCESS else TelemetryConstants.Value.FAILURE

    private fun Int.lengthBucket(): String = when {
        this <= 0 -> TelemetryConstants.Value.EMPTY
        this < 40 -> TelemetryConstants.Value.SHORT
        this < 160 -> TelemetryConstants.Value.MEDIUM
        this < 600 -> TelemetryConstants.Value.LONG
        else -> TelemetryConstants.Value.VERY_LONG
    }

    private fun String.safeValue(): String = PiiUtils.scrub(this)
        .take(96)
        .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
}
