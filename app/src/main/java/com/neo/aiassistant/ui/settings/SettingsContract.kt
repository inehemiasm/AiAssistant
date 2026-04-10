package com.neo.aiassistant.ui.settings

import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState

data class SettingsState(
    val isDarkMode: Boolean = false,
    val appVersion: String = "1.0.0-STABLE",
    val engineInfo: String = "Gemma 4 Edge",
    val protocolInfo: String = "On-Device Inference"
) : UiState

sealed class SettingsIntent : UiIntent {
    data class UpdateTheme(val isDark: Boolean) : SettingsIntent()
}

sealed class SettingsEffect : UiEffect
