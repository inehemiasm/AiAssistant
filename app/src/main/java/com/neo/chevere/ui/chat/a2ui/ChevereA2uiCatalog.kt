package com.neo.chevere.ui.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.chevere.R
import com.neo.chevere.ui.tasks.taskUiModelFromProtocol
import com.neo.chevere.ui.tasks.TaskRowItem

/** Version the catalog ID when changing property contracts; AndroidX owns schema validation. */
internal const val CHEVERE_CATALOG_ID = "https://github.com/inehemiasm/AiAssistant/a2ui/catalogs/v1"
internal const val SET_TASK_COMPLETED = "set_task_completed"
internal val ChevereA2uiCatalog = A2uiCatalog(
    catalogId = CHEVERE_CATALOG_ID,
    components = listOf(TaskChecklistComponent)
)

/** Maps a validated A2UI component onto the native rows used on the Tasks screen. */
internal object TaskChecklistComponent : A2uiComponent {
    private val id = A2uiProperty.string("id", required = true)
    private val title = A2uiProperty.string("title", required = true)
    private val descriptionProp = A2uiProperty.string("description", required = true)
    private val completed = A2uiProperty.boolean("completed", required = true)
    private val enabled = A2uiProperty.boolean("enabled", required = true)
    private val tasks = A2uiProperty.nestedList(
        "tasks", listOf(id, title, descriptionProp, completed, enabled),
        required = true, maxItems = 50, isAdditionalPropertiesAllowed = false
    )
    override val name = "TaskChecklist"
    override val description = "A live checklist of existing local tasks."
    override val properties = listOf(tasks)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val rows = properties[tasks].orEmpty()
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) Text(stringResource(R.string.a2ui_no_tasks))
            rows.forEach { row ->
                val taskId = row[id]?.toIntOrNull() ?: return@forEach
                val isCompleted = row[completed] ?: false
                key(taskId) {
                    TaskRowItem(
                        task = taskUiModelFromProtocol(
                            id = taskId, title = row[title].orEmpty(),
                            description = row[descriptionProp].orEmpty(),
                            completed = isCompleted, enabled = row[enabled] == true
                        ),
                        onToggleStatus = {
                            dispatchAction(mapOf("event" to mapOf(
                                "name" to SET_TASK_COMPLETED,
                                "context" to mapOf("taskId" to taskId.toString(), "completed" to !isCompleted)
                            )))
                        }
                    )
                }
            }
        }
    }
}
