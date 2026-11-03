package com.neo.chevere.data

import com.neo.chevere.data.agent.ui.AgentUiEnvelope
import com.neo.chevere.domain.*
import org.junit.Assert.*
import org.junit.Test

class ChatMessageMetadataCodecTest {
    @Test fun sourcesAndTerminalOutcomeSurviveRoundTrip() {
        val content = ChatMessageMetadataCodec.Content("Answer", DocumentEvidence.Matches(listOf(DocumentExcerpt("policy.md", 2, "Warranty: 73 days"))), AgentOutcome.INCOMPLETE, TokenCountKind.MEASURED)
        assertEquals(content, ChatMessageMetadataCodec.decodeOrPlain(ChatMessageMetadataCodec.encode(content)))
    }

    @Test fun legacyTextAndChecklistsKeepTheirStorageContract() {
        assertEquals("old reply", ChatMessageMetadataCodec.decodeOrPlain("old reply").body)
        val oldChecklist = AgentUiEnvelope.checklist("Tasks", listOf(1, 2))
        assertEquals(oldChecklist, ChatMessageMetadataCodec.decodeOrPlain(oldChecklist).body)
        val stored = ChatMessageMetadataCodec.encode(ChatMessageMetadataCodec.Content(oldChecklist, outcome = AgentOutcome.ANSWERED))
        assertEquals(listOf(1, 2), AgentUiEnvelope.decode(ChatMessageMetadataCodec.decodeOrPlain(stored).body)!!.checklist.taskIds)
    }

    @Test fun malformedMetadataNeverBecomesVisibleProtocolJson() {
        assertEquals("", ChatMessageMetadataCodec.decodeOrPlain("CHEVERE_MESSAGE_V1:{bad}").body)
        assertEquals("", ChatMessageMetadataCodec.decodeOrPlain("CHEVERE_MESSAGE_V1:{\"body\":\"x\",\"version\":99}").body)
        val invalid = ChatMessageMetadataCodec.Content("reply", DocumentEvidence.Matches(listOf(DocumentExcerpt("x", -1, "bad"))))
        assertEquals("", ChatMessageMetadataCodec.decodeOrPlain(ChatMessageMetadataCodec.encode(invalid)).body)
    }
}
