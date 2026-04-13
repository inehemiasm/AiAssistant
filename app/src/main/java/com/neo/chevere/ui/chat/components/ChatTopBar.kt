package com.neo.chevere.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * A top app bar for the chat screen.
 *
 * Displays the currently selected model, provides a menu for model selection,
 * and actions for clearing the chat and navigating to profile/settings.
 *
 * @param isInteractionEnabled Whether user interaction with the bar's elements is allowed.
 * @param selectedModel The name of the currently selected AI model.
 * @param availableModels List of model names available for switching.
 * @param onModelSelected Callback triggered when a model is selected from the dropdown.
 * @param onClearChat Callback triggered when the "clear chat" button is clicked.
 * @param onModelsClick Callback triggered when the navigation icon (menu) is clicked.
 * @param onSettingsClick Callback triggered when the profile icon is clicked.
 * @param modifier The modifier to be applied to the top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    isInteractionEnabled: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    onClearChat: () -> Unit,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onModelsClick) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu_library), tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().clickable { if (isInteractionEnabled && availableModels.isNotEmpty()) showMenu = true }
            ) {
                Text(
                    text = selectedModel.replace(".litertlm", "").uppercase().ifEmpty { "SELECT MODEL" },
                    style = Typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Text(
                    text = stringResource(R.string.nebula_core_private_ai),
                    style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                
                if (availableModels.isNotEmpty()) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        model.replace(".litertlm", "").uppercase(), 
                                        color = if (model == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = { onModelSelected(model); showMenu = false }
                            )
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onClearChat, enabled = isInteractionEnabled) {
                Icon(Icons.Default.DeleteSweep, stringResource(R.string.clear_chat), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Person, 
                    stringResource(R.string.profile), 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape).padding(4.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
    )
}
