package com.neo.chevere.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neo.chevere.ui.chat.components.ModelInitializationScreen
import kotlinx.coroutines.delay

@Composable
fun LaunchAnimatedApp(
    content: @Composable () -> Unit
) {
    // This splash screen acts as the initial "intro".
    // It will fade out after a short duration to reveal the main app.
    // If the model is still loading, the ChatScreen will continue showing 
    // the same initialization UI for a seamless transition.
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The real app content stays underneath
        content()

        // Overlay intro splash screen
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 800))
        ) {
            ModelInitializationScreen(
                statusMessage = "INITIALIZING...",
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }
}
