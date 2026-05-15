package com.neo.chevere.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.PreferenceManager
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
            preferenceManager.weatherUnitPreference.collectLatest { unitSystem ->
                setState { copy(weatherUnitSystem = unitSystem) }
            }
        }
    }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateTheme -> {
                preferenceManager.updateTheme(intent.isDark)
            }

            is SettingsIntent.UpdateWeatherUnitSystem -> {
                preferenceManager.updateWeatherUnitSystem(intent.unitSystem)
            }
        }
    }
}
