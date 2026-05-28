package com.neo.chevere.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.ui.designsystem.AtmosphericTheme
import com.neo.chevere.domain.ImageAspectRatio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(application, SettingsState()) {

    init {
        viewModelScope.launch {
            preferenceManager.themePreference.collectLatest { isDark ->
                setState { copy(isDarkMode = isDark) }
            }
        }
        viewModelScope.launch {
            preferenceManager.atmosphericThemePreference.collectLatest { theme ->
                setState { copy(atmosphericTheme = theme) }
            }
        }
        viewModelScope.launch {
            preferenceManager.weatherUnitPreference.collectLatest { unitSystem ->
                setState { copy(weatherUnitSystem = unitSystem) }
            }
        }
        viewModelScope.launch {
            preferenceManager.biometricLockPreference.collectLatest { enabled ->
                setState { copy(isBiometricLockEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferenceManager.defaultImageAspectRatioPreference.collectLatest { ratioStr ->
                val ratio = ImageAspectRatio.fromString(ratioStr)
                setState { copy(defaultImageAspectRatio = ratio) }
            }
        }
        viewModelScope.launch {
            preferenceManager.defaultImageStepsPreference.collectLatest { steps ->
                setState { copy(defaultImageSteps = steps) }
            }
        }
        viewModelScope.launch {
            preferenceManager.defaultImageGuidanceScalePreference.collectLatest { scale ->
                setState { copy(defaultImageGuidanceScale = scale) }
            }
        }
        viewModelScope.launch {
            preferenceManager.defaultImageNegativePromptPreference.collectLatest { prompt ->
                setState { copy(defaultImageNegativePrompt = prompt) }
            }
        }
    }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateTheme -> {
                preferenceManager.updateTheme(intent.isDark)
            }

            is SettingsIntent.UpdateAtmosphericTheme -> {
                preferenceManager.updateAtmosphericTheme(intent.theme)
            }

            is SettingsIntent.UpdateWeatherUnitSystem -> {
                preferenceManager.updateWeatherUnitSystem(intent.unitSystem)
            }

            is SettingsIntent.UpdateBiometricLock -> {
                preferenceManager.updateBiometricLock(intent.enabled)
            }

            is SettingsIntent.UpdateDefaultImageAspectRatio -> {
                preferenceManager.updateDefaultImageAspectRatio(intent.ratio.name)
            }

            is SettingsIntent.UpdateDefaultImageSteps -> {
                preferenceManager.updateDefaultImageSteps(intent.steps)
            }

            is SettingsIntent.UpdateDefaultImageGuidanceScale -> {
                preferenceManager.updateDefaultImageGuidanceScale(intent.scale)
            }

            is SettingsIntent.UpdateDefaultImageNegativePrompt -> {
                preferenceManager.updateDefaultImageNegativePrompt(intent.prompt)
            }
        }
    }
}
