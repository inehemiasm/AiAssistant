package com.neo.aiassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.ui.designsystem.HighTechAiTheme
import com.neo.aiassistant.ui.navigation.AiAssistantApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the AI Assistant application.
 *
 * This activity serves as the entry point for the application, setting up the
 * user interface with Jetpack Compose and applying the app's theme based on
 * user preferences.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = true)
            
            HighTechAiTheme(darkTheme = isDarkMode) {
                AiAssistantApp()
            }
        }
    }
}
