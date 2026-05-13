package com.neo.chevere.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.neo.chevere.R
import com.neo.chevere.data.telemetry.TelemetryConstants
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Chat : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object ModelMarketplace : Route

    @Serializable
    data class ModelDetails(val modelId: String) : Route
}

enum class TopLevelDestination(
    val route: Route,
    val icon: ImageVector,
    val labelResId: Int,
    val telemetryAction: String
) {
    CHAT(Route.Chat, Icons.Default.ChatBubble, R.string.chat_label, TelemetryConstants.Action.BOTTOM_NAV_CHAT),
    MODELS(Route.ModelMarketplace, Icons.Default.Storage, R.string.models_label, TelemetryConstants.Action.BOTTOM_NAV_MODELS),
    SETTINGS(Route.Settings, Icons.Default.Settings, R.string.settings_label, TelemetryConstants.Action.BOTTOM_NAV_SETTINGS)
}
