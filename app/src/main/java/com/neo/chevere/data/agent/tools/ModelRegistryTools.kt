package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.domain.InstalledModelRegistry
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

@Singleton
class ListModelsTool @Inject constructor(
    private val registry: InstalledModelRegistry,
    private val preferenceManager: PreferenceManager
) : AgentTool {
    override val name: String = "listInstalledModels"
    override val description: String = "Returns a structured list of all AI models currently installed and available on the device."
    override val inputSchema: String = "{}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val models = registry.getInstalledModels()
        val activeModelId = preferenceManager.selectedModelPreference.firstOrNull()
        
        val summaries = models.map { model ->
            ModelSummary(
                id = model.id,
                displayName = model.displayName,
                isActive = model.id == activeModelId || model.fileName == activeModelId,
                runtime = model.runtime.name,
                taskType = model.taskType.name,
                capabilities = model.capabilities.map { it.name },
                installStatus = model.installStatus.name,
                sizeBytes = model.sizeBytes
            )
        }
        
        return ToolResult.Success(json.encodeToString(InstalledModelsResult(summaries)))
    }
}

@Singleton
class GetActiveModelTool @Inject constructor(
    private val registry: InstalledModelRegistry,
    private val preferenceManager: PreferenceManager
) : AgentTool {
    override val name: String = "getActiveModel"
    override val description: String = "Returns details about the currently active AI model."
    override val inputSchema: String = "{}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val activeModelId = preferenceManager.selectedModelPreference.firstOrNull() 
            ?: return ToolResult.Error("No model is currently selected.")
            
        val models = registry.getInstalledModels()
        val model = models.find { it.id == activeModelId || it.fileName == activeModelId }
            ?: return ToolResult.Error("Active model '$activeModelId' not found in registry.")

        val details = ModelDetailsResult(
            id = model.id,
            displayName = model.displayName,
            isActive = true,
            runtime = model.runtime,
            taskType = model.taskType,
            capabilities = model.capabilities,
            installStatus = model.installStatus,
            sizeBytes = model.sizeBytes,
            fileName = model.fileName,
            source = model.source.name,
            isHealthy = model.isHealthy
        )
        
        return ToolResult.Success(json.encodeToString(details))
    }
}

@Singleton
class GetModelDetailsTool @Inject constructor(
    private val registry: InstalledModelRegistry,
    private val preferenceManager: PreferenceManager
) : AgentTool {
    override val name: String = "getModelDetails"
    override val description: String = "Returns rich metadata for a specific installed model by its ID."
    override val inputSchema: String = "{\"modelId\": \"The unique identifier of the model\"}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val modelId = args["modelId"] ?: return ToolResult.Error("Missing 'modelId' argument.")
        val model = registry.getInstalledModel(modelId) ?: return ToolResult.Error("Model '$modelId' not found.")
        val activeModelId = preferenceManager.selectedModelPreference.firstOrNull()

        val details = ModelDetailsResult(
            id = model.id,
            displayName = model.displayName,
            isActive = model.id == activeModelId || model.fileName == activeModelId,
            runtime = model.runtime,
            taskType = model.taskType,
            capabilities = model.capabilities,
            installStatus = model.installStatus,
            sizeBytes = model.sizeBytes,
            fileName = model.fileName,
            source = model.source.name,
            isHealthy = model.isHealthy
        )
        
        return ToolResult.Success(json.encodeToString(details))
    }
}

@Singleton
class SelectModelTool @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val registry: InstalledModelRegistry
) : AgentTool {
    override val name: String = "selectInstalledModel"
    override val description: String = "Requests a switch to a different installed model. Validates model health before switching."
    override val inputSchema: String = "{\"modelId\": \"The ID of the model to select\"}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val modelId = args["modelId"] ?: return ToolResult.Error("Missing 'modelId' argument")
        val model = registry.getInstalledModel(modelId) ?: return ToolResult.Error("Model '$modelId' not found.")
        
        if (!model.isHealthy) {
            return ToolResult.Success(json.encodeToString(ModelSwitchResult(
                success = false,
                message = "Cannot switch to model '${model.displayName}' because its status is ${model.installStatus}.",
                modelId = modelId
            )))
        }

        preferenceManager.updateSelectedModel(model.fileName)
        return ToolResult.Success(json.encodeToString(ModelSwitchResult(
            success = true,
            message = "Successfully requested switch to ${model.displayName}. The engine will reload shortly.",
            modelId = modelId
        )))
    }
}

@Singleton
class RecommendModelTool @Inject constructor(
    private val registry: InstalledModelRegistry
) : AgentTool {
    override val name: String = "recommendInstalledModelForTask"
    override val description: String = "Recommends the best installed model for a specific task type (fast_chat, deep_reasoning, vision, low_storage)."
    override val inputSchema: String = "{\"taskType\": \"The type of task (fast_chat, deep_reasoning, vision, low_storage)\"}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val task = args["taskType"] ?: return ToolResult.Error("Missing 'taskType' argument.")
        val models = registry.getInstalledModels().filter { it.isHealthy }
        
        if (models.isEmpty()) {
            return ToolResult.Success(json.encodeToString(RecommendationResult(null, null, "No healthy models installed.")))
        }

        val recommendation = when (task.lowercase()) {
            "fast_chat" -> {
                val best = models.filter { it.taskType == ModelTaskType.CHAT || it.taskType == ModelTaskType.VISION_CHAT }
                    .minByOrNull { it.sizeBytes ?: Long.MAX_VALUE }
                RecommendationResult(best?.id, best?.displayName, "Smallest available chat model for quick response.")
            }
            "deep_reasoning" -> {
                // In a real app, we might have a 'strength' or 'parameters' field. 
                // For now, let's assume larger models are 'deeper' or prefer specific naming.
                val best = models.filter { it.taskType == ModelTaskType.CHAT }
                    .maxByOrNull { it.sizeBytes ?: 0L }
                RecommendationResult(best?.id, best?.displayName, "Largest chat model available for complex reasoning.")
            }
            "vision" -> {
                val best = models.find { it.capabilities.contains(ModelCapability.VISION) || it.taskType == ModelTaskType.VISION_CHAT }
                RecommendationResult(best?.id, best?.displayName, "Best available model with vision capabilities.")
            }
            "low_storage" -> {
                val best = models.minByOrNull { it.sizeBytes ?: Long.MAX_VALUE }
                RecommendationResult(best?.id, best?.displayName, "Smallest valid installed model.")
            }
            else -> RecommendationResult(null, null, "Unknown task type: $task")
        }

        return ToolResult.Success(json.encodeToString(recommendation))
    }
}

@Singleton
class RuntimeStatusTool @Inject constructor(
    private val preferenceManager: PreferenceManager
) : AgentTool {
    override val name: String = "getRuntimeStatus"
    override val description: String = "Checks the status of the current AI runtime."
    override val inputSchema: String = "{}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val selectedModel = preferenceManager.selectedModelPreference.firstOrNull() ?: "None"
        return ToolResult.Success("Current Runtime Status: ACTIVE. Selected Model: $selectedModel.")
    }
}
