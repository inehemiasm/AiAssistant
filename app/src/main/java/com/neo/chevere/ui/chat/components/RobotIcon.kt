package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neo.chevere.ui.designsystem.*

@Composable
fun RobotIcon(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robot_animation")
    val isDark = isSystemInDarkTheme()
    
    // Theme-aware colors
    val robotWhite = if (isDark) AstroRobotWhite else Color(0xFFE3F2FD)
    val robotGray = if (isDark) AstroRobotGray else Color(0xFFBBDEFB)
    val robotDark = if (isDark) AstroRobotDark else Color(0xFF0D47A1)
    val glowColor = if (isDark) AstroGlowCyan else Color(0xFF1E88E5)

    // Pulsing effect for glowing elements
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Floating/Gliding animation
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = SineHighlightEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    // Winking logic
    val eyeWinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 2800
                0.1f at 2950
                1f at 3100
                1f at 3500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "wink"
    )

    // Waving animation for the hand
    val waveRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        
        withTransform({
            translate(top = floatingOffset)
        }) {
            // --- 1. Background Glow (Portal effect) ---
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to glowColor.copy(alpha = 0.3f * glowAlpha),
                    1.0f to Color.Transparent,
                    center = Offset(w / 2, h * 0.75f),
                    radius = w * 0.4f
                ),
                center = Offset(w / 2, h * 0.75f),
                radius = w * 0.4f
            )

            // --- 2. Legs ---
            drawRoundRect(
                color = robotWhite,
                topLeft = Offset(w * 0.35f, h * 0.75f),
                size = Size(w * 0.12f, h * 0.15f),
                cornerRadius = CornerRadius(20f, 20f)
            )
            drawRoundRect(
                color = robotWhite,
                topLeft = Offset(w * 0.53f, h * 0.75f),
                size = Size(w * 0.12f, h * 0.15f),
                cornerRadius = CornerRadius(20f, 20f)
            )
            // Glowing soles
            drawOval(
                color = glowColor.copy(alpha = glowAlpha),
                topLeft = Offset(w * 0.35f, h * 0.88f),
                size = Size(w * 0.12f, 10f)
            )
            drawOval(
                color = glowColor.copy(alpha = glowAlpha),
                topLeft = Offset(w * 0.53f, h * 0.88f),
                size = Size(w * 0.12f, 10f)
            )

            // --- 3. Arms ---
            drawRoundRect(
                color = robotWhite,
                topLeft = Offset(w * 0.65f, h * 0.55f),
                size = Size(w * 0.1f, h * 0.15f),
                cornerRadius = CornerRadius(20f, 20f)
            )

            withTransform({
                rotate(waveRotation, pivot = Offset(w * 0.25f, h * 0.55f))
            }) {
                drawRoundRect(
                    color = robotWhite,
                    topLeft = Offset(w * 0.1f, h * 0.4f),
                    size = Size(w * 0.15f, h * 0.2f),
                    cornerRadius = CornerRadius(30f, 30f)
                )
                drawCircle(
                    color = if (isDark) Color(0xFF424242) else Color(0xFF1565C0),
                    radius = w * 0.06f,
                    center = Offset(w * 0.15f, h * 0.38f)
                )
            }

            // --- 4. Body ---
            val bodyCenter = Offset(w / 2, h * 0.65f)
            val bodyWidth = w * 0.42f
            val bodyHeight = h * 0.32f
            
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(robotWhite, robotGray),
                    start = Offset(bodyCenter.x, bodyCenter.y - bodyHeight / 2),
                    end = Offset(bodyCenter.x, bodyCenter.y + bodyHeight / 2)
                ),
                topLeft = Offset(bodyCenter.x - bodyWidth / 2, bodyCenter.y - bodyHeight / 2),
                size = Size(bodyWidth, bodyHeight)
            )

            drawHeart(Offset(bodyCenter.x, bodyCenter.y - 10f), 18f, glowColor.copy(alpha = glowAlpha))
            drawHeart(Offset(bodyCenter.x, bodyCenter.y - 10f), 10f, robotWhite.copy(alpha = glowAlpha))

            // --- 5. Antennas ---
            drawLine(robotGray, Offset(w * 0.38f, h * 0.25f), Offset(w * 0.3f, h * 0.12f), 5f)
            drawCircle(glowColor.copy(alpha = glowAlpha), 10f, Offset(w * 0.3f, h * 0.12f))
            drawLine(robotGray, Offset(w * 0.62f, h * 0.25f), Offset(w * 0.7f, h * 0.12f), 5f)
            drawCircle(glowColor.copy(alpha = glowAlpha), 10f, Offset(w * 0.7f, h * 0.12f))

            // --- 6. Head ---
            val headWidth = w * 0.65f
            val headHeight = h * 0.45f
            val headCenter = Offset(w / 2, h * 0.38f)

            drawRoundRect(
                color = robotWhite,
                topLeft = Offset(headCenter.x - headWidth / 2, headCenter.y - headHeight / 2),
                size = Size(headWidth, headHeight),
                cornerRadius = CornerRadius(100f, 100f)
            )
            
            drawRoundRect(AstroAccentBlue, Offset(headCenter.x - headWidth / 2 - 5f, headCenter.y - 20f), Size(15f, 50f), CornerRadius(10f, 10f))
            drawRoundRect(AstroAccentBlue, Offset(headCenter.x + headWidth / 2 - 10f, headCenter.y - 20f), Size(15f, 50f), CornerRadius(10f, 10f))

            val faceWidth = headWidth * 0.85f
            val faceHeight = headHeight * 0.7f
            drawRoundRect(
                color = robotDark,
                topLeft = Offset(headCenter.x - faceWidth / 2, headCenter.y - faceHeight / 2 + 15f),
                size = Size(faceWidth, faceHeight),
                cornerRadius = CornerRadius(80f, 80f)
            )

            // --- 7. Eyes & Mouth ---
            val eyeSize = 32f
            val eyeY = headCenter.y + 15f
            
            drawCircle(glowColor.copy(alpha = glowAlpha), eyeSize, Offset(headCenter.x - faceWidth * 0.25f, eyeY))
            drawCircle(robotWhite.copy(alpha = 0.8f), eyeSize * 0.4f, Offset(headCenter.x - faceWidth * 0.25f - 6f, eyeY - 6f))

            if (eyeWinkScale > 0.4f) {
                drawCircle(glowColor.copy(alpha = glowAlpha), eyeSize * eyeWinkScale, Offset(headCenter.x + faceWidth * 0.25f, eyeY))
            } else {
                drawArc(
                    color = glowColor.copy(alpha = glowAlpha),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(headCenter.x + faceWidth * 0.25f - eyeSize, eyeY - 5f),
                    size = Size(eyeSize * 2, 10f),
                    style = Stroke(6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            drawArc(
                color = glowColor.copy(alpha = glowAlpha),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(headCenter.x - 10f, eyeY + 40f),
                size = Size(20f, 12f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        val width = size * 2
        val height = size * 2
        val x = center.x - size
        val y = center.y - size
        moveTo(x + width / 2, y + height / 4)
        cubicTo(x + width / 2, y, x, y, x, y + height / 4)
        cubicTo(x, y + height / 2, x + width / 2, y + height * 0.75f, x + width / 2, y + height)
        cubicTo(x + width / 2, y + height * 0.75f, x + width, y + height / 2, x + width, y + height / 4)
        cubicTo(x + width, y, x + width / 2, y, x + width / 2, y + height / 4)
        close()
    }
    drawPath(path, color)
}

private val SineHighlightEasing = Easing { fraction ->
    kotlin.math.sin(fraction * kotlin.math.PI.toFloat())
}
