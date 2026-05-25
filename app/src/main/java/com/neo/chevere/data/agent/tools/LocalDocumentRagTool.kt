package com.neo.chevere.data.agent.tools

import android.content.Context
import android.os.Environment
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A tool that indexes folders of local text/markdown documents and performs TF-IDF vector search.
 */
class LocalDocumentRagTool @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val documentChunkDao: DocumentChunkDao
) : AgentTool {

    override val name: String = "document_rag"
    override val description: String =
        "Indexes local text/markdown document folders and performs search over the indexed content. Actions: 'index' (indexes a folderPath) or 'search' (searches the index for a query)."
    override val inputSchema: String =
        "action: 'index' or 'search'. folderPath: Required for 'index'. query: Required for 'search'."

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "else", "of", "at", "by", "for", 
        "with", "about", "against", "between", "into", "through", "during", "before", 
        "after", "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", 
        "over", "under", "again", "further", "then", "once", "here", "there", "when", 
        "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", 
        "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", 
        "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", 
        "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", 
        "hers", "herself", "it", "its", "itself", "they", "them", "their", "theirs", 
        "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", 
        "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", 
        "having", "do", "does", "did", "doing"
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.trim()?.lowercase() ?: return@withContext ToolResult.Error("Missing 'action' argument")

        when (action) {
            "index" -> {
                val folderPath = args["folderPath"]?.trim() ?: return@withContext ToolResult.Error("Missing 'folderPath' argument for index action")
                indexFolder(folderPath)
            }
            "search" -> {
                val query = args["query"]?.trim() ?: return@withContext ToolResult.Error("Missing 'query' argument for search action")
                searchIndex(query)
            }
            else -> ToolResult.Error("Unsupported action '$action'. Use 'index' or 'search'.")
        }
    }

    private suspend fun indexFolder(path: String): ToolResult {
        val folder = File(path)
        if (!folder.isDirectory) {
            return ToolResult.Error("Provided path is not a valid directory: $path")
        }

        if (!isAllowedPath(folder)) {
            return ToolResult.Error(
                "Access denied. Directory is outside app-accessible storage (Downloads, private files, or cache)."
            )
        }

        val textFiles = folder.walkTopDown()
            .filter { it.isFile && (it.extension.equals("txt", ignoreCase = true) || it.extension.equals("md", ignoreCase = true)) }
            .toList()

        if (textFiles.isEmpty()) {
            return ToolResult.Success("No text (.txt) or markdown (.md) files found in $path.")
        }

        var filesIndexed = 0
        var totalChunksCreated = 0

        for (file in textFiles) {
            val text = try {
                file.readText()
            } catch (e: Exception) {
                continue
            }
            if (text.isBlank()) continue

            // Delete old chunks for this file
            documentChunkDao.deleteChunksForFile(file.absolutePath)

            // Chunk document text: chunk size = 800 chars, overlap = 200 chars
            val chunks = chunkText(text, chunkSize = 800, overlap = 200)
            val entities = chunks.mapIndexed { index, chunk ->
                DocumentChunkEntity(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    chunkIndex = index,
                    text = chunk
                )
            }
            documentChunkDao.insertChunks(entities)
            filesIndexed++
            totalChunksCreated += entities.size
        }

        return ToolResult.Success("RAG Index Completed: Successfully indexed $filesIndexed file(s) into $totalChunksCreated content chunks under $path.")
    }

    private suspend fun searchIndex(query: String): ToolResult {
        val chunks = documentChunkDao.getAllChunks()
        if (chunks.isEmpty()) {
            return ToolResult.Success("No documents are indexed. Please index a document folder first using: action='index' folderPath='...'")
        }

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return ToolResult.Error("Empty or invalid search query. Please use search terms with letters.")
        }

        // Calculate IDF for each query term
        val totalChunks = chunks.size
        val idfMap = mutableMapOf<String, Double>()
        for (term in queryTokens) {
            val docsWithTerm = chunks.count { it.text.contains(term, ignoreCase = true) }
            // Smoothed IDF
            idfMap[term] = ln(1.0 + (totalChunks.toDouble() / (docsWithTerm.toDouble() + 1.0))) + 1.0
        }

        // Calculate query vector magnitude
        // Query term TF is just count of occurrence / total terms in query
        val queryTfs = queryTokens.groupingBy { it }.eachCount()
        val queryVector = queryTokens.distinct().associateWith { term ->
            val tf = queryTfs[term]!!.toDouble() / queryTokens.size
            tf * idfMap[term]!!
        }
        val queryMagnitude = sqrt(queryVector.values.sumOf { it * it })
        if (queryMagnitude == 0.0) {
            return ToolResult.Success("No matches found for query: '$query'.")
        }

        data class SearchMatch(val chunk: DocumentChunkEntity, val similarity: Double)

        val matches = mutableListOf<SearchMatch>()

        for (chunk in chunks) {
            val chunkTokens = tokenize(chunk.text)
            if (chunkTokens.isEmpty()) continue

            val chunkTfs = chunkTokens.groupingBy { it }.eachCount()

            // Construct document vector for the query terms
            var dotProduct = 0.0
            var docVectorSquaredSum = 0.0

            for (term in queryTokens.distinct()) {
                val qWeight = queryVector[term] ?: 0.0
                val docTermCount = chunkTfs[term] ?: 0
                val tf = docTermCount.toDouble() / chunkTokens.size
                val dWeight = tf * idfMap[term]!!

                dotProduct += qWeight * dWeight
            }

            // Document magnitude squared (over all terms in chunk, not just query terms, for proper normalization)
            for (term in chunkTokens.distinct()) {
                val docTermCount = chunkTfs[term] ?: 0
                val tf = docTermCount.toDouble() / chunkTokens.size
                // Use default IDF 1.0 for non-query terms to estimate magnitude
                val idf = idfMap[term] ?: 1.0
                val dWeight = tf * idf
                docVectorSquaredSum += dWeight * dWeight
            }

            val docMagnitude = sqrt(docVectorSquaredSum)
            val similarity = if (docMagnitude > 0.0) {
                dotProduct / (queryMagnitude * docMagnitude)
            } else {
                0.0
            }

            if (similarity > 0.0) {
                matches.add(SearchMatch(chunk, similarity))
            }
        }

        val sortedMatches = matches.sortedByDescending { it.similarity }.take(3)

        return if (sortedMatches.isEmpty()) {
            ToolResult.Success("No matches found for query: '$query' in the local index.")
        } else {
            val resultsText = sortedMatches.joinToString("\n\n") { match ->
                val scorePercent = "%.1f%%".format(Locale.US, match.similarity * 100.0)
                "[Score: $scorePercent] Source: ${match.chunk.fileName} (${match.chunk.filePath})\nSnippet: \"${match.chunk.text.trim()}\""
            }
            ToolResult.Success("Top matching document chunks:\n\n$resultsText")
        }
    }

    private fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
        if (text.length <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            chunks.add(text.substring(start, end))
            start += chunkSize - overlap
        }
        return chunks
    }

    private fun tokenize(text: String): List<String> {
        val words = text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return words.filter { it !in stopWords }
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
}
