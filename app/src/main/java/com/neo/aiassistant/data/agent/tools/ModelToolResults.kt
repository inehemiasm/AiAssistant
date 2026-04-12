package com.neo.aiassistant.data.agent.tools

import com.neo.aiassistant.domain.InstallStatus
import com.neo.aiassistant.domain.ModelCapability
import com.neo.aiassistant.domain.ModelRuntime
import com.neo.aiassistant.domain.ModelTaskType
import kotlinx.serialization.Serializable

@Serializable
data class ModelSummary(
    val id: String,
    val displayName: String,
    val isActive: Boolean,
    val runtime: String,
    val taskType: String,
    val capabilities: List<String>,
    val installStatus: String,
    val sizeBytes: Long?
)

@Serializable
data class InstalledModelsResult(
    val models: List<ModelSummary>
)

@Serializable
data class ModelDetailsResult(
    val id: String,
    val displayName: String,
    val isActive: Boolean,
    val runtime: ModelRuntime,
    val taskType: ModelTaskType,
    val capabilities: Set<ModelCapability>,
    val installStatus: InstallStatus,
    val sizeBytes: Long?,
    val fileName: String,
    val source: String,
    val isHealthy: Boolean
)

@Serializable
data class RecommendationResult(
    val recommendedModelId: String?,
    val modelDisplayName: String?,
    val reason: String
)

@Serializable
data class ModelSwitchResult(
    val success: Boolean,
    val message: String,
    val modelId: String? = null
)
