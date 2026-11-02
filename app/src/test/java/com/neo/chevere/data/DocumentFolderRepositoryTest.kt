package com.neo.chevere.data

import androidx.room.Room
import com.neo.chevere.data.datasource.local.AppDatabase
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import com.neo.chevere.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentFolderRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase
    private lateinit var access: AndroidDocumentTreeAccess
    private lateinit var repository: LocalDocumentFolderRepository
    private val reference = "content://documents/tree/root"

    @Before fun setup() {
        context.getSharedPreferences("document_folders", 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        access = mock()
        whenever(access.name(reference)).thenReturn("Notes")
        repository = LocalDocumentFolderRepository(context, database.documentChunkDao(), access)
    }

    @After fun close() { database.close() }

    @Test fun refreshReplacesDeletedFilesAndDisconnectPreservesOtherSources() = runTest {
        val dao = database.documentChunkDao()
        dao.replaceDocument("content://individual", listOf(DocumentChunkEntity(filePath = "content://individual", fileName = "standalone.txt", chunkIndex = 0, text = "Keep this")))
        whenever(access.read(reference)).thenReturn(DocumentTreeSnapshot(listOf(TreeTextFile("content://old", "nested/old.md", "Old content")), 1))
        assertEquals(FolderIndexResult.Indexed(1, 1), repository.indexFolder(reference))
        verify(access).persist(reference)
        assertEquals("Notes", LocalDocumentFolderRepository(context, dao, access).getFolders().single().name)

        whenever(access.read(reference)).thenReturn(DocumentTreeSnapshot(listOf(TreeTextFile("content://new", "new.txt", "New content")), 0))
        assertEquals(FolderIndexResult.Indexed(1, 0), repository.indexFolder(reference))
        assertFalse(dao.getAllChunks().any { it.text == "Old content" })
        assertTrue(dao.getAllChunks().any { it.text == "New content" })
        val search = com.neo.chevere.data.agent.tools.LocalDocumentRagTool(context, dao)
            .execute(mapOf("action" to "search", "query" to "New content"))
        assertTrue(search is com.neo.chevere.data.agent.ToolResult.Success)
        assertTrue((search as com.neo.chevere.data.agent.ToolResult.Success).data.contains("Snippet: \"New content\""))

        assertEquals(FolderDisconnectResult.Disconnected, repository.disconnectFolder(reference))
        verify(access).release(reference)
        assertTrue(repository.getFolders().isEmpty())
        assertEquals("Keep this", dao.getAllChunks().single().text)
    }

    @Test fun failedRefreshKeepsPreviousSnapshotAndSourceForRecovery() = runTest {
        whenever(access.read(reference)).thenReturn(DocumentTreeSnapshot(listOf(TreeTextFile("content://old", "old.txt", "Retained")), 0))
        repository.indexFolder(reference)
        whenever(access.read(reference)).thenThrow(SecurityException("Revoked"))
        assertTrue(repository.indexFolder(reference) is FolderIndexResult.Failed)
        assertEquals("Retained", database.documentChunkDao().getAllChunks().single().text)
        assertEquals(reference, repository.getFolders().single().reference)
    }

    @Test fun emptyRefreshRemovesAllDeletedFiles() = runTest {
        whenever(access.read(reference)).thenReturn(DocumentTreeSnapshot(listOf(TreeTextFile("content://old", "old.txt", "Old")), 0))
        repository.indexFolder(reference)
        whenever(access.read(reference)).thenReturn(DocumentTreeSnapshot(emptyList(), 2))
        assertEquals(FolderIndexResult.Indexed(0, 2), repository.indexFolder(reference))
        assertTrue(database.documentChunkDao().getAllChunks().isEmpty())
    }

    @Test fun deniedPermissionDoesNotCreateASourceOrIndex() = runTest {
        doThrow(SecurityException()).whenever(access).persist(reference)
        assertTrue(repository.indexFolder(reference) is FolderIndexResult.Failed)
        assertTrue(repository.getFolders().isEmpty())
        assertTrue(database.documentChunkDao().getAllChunks().isEmpty())
    }

    @Test fun unknownSourceCannotBeDisconnected() = runTest {
        assertTrue(repository.disconnectFolder("content://unknown") is FolderDisconnectResult.Failed)
        verifyNoInteractions(access)
    }

    @Test fun failedFolderMetadataReadReleasesNewPermission() = runTest {
        whenever(access.name(reference)).thenThrow(SecurityException())
        assertTrue(repository.indexFolder(reference) is FolderIndexResult.Failed)
        verify(access).release(reference)
        assertTrue(repository.getFolders().isEmpty())
    }

    @Test fun cancellationPropagatesWithoutPublishingPartialIndex() = runTest {
        whenever(access.read(reference)).thenThrow(CancellationException())
        try {
            repository.indexFolder(reference)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(database.documentChunkDao().getAllChunks().isEmpty())
    }
}
