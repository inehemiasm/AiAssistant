package com.neo.aiassistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LaunchAnimatedApp(
    content: @Composable () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The real app content stays underneath
        content()

        // Overlay splash screen
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 400))
        ) {
            RobotWinkSplash(
                onAnimationFinished = { showSplash = false }
            )
        }
    }
}

@Composable
fun RobotWinkSplash(
    onAnimationFinished: () -> Unit
) {
    var isWinking by remember { mutableStateOf(false) }
    
    // Total duration ~1000ms
    LaunchedEffect(Unit) {
        delay(300) // Initial delay before wink
        isWinking = true
        delay(200) // Wink duration
        isWinking = false
        delay(500) // Post-wink pause
        onAnimationFinished()
    }

    val eyeScale by animateFloatAsState(
        targetValue = if (isWinking) 0.1f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "EyeWink"
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    val eyeColor = Color(0xFF00E5FF) // Cyan glow matching avd_wink

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Robot Head (Rounded Rectangle)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(canvasWidth * 0.15f, canvasHeight * 0.2f),
                size = Size(canvasWidth * 0.7f, canvasHeight * 0.65f),
                cornerRadius = CornerRadius(40f, 40f)
            )

            // Visor (Dark Gray)
            drawRoundRect(
                color = Color(0xFF1A1C1E),
                topLeft = Offset(canvasWidth * 0.25f, canvasHeight * 0.35f),
                size = Size(canvasWidth * 0.5f, canvasHeight * 0.25f),
                cornerRadius = CornerRadius(20f, 20f)
            )

            // Left Eye (Steady)
            drawCircle(
                color = eyeColor,
                radius = 12f,
                center = Offset(canvasWidth * 0.4f, canvasHeight * 0.47f)
            )

            // Right Eye (Winking)
            val rightEyeCenter = Offset(canvasWidth * 0.6f, canvasHeight * 0.47f)
            if (isWinking) {
                // Draw a line for closed eye
                drawLine(
                    color = eyeColor,
                    start = Offset(rightEyeCenter.x - 12f, rightEyeCenter.y),
                    end = Offset(rightEyeCenter.x + 12f, rightEyeCenter.y),
                    strokeWidth = 6f
                )
            } else {
                drawCircle(
                    color = eyeColor,
                    radius = 12f * eyeScale,
                    center = rightEyeCenter
                )
            }

            // Smile
            drawArc(
                color = Color(0xFFFFAB40), // Orange matching avd_wink
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(canvasWidth * 0.4f, canvasHeight * 0.65f),
                size = Size(canvasWidth * 0.2f, canvasHeight * 0.1f),
                style = Stroke(width = 8f)
            )
        }
    }
}
