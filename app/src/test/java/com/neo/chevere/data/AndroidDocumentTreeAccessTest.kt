package com.neo.chevere.data

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidDocumentTreeAccessTest {
    private val resolver = mock<ContentResolver>()
    private val context = mock<Context> { on { contentResolver } doReturn resolver }
    private val access = AndroidDocumentTreeAccess(context)
    private val tree = Uri.parse("content://documents/tree/root")
    private fun children(id: String) = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
    private fun rows(vararg rows: Array<String>) = MatrixCursor(arrayOf("document_id", "_display_name", "mime_type")).apply {
        rows.forEach { addRow(it) }
    }
    private fun cursor(id: String, cursor: MatrixCursor) {
        whenever(resolver.query(eq(children(id)), any(), isNull(), isNull(), isNull())).thenReturn(cursor)
    }

    @Test fun nestedFilesUseScopedUrisAndSkipUnsupportedContent() = runTest {
        cursor("root", rows(arrayOf("nested", "nested", DocumentsContract.Document.MIME_TYPE_DIR), arrayOf("pdf", "ignore.pdf", "application/pdf")))
        cursor("nested", rows(arrayOf("note", "note.md", "text/markdown")))
        val file = DocumentsContract.buildDocumentUriUsingTree(tree, "note")
        whenever(resolver.openInputStream(file)).thenReturn("Searchable fact".byteInputStream())

        val result = access.read(tree.toString())

        assertEquals("nested/note.md", result.files.single().name)
        assertEquals(file.toString(), result.files.single().reference)
        assertEquals("Searchable fact", result.files.single().text)
        assertEquals(1, result.skipped)
        verify(resolver, never()).openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, "pdf"))
    }

    @Test fun oversizedTextFailsRatherThanReturningAPartialSnapshot() = runTest {
        cursor("root", rows(arrayOf("large", "large.txt", "text/plain")))
        whenever(resolver.openInputStream(any())).thenReturn(ByteArray(2 * 1024 * 1024 + 1) { 65 }.inputStream())
        try {
            access.read(tree.toString())
            fail("Expected size limit failure")
        } catch (_: IllegalStateException) { }
    }

    @Test fun malformedUtf8FailsRatherThanSilentlyChangingText() = runTest {
        cursor("root", rows(arrayOf("bad", "bad.txt", "text/plain")))
        whenever(resolver.openInputStream(any())).thenReturn(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
        try {
            access.read(tree.toString())
            fail("Expected UTF-8 decoding failure")
        } catch (_: java.nio.charset.CharacterCodingException) { }
    }

    @Test fun arbitraryFilesystemPathsCannotObtainAccess() {
        try {
            access.persist("/sdcard/Documents")
            fail("Expected URI validation failure")
        } catch (_: IllegalArgumentException) { }
        verifyNoInteractions(resolver)
    }

    @Test fun persistRequestsOnlyReadPermissionForTheSelectedTree() {
        access.persist(tree.toString())
        verify(resolver).takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
