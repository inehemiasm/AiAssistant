package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Backend
import com.neo.chevere.core.Constants
import java.io.File

/** Model-specific backend constraints, limits, and template settings. */
internal object ModelRuntimeProfile {
    private fun isSpark(path: String) = File(path).name == "Spark-X2.6-4B_int8.litertlm"

    /** Corrects the historical vision label on the text-only Web Gemma artifact. */
    fun isTextOnlyGpu(path: String) = File(path).name == "gemma-4-E2B-it-web.litertlm"

    /** Vision availability and main backend compatibility are independent. */
    fun backends(path: String, enableVision: Boolean): List<Triple<Backend, Backend?, String>> =
        when {
            isTextOnlyGpu(path) -> listOf(Triple(Backend.GPU(), null, "GPU Text-Only"))
            isSpark(path) -> listOf(Triple(Backend.CPU(), null, "CPU Text-Only"))
            else -> buildList {
                if (enableVision) {
                    add(Triple(Backend.GPU(), Backend.CPU(), "GPU + Vision"))
                    add(Triple(Backend.CPU(), Backend.CPU(), "CPU + Vision"))
                }
                add(Triple(Backend.CPU(), null, "CPU Text-Only"))
                add(Triple(Backend.GPU(), null, "GPU Text-Only"))
            }
        }

    fun contextSize(path: String): Int =
        if (isSpark(path)) 4096 else Constants.Inference.MAX_CONTEXT_TOKENS

    fun outputLimit(path: String): Int = if (isSpark(path)) 1536 else Constants.Inference.MAX_OUTPUT_TOKENS

    fun conversationConfig(path: String): ConversationConfig =
        if (isSpark(path)) ConversationConfig(
            automaticToolCalling = false,
            extraContext = mapOf("enable_thinking" to false),
            maxOutputToken = 1536,
        ) else ConversationConfig(maxOutputToken = Constants.Inference.MAX_OUTPUT_TOKENS)
}
