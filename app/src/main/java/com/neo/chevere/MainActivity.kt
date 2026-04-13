package com.neo.chevere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.ui.LaunchAnimatedApp
import com.neo.chevere.ui.designsystem.HighTechAiTheme
import com.neo.chevere.ui.navigation.ChevereApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the AI Assistant application.
 *
 * Handles splash screen transitions, theme management, and edge-to-edge layout.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = systemDark)
            
            HighTechAiTheme(darkTheme = isDarkMode) {
                // Wrap the main app entry with the launch animation
                LaunchAnimatedApp {
                    ChevereApp()
                }
            }
        }
    }
}
