package com.neo.chevere.data.agent.tools

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val MAX_TEXT_CHARS = 24_000

/**
 * Reads local text-like documents from a user-provided content URI or accessible file path.
 */
class ReadLocalFileTool @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "read_local_file"
    override val description: String =
        "Reads a local text, markdown, or PDF document when given a System Document Picker contentUri or an accessible local filePath. For Downloads files, ask the user to pick the file if direct access is unavailable."
    override val inputSchema: String =
        "contentUri: Optional content:// URI selected by the user. filePath: Optional accessible absolute path. maxChars: Optional maximum text characters."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val maxChars = args["maxChars"]?.toIntOrNull()?.coerceIn(1_000, MAX_TEXT_CHARS)
            ?: MAX_TEXT_CHARS
        val contentUri = args["contentUri"]?.trim()?.takeIf { it.isNotBlank() }
        val filePath = args["filePath"]?.trim()?.takeIf { it.isNotBlank() }

        when {
            contentUri != null -> readContentUri(Uri.parse(contentUri), maxChars)
            filePath != null -> readFilePath(filePath, maxChars)
            else -> ToolResult.Error(
                "Missing file reference. Ask the user to select the document with the Android document picker, then call read_local_file with contentUri."
            )
        }
    }

    private fun readContentUri(uri: Uri, maxChars: Int): ToolResult {
        val type = context.contentResolver.getType(uri).orEmpty()
        return if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            readPdf(uri)
        } else {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText().take(maxChars)
            } ?: return ToolResult.Error("Could not open selected document.")
            ToolResult.Success(formatTextResult(uri.lastPathSegment ?: "selected document", text, maxChars))
        }
    }

    private fun readFilePath(path: String, maxChars: Int): ToolResult {
        val file = File(path)
        if (!file.isFile) return ToolResult.Error("File not found: $path")
        if (!isAllowedPath(file)) {
            return ToolResult.Error(
                "This file path is outside app-accessible storage. Ask the user to select it with the Android document picker."
            )
        }
        return if (file.extension.equals("pdf", ignoreCase = true)) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                readPdf(descriptor, file.name)
            }
        } else {
            val text = file.bufferedReader().use { it.readText().take(maxChars) }
            ToolResult.Success(formatTextResult(file.name, text, maxChars))
        }
    }

    private fun readPdf(uri: Uri): ToolResult {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return ToolResult.Error("Could not open PDF document.")
        return descriptor.use { readPdf(it, uri.lastPathSegment ?: "selected PDF") }
    }

    private fun readPdf(descriptor: ParcelFileDescriptor, label: String): ToolResult {
        PdfRenderer(descriptor).use { renderer ->
            return ToolResult.Success(
                "$label is a PDF with ${renderer.pageCount} page(s). Android PdfRenderer can open and render the pages, but it does not expose embedded text for summarization without an OCR/text extraction layer."
            )
        }
    }

    private fun isAllowedPath(file: File): Boolean {
        val canonical = file.canonicalFile
        val allowedRoots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ).map { it.canonicalFile }

        return allowedRoots.any { root ->
            canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)
        }
    }

    private fun formatTextResult(label: String, text: String, maxChars: Int): String {
        val suffix = if (text.length >= maxChars) "\n\n[Truncated to $maxChars characters.]" else ""
        return "Read $label:\n$text$suffix"
    }
}
