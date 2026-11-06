package com.neo.chevere.data

import android.app.Application
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.neo.chevere.data.report.AiContentReportRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiContentReportFailureTest {
    @Test
    fun stalledWriteMustNotClaimSuccessfulSubmission() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val collection = mock<CollectionReference>()
        whenever(firestore.collection("ai_content_reports")).thenReturn(collection)
        whenever(collection.add(any())).thenReturn(TaskCompletionSource<DocumentReference>().task)
        val result = AiContentReportRepository(firestore, mock()).submitReport("message", "model", "unsafe", "")
        assertTrue(result.isFailure)
    }

    @Test
    fun successfulWriteIncludes90DayExpirationAndScrubbedFields() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val collection = mock<CollectionReference>()
        val docRef = mock<DocumentReference>()
        whenever(firestore.collection("ai_content_reports")).thenReturn(collection)
        whenever(collection.add(any())).thenReturn(Tasks.forResult(docRef))

        val beforeMs = System.currentTimeMillis()
        val result = AiContentReportRepository(firestore, mock()).submitReport(
            messageText = "Contact me at test@example.com for details",
            modelName = "gemma-4-E2B-it-web.litertlm",
            reason = "Inaccurate",
            additionalDetails = "Extra info"
        )
        assertTrue(result.isSuccess)

        val captor = argumentCaptor<Map<String, Any>>()
        verify(collection).add(captor.capture())
        val payload = captor.firstValue
        assertEquals(
            setOf("reason", "modelName", "messageSnippet", "details", "appVersion", "createdAt", "expiresAt"),
            payload.keys
        )
        val expiresAt = payload["expiresAt"] as Timestamp
        val deltaDays = (expiresAt.toDate().time - beforeMs) / (24.0 * 60.0 * 60.0 * 1000.0)
        assertTrue("Expected ~90 day retention window, got $deltaDays", deltaDays in 89.9..90.1)
    }
}
