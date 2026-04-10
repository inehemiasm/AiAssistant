package com.neo.aiassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neo.aiassistant.ui.chat.ChatScreen
import com.neo.aiassistant.ui.marketplace.ModelMarketplaceScreen
import com.neo.aiassistant.ui.models.ModelsScreen
import com.neo.aiassistant.ui.settings.SettingsScreen

@Composable
fun AiAssistantNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Chat,
        modifier = modifier
    ) {
        composable<Route.Chat> {
            ChatScreen(
                onSettingsClick = { navController.navigate(Route.Settings) },
                onModelsClick = { navController.navigate(Route.Models) }
            )
        }

        composable<Route.Models> {
            ModelsScreen(
                onMarketplaceClick = { navController.navigate(Route.ModelMarketplace) }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Route.ModelMarketplace> {
            ModelMarketplaceScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
