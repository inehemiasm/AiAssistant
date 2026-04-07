package com.neo.aiassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neo.aiassistant.ChatViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.ui.SettingsScreen
import com.neo.aiassistant.ui.chat.ChatScreen
import com.neo.aiassistant.ui.models.ModelsScreen
import kotlinx.coroutines.launch

@Composable
fun AiAssistantNavHost(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    preferenceManager: PreferenceManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Chat,
        modifier = modifier
    ) {
        composable<Route.Chat> {
            ChatScreen(
                viewModel = chatViewModel,
                onSettingsClick = { navController.navigate(Route.Settings) },
                onModelsClick = { navController.navigate(Route.Models) }
            )
        }

        composable<Route.Models> {
            ModelsScreen(
                viewModel = chatViewModel
            )
        }

        composable<Route.Settings> {
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = true)
            val scope = rememberCoroutineScope()
            
            SettingsScreen(
                isDarkMode = isDarkMode,
                onThemeChange = { scope.launch { preferenceManager.updateTheme(it) } },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
