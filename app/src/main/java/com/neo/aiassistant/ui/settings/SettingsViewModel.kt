package com.neo.aiassistant.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
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
    }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateTheme -> {
                preferenceManager.updateTheme(intent.isDark)
            }
        }
    }
}
