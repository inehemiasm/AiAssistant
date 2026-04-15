package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RobotIcon(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robot_animation")
    
    // Pulsing effect for the glowing parts
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        
        // Colors from image
        val robotBlue = Color(0xFF217896)
        val robotDarkBlue = Color(0xFF164D61)
        val visorOrange = Color(0xFFFFB300)
        val chestCyan = Color(0xFF00E5FF)
        val accentYellow = Color(0xFFFFD54F)

        // 1. Robot Head
        val headWidth = w * 0.45f
        val headHeight = h * 0.35f
        val headLeft = (w - headWidth) / 2
        val headTop = h * 0.15f
        
        drawRoundRect(
            color = robotBlue,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headWidth * 0.3f)
        )
        
        // 2. Visor (Orange Glow)
        val visorWidth = headWidth * 0.7f
        val visorHeight = headHeight * 0.3f
        val visorLeft = headLeft + (headWidth - visorWidth) / 2
        val visorTop = headTop + headHeight * 0.25f
        
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.8f),
            topLeft = Offset(visorLeft, visorTop),
            size = Size(visorWidth, visorHeight),
            cornerRadius = CornerRadius(visorHeight * 0.4f)
        )
        
        drawRoundRect(
            color = visorOrange.copy(alpha = glowAlpha),
            topLeft = Offset(visorLeft + 4f, visorTop + 4f),
            size = Size(visorWidth - 8f, visorHeight - 8f),
            cornerRadius = CornerRadius(visorHeight * 0.3f)
        )

        // 3. Neck
        val neckWidth = headWidth * 0.4f
        val neckHeight = h * 0.05f
        drawRect(
            color = robotDarkBlue,
            topLeft = Offset((w - neckWidth) / 2, headTop + headHeight - 5f),
            size = Size(neckWidth, neckHeight)
        )

        // 4. Body/Torso
        val torsoWidth = w * 0.65f
        val torsoHeight = h * 0.45f
        val torsoLeft = (w - torsoWidth) / 2
        val torsoTop = headTop + headHeight + neckHeight - 10f
        
        val torsoPath = Path().apply {
            moveTo(torsoLeft + torsoWidth * 0.2f, torsoTop)
            lineTo(torsoLeft + torsoWidth * 0.8f, torsoTop)
            lineTo(torsoLeft + torsoWidth, torsoTop + torsoHeight)
            lineTo(torsoLeft, torsoTop + torsoHeight)
            close()
        }
        drawPath(path = torsoPath, color = robotBlue)

        // 5. Chest Light (Cyan Circle)
        val chestSize = torsoWidth * 0.25f
        drawCircle(
            color = chestCyan.copy(alpha = glowAlpha),
            radius = chestSize / 2,
            center = Offset(w / 2, torsoTop + torsoHeight * 0.3f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.5f * glowAlpha),
            radius = chestSize / 4,
            center = Offset(w / 2, torsoTop + torsoHeight * 0.3f)
        )
        
        // 6. Shoulders/Arms (Simplified)
        drawCircle(
            color = robotDarkBlue,
            radius = torsoWidth * 0.15f,
            center = Offset(torsoLeft, torsoTop + 20f)
        )
        drawCircle(
            color = robotDarkBlue,
            radius = torsoWidth * 0.15f,
            center = Offset(torsoLeft + torsoWidth, torsoTop + 20f)
        )
        
        // Accent circle on shoulder
        drawCircle(
            color = accentYellow,
            radius = 8f,
            center = Offset(torsoLeft + 15f, torsoTop + 25f)
        )
    }
}
