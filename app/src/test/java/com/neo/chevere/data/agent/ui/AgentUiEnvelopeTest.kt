package com.neo.chevere.data.agent.ui

import com.neo.chevere.domain.AgentUiContent
import org.junit.Assert.*
import org.junit.Test

class AgentUiEnvelopeTest {
    @Test fun roundTrip_preservesEscapedTextAndStableSurface() {
        val text = "Buy \"milk\"\nCafé ☕"
        val payload = AgentUiEnvelope.Payload(text = text,
            checklist = AgentUiContent.TaskChecklist("surface-1", listOf(4, 8)))
        assertEquals(payload, AgentUiEnvelope.decode(AgentUiEnvelope.encode(payload)))
        assertEquals(text, AgentUiEnvelope.displayText(AgentUiEnvelope.encode(payload)))
    }

    @Test fun invalidEnvelopes_doNotExposeProtocolOrBecomeActionable() {
        val prefix = AgentUiEnvelope.PREFIX
        val valid = AgentUiEnvelope.Payload(text = "Tasks", checklist =
            AgentUiContent.TaskChecklist("surface-1", listOf(1)))
        val invalid = listOf(prefix + "{", prefix + "x".repeat(65_537),
            AgentUiEnvelope.encode(valid.copy(version = 2)),
            AgentUiEnvelope.encode(valid.copy(checklist = valid.checklist.copy(taskIds = listOf(-1)))),
            AgentUiEnvelope.encode(valid.copy(checklist = valid.checklist.copy(taskIds = listOf(1, 1)))),
            AgentUiEnvelope.encode(valid.copy(checklist = valid.checklist.copy(surfaceId = "../../evil"))))
        invalid.forEach {
            assertNull(AgentUiEnvelope.decode(it))
            assertFalse(AgentUiEnvelope.displayText(it).contains(prefix))
        }
        assertEquals("Normal answer", AgentUiEnvelope.displayText("Normal answer"))
    }

    @Test fun checklist_boundsLargeTaskResults() {
        val encoded = AgentUiEnvelope.checklist("\n".repeat(100_000), (1..100).toList())
        val payload = requireNotNull(AgentUiEnvelope.decode(encoded))
        assertEquals(50, payload.checklist.taskIds.size)
        assertEquals(8_000, payload.text.length)
    }
}
