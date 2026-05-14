package com.neo.chevere.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neo.chevere.domain.WeatherUnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manages user preferences using Jetpack DataStore.
 *
 * This class provides access to and allows modification of application settings,
 * such as the theme preference, selected AI model, and weather unit system.
 *
 * @property context The application context used to access DataStore.
 */
@Singleton
class PreferenceManager @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    /**
     * A [Flow] that emits the user's theme preference.
     * Emits `true` for dark/high-tech theme, `false` for light theme.
     * Defaults to `false` if no preference is set.
     */
    val themePreference: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[THEME_KEY] ?: false
        }

    /**
     * A [Flow] that emits the identifier of the currently selected AI model.
     * Emits `null` if no model has been selected yet.
     */
    val selectedModelPreference: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SELECTED_MODEL_KEY]
        }

    /**
     * A [Flow] that emits the user's preferred weather unit system.
     * Defaults to [WeatherUnitSystem.METRIC] if no preference is set.
     */
    val weatherUnitPreference: Flow<WeatherUnitSystem> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[WEATHER_UNIT_KEY]
                ?.let { value -> WeatherUnitSystem.entries.firstOrNull { it.name == value } }
                ?: WeatherUnitSystem.METRIC
        }

    /**
     * Updates the user's theme preference.
     *
     * @param isDark `true` to enable dark theme, `false` for light theme.
     */
    suspend fun updateTheme(isDark: Boolean) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = isDark
        }
    }

    /**
     * Updates the identifier of the selected AI model.
     *
     * @param modelName The identifier of the model to select.
     */
    suspend fun updateSelectedModel(modelName: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_KEY] = modelName
        }
    }

    /**
     * Updates the user's preferred weather unit system.
     */
    suspend fun updateWeatherUnitSystem(unitSystem: WeatherUnitSystem) {
        dataStore.edit { preferences ->
            preferences[WEATHER_UNIT_KEY] = unitSystem.name
        }
    }

    companion object {
        private val THEME_KEY = booleanPreferencesKey("theme_preference")
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val WEATHER_UNIT_KEY = stringPreferencesKey("weather_unit_system")
    }
}
