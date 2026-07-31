package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.synapse.lantransfer.ui.theme.Accent1
import com.synapse.lantransfer.ui.theme.AccentSubtle
import com.synapse.lantransfer.ui.theme.TextMuted
import kotlin.math.cos
import kotlin.math.sin

data class RadarBlip(
    val angle: Float,
    val distance: Float, // 0..1 from center
    val label: String
)

@Composable
fun RadarDisplay(
    modifier: Modifier = Modifier,
    radarSize: Dp = 280.dp,
    blips: List<RadarBlip> = emptyList()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val blipAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blipPulse"
    )

    Box(
        modifier = modifier.size(radarSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(radarSize)) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2

            // Concentric rings
            for (i in 1..4) {
                val ringRadius = maxRadius * (i / 4f)
                drawCircle(
                    color = TextMuted.copy(alpha = 0.15f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            // Cross hairs
            drawLine(
                color = TextMuted.copy(alpha = 0.1f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1f
            )
            drawLine(
                color = TextMuted.copy(alpha = 0.1f),
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1f
            )

            // Sweep beam
            rotate(sweepAngle, pivot = center) {
                val sweepBrush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.05f to Accent1.copy(alpha = 0.02f),
                    0.1f to Accent1.copy(alpha = 0.15f),
                    0.12f to Accent1.copy(alpha = 0.3f),
                    0.13f to Accent1.copy(alpha = 0.0f),
                    1.0f to Color.Transparent,
                    center = center
                )
                drawCircle(
                    brush = sweepBrush,
                    radius = maxRadius,
                    center = center
                )

                // Sweep line
                drawLine(
                    color = Accent1.copy(alpha = 0.6f),
                    start = center,
                    end = Offset(center.x, center.y - maxRadius),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }

            // Center dot
            drawCircle(
                color = Accent1,
                radius = 4f,
                center = center
            )
            drawCircle(
                color = Accent1.copy(alpha = 0.3f),
                radius = 8f,
                center = center
            )

            // Blips
            blips.forEach { blip ->
                val rad = Math.toRadians(blip.angle.toDouble())
                val bx = center.x + cos(rad).toFloat() * maxRadius * blip.distance
                val by = center.y + sin(rad).toFloat() * maxRadius * blip.distance

                drawCircle(
                    color = Accent1.copy(alpha = blipAlpha * 0.3f),
                    radius = 14f,
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = Accent1.copy(alpha = blipAlpha),
                    radius = 6f,
                    center = Offset(bx, by)
                )
            }
        }
    }
}
