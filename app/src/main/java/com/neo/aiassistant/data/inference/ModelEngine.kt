package com.neo.aiassistant.data.inference

import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import com.neo.aiassistant.domain.LoadResult
import com.neo.aiassistant.domain.LocalModel
import kotlinx.coroutines.flow.Flow

interface ModelEngine {
    val initStatus: Flow<String>
    suspend fun load(model: LocalModel): LoadResult
    suspend fun generate(request: InferenceRequest): InferenceResult
    suspend fun unload()
    suspend fun clearConversation()
    fun isVisionSupported(): Boolean
}
