package com.neo.chevere.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    
    // Background Gradient: Orange -> White -> Teal -> Yellow (matching the user's image)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFCCBC), // Light Orange/Peach
            Color(0xFFE0F7FA), // Light Cyan
            Color(0xFF80CBC4), // Teal
            Color(0xFFFFF59D)  // Light Yellow
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Robot Illustration Container (Centered)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                RobotIcon(size = 140.dp)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // App Name
            Text(
                text = "Chevere",
                style = Typography.displaySmall.copy(
                    color = Color(0xFF00332E),
                    fontWeight = FontWeight.Black,
                    fontSize = 54.sp
                ),
                textAlign = TextAlign.Center
            )

            // Subtitle
            Text(
                text = "YOUR INTELLIGENT COMPANION",
                style = Typography.labelSmall.copy(
                    color = Color(0xFF00695C).copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Progress Bar (Thin line)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                val progressAnimation by infiniteTransition.animateFloat(
                    initialValue = -0.3f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "progress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val progressWidth = width * 0.4f
                    val x = progressAnimation * width
                    
                    drawRect(
                        color = Color(0xFFFFD54F),
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(progressWidth, size.height)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Initializing Status Message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = Color(0xFF00695C).copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Text(
                    text = statusMessage.uppercase(),
                    style = Typography.labelSmall.copy(
                        color = Color(0xFF00695C).copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}
