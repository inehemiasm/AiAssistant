package com.neo.chevere.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.neo.chevere.BuildConfig
import com.neo.chevere.ui.chat.ChatScreen
import com.neo.chevere.ui.chat.ChatViewModel
import com.neo.chevere.ui.marketplace.MarketplaceViewModel
import com.neo.chevere.ui.marketplace.ModelMarketplaceScreen
import com.neo.chevere.ui.marketplace.details.ModelDetailsScreen
import com.neo.chevere.ui.purchases.PurchaseSandboxScreen
import com.neo.chevere.ui.radar.SensorsRadarScreen
import com.neo.chevere.ui.settings.BenchmarkScreen
import com.neo.chevere.ui.settings.SettingsScreen
import com.neo.chevere.ui.tasks.TasksScreen

@Composable
fun ChevereNavHost(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val purchaseSandboxEnabled = BuildConfig.DEBUG && BuildConfig.REVENUECAT_SDK_KEY.startsWith("test_")
    NavHost(
        navController = navController,
        startDestination = Route.Chat,
        modifier = modifier
    ) {
        composable<Route.Chat> {
            ChatScreen(
                viewModel = chatViewModel,
                onSettingsClick = { navController.navigate(Route.Settings) },
                onModelsClick = { navController.navigate(Route.ModelMarketplace) },
                onRadarClick = { mode -> navController.navigate(Route.SensorRadar(mode = mode)) }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onBenchmarkClick = { navController.navigate(Route.Benchmark) },
                onRadarClick = { navController.navigate(Route.SensorRadar(mode = "all")) },
                onPurchaseSandboxClick = if (purchaseSandboxEnabled) {
                    { navController.navigate(Route.PurchaseSandbox) }
                } else {
                    null
                }
            )
        }

        composable<Route.PurchaseSandbox> {
            PurchaseSandboxScreen(onBack = { navController.popBackStack() })
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

        composable<Route.Tasks> {
            TasksScreen()
        }

        composable<Route.Invoices> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(Route.Chat) {
                    popUpTo<Route.Invoices> { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable<Route.Benchmark> {
            BenchmarkScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Route.SensorRadar>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "chevere://sensor-radar" }
            )
        ) {
            SensorsRadarScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
