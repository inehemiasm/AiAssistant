package com.neo.chevere.data.agent.tools

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.provider.ContactsContract
import com.neo.chevere.data.agent.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchContactsToolTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var tool: SearchContactsTool

    @Before
    fun setup() {
        context = mock()
        contentResolver = mock()
        whenever(context.contentResolver).doReturn(contentResolver)
        tool = SearchContactsTool(context)
    }

    @Test
    fun execute_whenPermissionDenied_returnsPermissionRequired() = runTest {
        whenever(context.checkPermission(eq(Manifest.permission.READ_CONTACTS), any(), any()))
            .doReturn(PackageManager.PERMISSION_DENIED)

        val args = mapOf("query" to "John")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Error)
        assertEquals("CONTACTS_PERMISSION_REQUIRED", (result as ToolResult.Error).message)
    }

    @Test
    fun execute_whenPermissionGrantedAndNoResults_returnsNoMatches() = runTest {
        whenever(context.checkPermission(eq(Manifest.permission.READ_CONTACTS), any(), any()))
            .doReturn(PackageManager.PERMISSION_GRANTED)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        val cursor = MatrixCursor(projection)
        whenever(contentResolver.query(any(), eq(projection), any(), any(), any()))
            .doReturn(cursor)

        val args = mapOf("query" to "John")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Success)
        assertEquals("No contact email addresses matched 'John'.", (result as ToolResult.Success).data)
    }

    @Test
    fun execute_whenPermissionGrantedAndResultsFound_returnsFormattedMatches() = runTest {
        whenever(context.checkPermission(eq(Manifest.permission.READ_CONTACTS), any(), any()))
            .doReturn(PackageManager.PERMISSION_GRANTED)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        val cursor = MatrixCursor(projection).apply {
            addRow(arrayOf("John Doe", "john@example.com"))
            addRow(arrayOf("Johnny", "johnny@example.com"))
        }
        whenever(contentResolver.query(any(), eq(projection), any(), any(), any()))
            .doReturn(cursor)

        val args = mapOf("query" to "john")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Success)
        val expected = "Matching contacts:\n- John Doe: john@example.com\n- Johnny: johnny@example.com"
        assertEquals(expected, (result as ToolResult.Success).data)
    }

    @Test
    fun execute_whenQueryIsBlank_returnsError() = runTest {
        val args = mapOf("query" to "   ")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Error)
        assertEquals("Missing 'query' argument", (result as ToolResult.Error).message)
    }
}
