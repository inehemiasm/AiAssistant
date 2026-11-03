package com.neo.chevere.ui.chat

import com.neo.chevere.data.agent.AgentState
import org.junit.Assert.*
import org.junit.Test

class ChatModelStatusTest {
    @Test fun downloadedFilesDoNotMeanTheEngineIsReady() {
        assertEquals(ChatModelStatus.NeedsModel, ChatState().modelStatus)
        assertEquals(ChatModelStatus.Downloading("Gemma", 42), ChatState(downloadStatus = DownloadStatusUi.Active("Gemma", 42)).modelStatus)
        assertTrue(ChatState(runtimeState = RuntimeState.Initializing("warmup")).modelStatus is ChatModelStatus.Loading)
        assertEquals(ModelRecovery.RELOAD, (ChatState(runtimeState = RuntimeState.Error("load failed")).modelStatus as ChatModelStatus.Failed).recovery)
    }

    @Test fun activeEngineAndConfirmationTakePriorityOverOldDownloadFailures() {
        val ready = ChatState(runtimeState = RuntimeState.Ready, selectedModel = "Gemma", downloadStatus = DownloadStatusUi.Failed("old", "failed"))
        assertEquals(ChatModelStatus.Ready("Gemma"), ready.modelStatus)
        assertEquals(ChatModelStatus.Generating, ready.copy(sendState = SendState.Sending).modelStatus)
        assertEquals(ChatModelStatus.AwaitingConfirmation, ready.copy(agentState = AgentState.WaitingForConfirmation("mcp", "Confirm")).modelStatus)
        assertEquals(ModelRecovery.RETRY_RESPONSE, (ready.copy(sendState = SendState.Error("inference failed")).modelStatus as ChatModelStatus.Failed).recovery)
    }
}
