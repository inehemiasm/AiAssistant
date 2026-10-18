package com.neo.chevere.data.report

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.PiiUtils
import com.neo.chevere.data.telemetry.AppTelemetry
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiContentReportRepo"
private const val REPORTS_COLLECTION = "ai_content_reports"
private const val FIRESTORE_TIMEOUT_MS = 4_000L
private const val MAX_MESSAGE_SNIPPET_LENGTH = 500
private const val MAX_DETAILS_LENGTH = 300

/**
 * Handles in-app user reports of offensive, inaccurate, or inappropriate AI-generated responses.
 *
 * Satisfies Google Play's AI-Generated Content policy by persisting scrubbed reports to
 * Cloud Firestore (`ai_content_reports`) and recording an anonymized telemetry signal.
 */
@Singleton
class AiContentReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val telemetry: AppTelemetry
) {
    /**
     * Submits a user report for an AI-generated message.
     *
     * Uses Firestore's offline-capable persistence and a bounded timeout so the UI
     * never blocks if the device is offline.
     */
    suspend fun submitReport(
        messageText: String,
        modelName: String?,
        reason: String,
        additionalDetails: String
    ): Result<Unit> = runCatching {
        val safeReason = PiiUtils.scrub(reason).take(80)
        val safeModel = PiiUtils.scrub(modelName ?: "unknown").take(80)
        val safeSnippet = PiiUtils.scrub(messageText).take(MAX_MESSAGE_SNIPPET_LENGTH)
        val safeDetails = PiiUtils.scrub(additionalDetails).take(MAX_DETAILS_LENGTH)

        telemetry.logAiContentReported(reason = safeReason, modelId = safeModel)

        val payload = hashMapOf(
            "reason" to safeReason,
            "modelName" to safeModel,
            "messageSnippet" to safeSnippet,
            "details" to safeDetails,
            "appVersion" to BuildConfig.VERSION_NAME,
            "createdAt" to FieldValue.serverTimestamp()
        )

        withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
            firestore.collection(REPORTS_COLLECTION)
                .add(payload)
                .await()
        }
        Timber.tag(TAG).d("AI content report submitted (reason=%s, model=%s)", safeReason, safeModel)
    }
}
