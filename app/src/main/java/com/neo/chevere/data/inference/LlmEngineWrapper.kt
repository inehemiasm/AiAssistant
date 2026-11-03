package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
    /** Actual last-turn counts when the SDK provides benchmark data. */
    fun usage(): com.neo.chevere.domain.InferenceUsage? = null
    suspend fun sendMessage(message: Message): Message
    fun sendMessageAsync(message: Message): Flow<Message>
    fun close()
}

@Singleton
class RealLlmEngineWrapper @Inject constructor() : LlmEngineWrapper {
    private var engine: Engine? = null
    private var conversationConfig = ConversationConfig()

    override fun initialize(config: EngineConfig) {
        @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
        fun enableCounts() { com.google.ai.edge.litertlm.ExperimentalFlags.enableBenchmark = true }
        enableCounts()
        conversationConfig = ModelRuntimeProfile.conversationConfig(config.modelPath)
        engine = Engine(config).apply { initialize() }
    }

    override fun createConversation(): ConversationWrapper {
        val conversation =
            engine?.createConversation(conversationConfig) ?: throw IllegalStateException("Engine not initialized")
        return RealConversationWrapper(conversation)
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}

class RealConversationWrapper(private val conversation: Conversation) : ConversationWrapper {
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    override fun usage(): com.neo.chevere.domain.InferenceUsage? = runCatching {
        conversation.getBenchmarkInfo().let {
            require(it.lastPrefillTokenCount > 0 && it.lastDecodeTokenCount >= 0)
            com.neo.chevere.domain.InferenceUsage(it.lastPrefillTokenCount, it.lastDecodeTokenCount)
        }
    }.getOrNull()
    override suspend fun sendMessage(message: Message): Message {
        return conversation.sendMessage(message)
    }

    override fun sendMessageAsync(message: Message): Flow<Message> = flow {
        val chunks = Channel<Message>(Channel.UNLIMITED)
        val terminal = CompletableDeferred<Unit>()
        // The SDK Flow's awaitClose is empty: cancelling collection leaves native
        // generation running. Own the callback lifecycle so close cannot free it early.
        conversation.sendMessageAsync(message, object : MessageCallback {
            override fun onMessage(message: Message) { chunks.trySend(message) }
            override fun onDone() {
                chunks.close()
                terminal.complete(Unit)
            }
            override fun onError(throwable: Throwable) {
                chunks.close(throwable)
                terminal.complete(Unit)
            }
        })
        try {
            for (chunk in chunks) emit(chunk)
        } finally {
            withContext(NonCancellable) {
                try {
                    if (!terminal.isCompleted) conversation.cancelProcess()
                } finally {
                    // A timeout must not free memory still used by callback_thread.
                    terminal.await()
                    chunks.cancel()
                }
            }
        }
    }

    override fun close() {
        conversation.close()
    }
}
