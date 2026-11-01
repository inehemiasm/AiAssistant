package com.neo.chevere.data

import android.app.Application
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.neo.chevere.data.report.AiContentReportRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiContentReportFailureTest {
    @Test fun stalledWriteMustNotClaimSuccessfulSubmission() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val collection = mock<CollectionReference>()
        whenever(firestore.collection("ai_content_reports")).thenReturn(collection)
        whenever(collection.add(any())).thenReturn(TaskCompletionSource<DocumentReference>().task)
        val result = AiContentReportRepository(firestore, mock()).submitReport("message", "model", "unsafe", "")
        assertTrue(result.isFailure)
    }
}
