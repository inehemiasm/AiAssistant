package com.neo.chevere.data.agent.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Searches local device contacts so the agent can resolve names before drafting messages.
 */
class SearchContactsTool @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "search_contacts"
    override val description: String =
        "Searches device contacts by name or email and returns matching email addresses. Use before draft_email when the user names a person instead of giving an email address."
    override val inputSchema: String =
        "query: Contact name or email search text. maxResults: Optional maximum matches, default 5."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.trim().orEmpty()
        if (query.isBlank()) return@withContext ToolResult.Error("Missing 'query' argument")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext ToolResult.Error("CONTACTS_PERMISSION_REQUIRED")
        }

        val maxResults = args["maxResults"]?.toIntOrNull()?.coerceIn(1, 10) ?: 5
        val matches = searchContactEmails(query, maxResults)
        if (matches.isEmpty()) {
            ToolResult.Success("No contact email addresses matched '$query'.")
        } else {
            ToolResult.Success(
                matches.joinToString(
                    separator = "\n",
                    prefix = "Matching contacts:\n"
                ) { contact ->
                    "- ${contact.name}: ${contact.email}"
                }
            )
        }
    }

    private fun searchContactEmails(query: String, maxResults: Int): List<ContactEmail> {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        val selection =
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?"
        val likeQuery = "%$query%"
        val sortOrder = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"

        return resolver.query(uri, projection, selection, arrayOf(likeQuery, likeQuery), sortOrder)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY
                )
                val emailIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                )
                val contacts = mutableListOf<ContactEmail>()
                val seenEmails = mutableSetOf<String>()
                while (cursor.moveToNext() && contacts.size < maxResults) {
                    val email = cursor.getString(emailIndex)?.trim().orEmpty()
                    if (email.isBlank() || !seenEmails.add(email.lowercase())) continue
                    val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { "Unknown" }
                    contacts += ContactEmail(name = name, email = email)
                }
                contacts
            }
            .orEmpty()
    }

    private data class ContactEmail(
        val name: String,
        val email: String
    )
}
