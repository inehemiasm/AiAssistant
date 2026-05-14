package com.neo.chevere.ui.settings

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.domain.WeatherUnitSystem

data class SettingsState(
    val isDarkMode: Boolean = false,
    val weatherUnitSystem: WeatherUnitSystem = WeatherUnitSystem.METRIC,
    val appVersion: String = "1.0.0-STABLE",
    val engineInfo: String = "Gemma 4 Edge",
    val protocolInfo: String = "On-Device Inference"
) : UiState

sealed class SettingsIntent : UiIntent {
    data class UpdateTheme(val isDark: Boolean) : SettingsIntent()
    data class UpdateWeatherUnitSystem(val unitSystem: WeatherUnitSystem) : SettingsIntent()
}

sealed class SettingsEffect : UiEffect
