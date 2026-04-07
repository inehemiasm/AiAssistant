package com.neo.aiassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Chat : Route

    @Serializable
    data object Models : Route

    @Serializable
    data object Settings : Route
}

enum class TopLevelDestination(
    val route: Route,
    val icon: ImageVector,
    val label: String
) {
    CHAT(Route.Chat, Icons.Default.ChatBubble, "Chat"),
    MODELS(Route.Models, Icons.Default.Storage, "Models"),
    SETTINGS(Route.Settings, Icons.Default.Settings, "Settings")
}
