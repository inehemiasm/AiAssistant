package com.neo.chevere.data

import android.net.Uri
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import com.neo.chevere.domain.DocumentImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class DocumentImportTest {
    @Test fun selectedContentIsChunkedAndAtomicallyReplaced() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dao = mock<DocumentChunkDao>()
        val uri = Uri.parse("content://documents/test")
        val text = "Warranty covers repairs. ".repeat(80)
        shadowOf(context.contentResolver).registerInputStream(uri, text.byteInputStream())
        val result = LocalDocumentRepository(context, dao).importDocument(uri.toString())
        assertTrue(result is DocumentImportResult.Imported)
        val chunks = argumentCaptor<List<DocumentChunkEntity>>()
        verify(dao).replaceDocument(eq(uri.toString()), chunks.capture())
        assertTrue(chunks.firstValue.size > 1)
        assertEquals(text.take(800), chunks.firstValue.first().text)
        assertEquals(text.substring(600, 1400), chunks.firstValue[1].text)
    }

    @Test fun arbitraryPathsCannotBeImported() = runTest {
        val dao = mock<DocumentChunkDao>()
        val repository = LocalDocumentRepository(RuntimeEnvironment.getApplication(), dao)
        assertTrue(repository.importDocument("/sdcard/private.txt") is DocumentImportResult.Failed)
        verifyNoInteractions(dao)
    }
}
