package com.neo.chevere.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.telemetry.TelemetryConstants

@Composable
fun ChevereApp(
    telemetry: AppTelemetry
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen = currentDestination.telemetryScreen()
    val currentProductArea = currentDestination.telemetryProductArea()

    LaunchedEffect(currentScreen) {
        telemetry.logScreenViewed(
            screenName = currentScreen,
            productArea = currentProductArea
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp,
                modifier = Modifier.height(86.dp)
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            telemetry.logClick(
                                action = destination.telemetryAction,
                                screenName = currentScreen,
                                productArea = currentProductArea
                            )
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelResId),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelResId),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.6.sp
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            ChevereNavHost(
                navController = navController
            )
        }
    }
}

private fun androidx.navigation.NavDestination?.telemetryScreen(): String = when {
    this?.hierarchy?.any { it.hasRoute(Route.Chat::class) } == true -> TelemetryConstants.Screen.CHAT
    this?.hierarchy?.any { it.hasRoute(Route.ModelMarketplace::class) } == true -> TelemetryConstants.Screen.MODEL_MARKETPLACE
    this?.hierarchy?.any { it.hasRoute(Route.ModelDetails::class) } == true -> TelemetryConstants.Screen.MODEL_DETAILS
    this?.hierarchy?.any { it.hasRoute(Route.Settings::class) } == true -> TelemetryConstants.Screen.SETTINGS
    else -> TelemetryConstants.Screen.UNKNOWN
}

private fun androidx.navigation.NavDestination?.telemetryProductArea(): String = when (telemetryScreen()) {
    TelemetryConstants.Screen.MODEL_MARKETPLACE,
    TelemetryConstants.Screen.MODEL_DETAILS -> TelemetryConstants.ProductArea.MODEL_MANAGEMENT
    TelemetryConstants.Screen.SETTINGS -> TelemetryConstants.ProductArea.SETTINGS
    else -> TelemetryConstants.ProductArea.CHAT
}
