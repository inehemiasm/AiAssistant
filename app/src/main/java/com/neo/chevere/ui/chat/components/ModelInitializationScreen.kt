package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.ui.designsystem.Typography

@Composable
fun ModelInitializationScreen(
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    // Background: Deep Blue to Vibrant Cyan with a hint of Peach (matching the image's energy)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D47A1), // Deep Blue
            Color(0xFF00B8D4), // Bright Cyan
            Color(0xFFE0F7FA), // Very Light Blue/White
            Color(0xFFFFCCBC)  // Light Peach (from the light trails)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic "Light Trails" (Simulating the fast-moving background)
        DynamicLightTrails(infiniteTransition)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Robot Illustration (Floating)
            RobotIcon(size = 240.dp)

            Spacer(modifier = Modifier.height(40.dp))

            // App Name with high-tech feel
            Text(
                text = "Chevere",
                style = Typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 58.sp,
                    letterSpacing = (-2).sp
                ),
                textAlign = TextAlign.Center
            )

            // Subtitle
            Text(
                text = "INTELLIGENT COMPANION",
                style = Typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Animated Loading Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                val progressAnimation by infiniteTransition.animateFloat(
                    initialValue = -0.5f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "progress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val barWidth = width * 0.4f
                    val x = progressAnimation * width
                    
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF00E5FF), Color.White, Color(0xFF00E5FF), Color.Transparent)
                        ),
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = statusMessage.uppercase(),
                    style = Typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun DynamicLightTrails(infiniteTransition: InfiniteTransition) {
    val moveFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "move_factor"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Colors from image
        val cyan = Color(0xFF00E5FF)
        val peach = Color(0xFFFFCCBC)
        val white = Color.White

        // Draw multiple streaks
        for (i in 0..10) {
            val startY = (h * (i / 10f))
            val xOffset = (moveFactor * w * 2) - w
            val currentX = (xOffset + (i * 150)) % (w * 1.5f) - (w * 0.25f)
            
            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.5f to (if (i % 2 == 0) cyan else peach).copy(alpha = 0.4f),
                    1.0f to Color.Transparent
                ),
                start = Offset(currentX, startY),
                end = Offset(currentX + 300f, startY + 50f),
                strokeWidth = 4f
            )
        }
        
        // Background Bokeh
        drawCircle(cyan.copy(alpha = 0.1f), 100f, Offset(w * 0.2f, h * 0.3f))
        drawCircle(peach.copy(alpha = 0.1f), 150f, Offset(w * 0.8f, h * 0.7f))
        drawCircle(white.copy(alpha = 0.1f), 80f, Offset(w * 0.5f, h * 0.5f))
    }
}

private val SineHighlightEasing = Easing { fraction ->
    kotlin.math.sin(fraction * kotlin.math.PI.toFloat())
}
