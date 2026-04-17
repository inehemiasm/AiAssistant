package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.*

@Composable
fun ModelInitializationScreen(
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val isDark = isSystemInDarkTheme()
    
    // Theme-aware Background Brush
    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                AstroDeepBlue,
                AstroBrightCyan,
                Color(0xFF002F6C), // Deep blue variant
                Color(0xFF00193D)  // Near black blue
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                AstroSoftCyan,
                AstroPeach,
                Color.White,
                AstroSoftCyan
            )
        )
    }

    val textColor = if (isDark) AstroRobotWhite else AstroDeepBlue
    val subtitleColor = if (isDark) AstroRobotWhite.copy(alpha = 0.7f) else AstroDeepBlue.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        DynamicLightTrails(infiniteTransition)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            RobotIcon(size = 240.dp)

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.splash_app_name),
                style = Typography.displaySmall.copy(
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 58.sp,
                    letterSpacing = (-2).sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.splash_subtitle),
                style = Typography.labelSmall.copy(
                    color = subtitleColor,
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
                    .background(textColor.copy(alpha = 0.2f))
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
                            listOf(Color.Transparent, AstroGlowCyan, textColor, AstroGlowCyan, Color.Transparent)
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
                    tint = subtitleColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = statusMessage.uppercase(),
                    style = Typography.labelSmall.copy(
                        color = subtitleColor,
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
    val isDark = isSystemInDarkTheme()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for (i in 0..10) {
            val startY = (h * (i / 10f))
            val xOffset = (moveFactor * w * 2) - w
            val currentX = (xOffset + (i * 150)) % (w * 1.5f) - (w * 0.25f)
            
            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.5f to (if (i % 2 == 0) AstroGlowCyan else AstroPeach).copy(alpha = if (isDark) 0.4f else 0.2f),
                    1.0f to Color.Transparent
                ),
                start = Offset(currentX, startY),
                end = Offset(currentX + 300f, startY + 50f),
                strokeWidth = 4f
            )
        }
    }
}
