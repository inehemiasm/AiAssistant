package com.neo.chevere.data

import android.content.Context
import com.neo.chevere.R
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import com.neo.chevere.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Owns saved folder metadata, persisted permission, and atomic per-source index replacement. */
@Singleton
class LocalDocumentFolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentChunkDao,
    private val access: AndroidDocumentTreeAccess,
) : DocumentFolderRepository {
    private val mutex = Mutex()
    private val preferences get() = context.getSharedPreferences("document_folders", Context.MODE_PRIVATE)

    override suspend fun getFolders(): List<DocumentFolder> = withContext(Dispatchers.IO) {
        mutex.withLock { readFolders() }
    }

    override suspend fun indexFolder(reference: String): FolderIndexResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            var retainGrant = false
            try {
                val existing = readFolders()
                retainGrant = existing.any { it.reference == reference }
                access.persist(reference)
                val name = access.name(reference)
                val folders = existing.filterNot { it.reference == reference } + DocumentFolder(reference, name)
                saveFolders(folders)
                retainGrant = true
                val snapshot = access.read(reference)
                val prefix = sourcePrefix(reference)
                val chunks = snapshot.files.flatMap { file ->
                    file.text.windowed(800, 600, partialWindows = true).mapIndexed { index, chunk ->
                        DocumentChunkEntity(filePath = prefix + file.reference, fileName = file.name, chunkIndex = index, text = chunk)
                    }
                }
                dao.replaceFolder(prefix, chunks)
                FolderIndexResult.Indexed(snapshot.files.size, snapshot.skipped)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FolderIndexResult.Failed(context.getString(R.string.folder_index_failed))
            } finally {
                if (!retainGrant) runCatching { access.release(reference) }
            }
        }
    }

    override suspend fun disconnectFolder(reference: String): FolderDisconnectResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                require(readFolders().any { it.reference == reference })
                dao.deleteFolder(sourcePrefix(reference))
                access.release(reference)
                saveFolders(readFolders().filterNot { it.reference == reference })
                FolderDisconnectResult.Disconnected
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FolderDisconnectResult.Failed(context.getString(R.string.folder_disconnect_failed))
            }
        }
    }

    private fun readFolders(): List<DocumentFolder> {
        val json = JSONObject(preferences.getString("sources", "{}") ?: "{}")
        return json.keys().asSequence().map { DocumentFolder(it, json.getString(it)) }.toList()
    }

    private fun saveFolders(folders: List<DocumentFolder>) {
        val json = JSONObject()
        folders.forEach { json.put(it.reference, it.name) }
        check(preferences.edit().putString("sources", json.toString()).commit())
    }

    private fun sourcePrefix(reference: String): String = "folder:" + MessageDigest.getInstance("SHA-256")
        .digest(reference.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ":"
}
