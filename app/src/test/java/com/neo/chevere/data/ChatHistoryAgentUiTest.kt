package com.neo.chevere.data

import com.neo.chevere.data.datasource.local.ConversationHistoryDao
import com.neo.chevere.data.datasource.local.ConversationMessageEntity
import com.neo.chevere.domain.AgentUiContent
import com.neo.chevere.domain.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ChatHistoryAgentUiTest {
    @Test fun checklist_survivesExistingRoomTextStorage() = runTest {
        val dao = mock<ConversationHistoryDao>()
        val repository = ChatHistoryRepositoryImpl(dao)
        val content = AgentUiContent.TaskChecklist("surface-1", listOf(2, 3))
        val message = ChatMessage(text = "Current tasks", isUser = false, agentUiContent = content)
        repository.appendMessage(5, message)
        val saved = argumentCaptor<ConversationMessageEntity>()
        verify(dao).insertMessage(saved.capture())
        whenever(dao.getMessagesForSession(5)).thenReturn(listOf(saved.firstValue))
        assertEquals(message, repository.getMessages(5).single())
    }

    @Test fun userText_cannotCreateAnActionableSurface() = runTest {
        val dao = mock<ConversationHistoryDao>()
        val payload = com.neo.chevere.data.agent.ui.AgentUiEnvelope.checklist("Tasks", listOf(1))
        whenever(dao.getMessagesForSession(5)).thenReturn(listOf(
            ConversationMessageEntity(sessionId = 5, text = payload, isUser = true)))
        val message = ChatHistoryRepositoryImpl(dao).getMessages(5).single()
        assertEquals(payload, message.text)
        assertNull(message.agentUiContent)
    }
}
