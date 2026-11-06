package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CyanMint = Color(0xFF00F5D4)
private val ElectricSky = Color(0xFF00BBF9)
private val IndigoRoyal = Color(0xFF4361EE)
private val VisorObsidian = Color(0xFF080D1F)

/**
 * Animated "C-Bot" emblem matching the launcher icon: a geometric "C" robotic chassis
 * with a rich aqua-to-indigo gradient, top antenna node, side sensor ear, and glowing
 * optical pill eyes inside a dark recessed visor.
 */
@Composable
fun RobotIcon(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "c_robot_animation")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = SineHighlightEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    val eyeBlinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 2900
                0.15f at 3050
                1f at 3200
                1f at 3600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val scale = w / 108f

        withTransform({
            translate(top = floatingOffset)
        }) {
            // 1. Ambient outer aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricSky.copy(alpha = 0.32f * glowAlpha),
                        IndigoRoyal.copy(alpha = 0.14f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.52f, h * 0.52f),
                    radius = w * 0.46f
                ),
                center = Offset(w * 0.52f, h * 0.52f),
                radius = w * 0.46f
            )

            // 2. Top robotic antenna stem & glowing orb
            drawLine(
                color = ElectricSky,
                start = Offset(54f * scale, 19f * scale),
                end = Offset(54f * scale, 28f * scale),
                strokeWidth = 3.4f * scale,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = CyanMint.copy(alpha = glowAlpha),
                radius = 5.2f * scale,
                center = Offset(54f * scale, 17.5f * scale)
            )
            drawCircle(
                color = Color.White,
                radius = 2.2f * scale,
                center = Offset(54f * scale, 17.5f * scale)
            )

            // 3. Left sensor ear module
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(CyanMint, ElectricSky, IndigoRoyal),
                    startY = 43f * scale,
                    endY = 65f * scale
                ),
                topLeft = Offset(22.5f * scale, 43f * scale),
                size = Size(7f * scale, 22f * scale),
                cornerRadius = CornerRadius(4f * scale, 4f * scale)
            )

            // 4. Recessed dark obsidian visor
            drawRoundRect(
                color = VisorObsidian,
                topLeft = Offset(36f * scale, 35f * scale),
                size = Size(44f * scale, 38f * scale),
                cornerRadius = CornerRadius(18f * scale, 18f * scale)
            )
            drawRoundRect(
                color = ElectricSky.copy(alpha = 0.35f * glowAlpha),
                topLeft = Offset(36f * scale, 35f * scale),
                size = Size(44f * scale, 38f * scale),
                cornerRadius = CornerRadius(18f * scale, 18f * scale),
                style = Stroke(width = 1.3f * scale)
            )

            // 5. Bold geometric "C" Robot Helmet Chassis with Cyan -> Sky -> Indigo gradient
            val cArcTopLeft = Offset(29.5f * scale, 29.5f * scale)
            val cArcSize = Size(49f * scale, 49f * scale)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(CyanMint, ElectricSky, IndigoRoyal),
                    start = Offset(28f * scale, 28f * scale),
                    end = Offset(78f * scale, 80f * scale)
                ),
                startAngle = 45f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = cArcTopLeft,
                size = cArcSize,
                style = Stroke(width = 11.5f * scale, cap = StrokeCap.Round)
            )

            // Subtle inner specular highlight along the "C" arc
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = 50f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(32f * scale, 32f * scale),
                size = Size(44f * scale, 44f * scale),
                style = Stroke(width = 1.5f * scale, cap = StrokeCap.Round)
            )

            // 6. Glowing optical pill eyes inside the visor
            val eyeWidth = 8f * scale
            val fullEyeHeight = 15f * scale
            val currentEyeHeight = (fullEyeHeight * eyeBlinkScale).coerceAtLeast(3f * scale)
            val eyeCenterY = 54f * scale
            val eyeTop = eyeCenterY - currentEyeHeight / 2f
            val eyeBrush = Brush.verticalGradient(
                colors = listOf(CyanMint, ElectricSky),
                startY = eyeTop,
                endY = eyeTop + currentEyeHeight
            )

            // Left pill eye
            drawRoundRect(
                brush = eyeBrush,
                topLeft = Offset(49f * scale, eyeTop),
                size = Size(eyeWidth, currentEyeHeight),
                cornerRadius = CornerRadius(4f * scale, 4f * scale)
            )
            // Right pill eye
            drawRoundRect(
                brush = eyeBrush,
                topLeft = Offset(63f * scale, eyeTop),
                size = Size(eyeWidth, currentEyeHeight),
                cornerRadius = CornerRadius(4f * scale, 4f * scale)
            )

            if (eyeBlinkScale > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 1.3f * scale,
                    center = Offset(51.8f * scale, 49.8f * scale)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 1.3f * scale,
                    center = Offset(65.8f * scale, 49.8f * scale)
                )
            }
        }
    }
}

private val SineHighlightEasing = Easing { fraction ->
    kotlin.math.sin(fraction * kotlin.math.PI.toFloat())
}
