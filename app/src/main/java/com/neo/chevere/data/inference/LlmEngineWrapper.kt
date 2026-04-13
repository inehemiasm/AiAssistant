package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around LiteRT-LM Engine to facilitate testing.
 */
interface LlmEngineWrapper {
    fun initialize(config: EngineConfig)
    fun createConversation(): ConversationWrapper
    fun close()
}

/**
 * Wrapper around LiteRT-LM Conversation to facilitate testing.
 */
interface ConversationWrapper {
    suspend fun sendMessage(message: Message): Message
    fun close()
}

@Singleton
class RealLlmEngineWrapper @Inject constructor() : LlmEngineWrapper {
    private var engine: Engine? = null

    override fun initialize(config: EngineConfig) {
        engine = Engine(config).apply { initialize() }
    }

    override fun createConversation(): ConversationWrapper {
        val conversation = engine?.createConversation() ?: throw IllegalStateException("Engine not initialized")
        return RealConversationWrapper(conversation)
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}

class RealConversationWrapper(private val conversation: Conversation) : ConversationWrapper {
    override suspend fun sendMessage(message: Message): Message {
        return conversation.sendMessage(message)
    }

    override fun close() {
        conversation.close()
    }
}
