package com.neo.chevere.ui.settings

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.domain.WeatherUnitSystem
import com.neo.chevere.ui.designsystem.AtmosphericTheme

data class SettingsState(
    val isDarkMode: Boolean = false,
    val atmosphericTheme: AtmosphericTheme = AtmosphericTheme.CLASSIC_CYAN,
    val weatherUnitSystem: WeatherUnitSystem = WeatherUnitSystem.METRIC,
    val isBiometricLockEnabled: Boolean = false,
    val appVersion: String = "1.0.0-STABLE",
    val engineInfo: String = "Gemma 4 Edge",
    val protocolInfo: String = "On-Device Inference"
) : UiState

sealed class SettingsIntent : UiIntent {
    data class UpdateTheme(val isDark: Boolean) : SettingsIntent()
    data class UpdateAtmosphericTheme(val theme: AtmosphericTheme) : SettingsIntent()
    data class UpdateWeatherUnitSystem(val unitSystem: WeatherUnitSystem) : SettingsIntent()
    data class UpdateBiometricLock(val enabled: Boolean) : SettingsIntent()
}

sealed class SettingsEffect : UiEffect
