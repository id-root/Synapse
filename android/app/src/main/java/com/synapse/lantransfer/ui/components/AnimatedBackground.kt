package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.synapse.lantransfer.ui.theme.Accent1
import com.synapse.lantransfer.ui.theme.BgVoid
import kotlin.math.cos
import kotlin.math.sin

data class FloatingOrb(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val color: Color,
    val speed: Float,
    val phaseOffset: Float
)

@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val orbs = remember {
        listOf(
            FloatingOrb(0.2f, 0.3f, 180f, Accent1.copy(alpha = 0.08f), 1.0f, 0f),
            FloatingOrb(0.7f, 0.2f, 220f, Color(0xFFD4B896).copy(alpha = 0.06f), 0.7f, 90f),
            FloatingOrb(0.5f, 0.7f, 200f, Color(0xFFE8D5B7).copy(alpha = 0.07f), 0.8f, 180f),
            FloatingOrb(0.8f, 0.6f, 160f, Accent1.copy(alpha = 0.05f), 1.2f, 270f),
            FloatingOrb(0.3f, 0.8f, 190f, Color(0xFFF0E6D3).copy(alpha = 0.06f), 0.9f, 45f)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Base gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    BgVoid,
                    Color(0xFFF5F1E6),
                    Color(0xFFEDE7D9)
                )
            )
        )

        // Floating orbs
        val rad = Math.toRadians(time.toDouble())
        orbs.forEach { orb ->
            val angle = rad * orb.speed + orb.phaseOffset
            val dx = cos(angle).toFloat() * 40f
            val dy = sin(angle * 0.7).toFloat() * 30f
            val cx = orb.baseX * size.width + dx
            val cy = orb.baseY * size.height + dy

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb.color, orb.color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = orb.radius
                ),
                radius = orb.radius,
                center = Offset(cx, cy)
            )
        }
    }
}
