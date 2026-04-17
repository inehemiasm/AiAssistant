package com.neo.chevere.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neo.chevere.ui.chat.ChatScreen
import com.neo.chevere.ui.marketplace.MarketplaceViewModel
import com.neo.chevere.ui.marketplace.ModelMarketplaceScreen
import com.neo.chevere.ui.marketplace.details.ModelDetailsScreen
import com.neo.chevere.ui.settings.SettingsScreen

@Composable
fun ChevereNavHost(
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
                onModelsClick = { navController.navigate(Route.ModelMarketplace) }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Route.ModelMarketplace> {
            val viewModel: MarketplaceViewModel = hiltViewModel()
            ModelMarketplaceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onModelClick = { modelId -> navController.navigate(Route.ModelDetails(modelId)) }
            )
        }

        composable<Route.ModelDetails> {
            ModelDetailsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
