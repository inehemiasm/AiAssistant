package com.neo.chevere.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neo.chevere.ui.chat.components.ModelInitializationScreen

@Composable
fun LaunchAnimatedApp(
    isInitializing: Boolean,
    statusMessage: String,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = isInitializing,
            exit = fadeOut(animationSpec = tween(durationMillis = 800))
        ) {
            ModelInitializationScreen(
                statusMessage = statusMessage,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
