package com.neo.chevere.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.ui.designsystem.Typography

/**
 * A visually engaging loading indicator shown when the AI is "thinking" or processing a request.
 *
 * This component features a pulsing icon and a text label with an animated alpha effect.
 *
 * @param visible Whether the indicator should be shown.
 * @param modifier The modifier to be applied to the container.
 * @param statusMessage Optional custom message to display (e.g., "PLANNING", "EXECUTING").
 *                      Defaults to "THINKING" if null.
 */
@Composable
fun QuantumThinkingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "thinking")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(
                        color = Color.Cyan.copy(alpha = alpha * 0.2f),
                        radius = size.minDimension / 1.5f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = Color.Cyan.copy(alpha = alpha),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = (statusMessage ?: stringResource(R.string.thinking)).uppercase(),
                style = Typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Cyan.copy(alpha = alpha)
                )
            )
        }
    }
}
