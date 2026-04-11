package com.neo.aiassistant.data.agent.tools

import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.ToolResult
import com.neo.aiassistant.domain.InstalledModelRegistry
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListModelsTool @Inject constructor(
    private val registry: InstalledModelRegistry
) : AgentTool {
    override val name: String = "listInstalledModels"
    override val description: String = "Returns a list of all AI models currently installed on the device."
    override val inputSchema: String = "{}"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val models = registry.getInstalledModels()
        if (models.isEmpty()) return ToolResult.Success("No models installed.")
        
        val list = models.joinToString("\n") { 
            "- ${it.displayName} (ID: ${it.id}, Runtime: ${it.runtime}, Task: ${it.taskType})"
        }
        return ToolResult.Success("Installed models:\n$list")
    }
}

@Singleton
class SelectModelTool @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val registry: InstalledModelRegistry
) : AgentTool {
    override val name: String = "selectModel"
    override val description: String = "Selects a different model to be used for future interactions."
    override val inputSchema: String = "modelId: The ID of the model to select."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val modelId = args["modelId"] ?: return ToolResult.Error("Missing 'modelId' argument")
        val model = registry.getInstalledModel(modelId) ?: return ToolResult.Error("Model '$modelId' not found.")
        
        preferenceManager.updateSelectedModel(modelId)
        return ToolResult.Success("Successfully selected model: ${model.displayName}. Note: The user might need to restart the session for it to take effect.")
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
