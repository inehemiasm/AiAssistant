package com.neo.aiassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.ui.designsystem.HighTechAiTheme
import com.neo.aiassistant.ui.navigation.AiAssistantApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.collectAsState

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = true)
            
            HighTechAiTheme(darkTheme = isDarkMode) {
                AiAssistantApp(
                    chatViewModel = viewModel,
                    preferenceManager = preferenceManager
                )
            }
        }
    }
}
