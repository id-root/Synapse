package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.synapse.lantransfer.ui.theme.*
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
    val isDark = MaterialTheme.colorScheme.primary == Accent1Dark

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

    // Recolor orbs dynamically based on theme (light blue for light mode, dark neon blue/indigo for dark mode)
    val orbs = remember(isDark) {
        if (isDark) {
            listOf(
                FloatingOrb(0.2f, 0.3f, 180f, Color(0xFF1E3A8A).copy(alpha = 0.12f), 1.0f, 0f),
                FloatingOrb(0.7f, 0.2f, 220f, Color(0xFF0F172A).copy(alpha = 0.1f), 0.7f, 90f),
                FloatingOrb(0.5f, 0.7f, 200f, Color(0xFF1E293B).copy(alpha = 0.08f), 0.8f, 180f),
                FloatingOrb(0.8f, 0.6f, 160f, Color(0xFF1E3A8A).copy(alpha = 0.1f), 1.2f, 270f),
                FloatingOrb(0.3f, 0.8f, 190f, Color(0xFF0F172A).copy(alpha = 0.08f), 0.9f, 45f)
            )
        } else {
            listOf(
                FloatingOrb(0.2f, 0.3f, 180f, Color(0xFF90CAF9).copy(alpha = 0.08f), 1.0f, 0f),
                FloatingOrb(0.7f, 0.2f, 220f, Color(0xFFBBDEFB).copy(alpha = 0.06f), 0.7f, 90f),
                FloatingOrb(0.5f, 0.7f, 200f, Color(0xFFE3F2FD).copy(alpha = 0.07f), 0.8f, 180f),
                FloatingOrb(0.8f, 0.6f, 160f, Color(0xFF90CAF9).copy(alpha = 0.05f), 1.2f, 270f),
                FloatingOrb(0.3f, 0.8f, 190f, Color(0xFFBBDEFB).copy(alpha = 0.06f), 0.9f, 45f)
            )
        }
    }

    val bgTop = if (isDark) HomeBgTopDark else HomeBgTop
    val bgMid = if (isDark) HomeBgMidDark else HomeBgMid
    val bgBottom = if (isDark) HomeBgBottomDark else HomeBgBottom

    Canvas(modifier = modifier.fillMaxSize()) {
        // Base gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    bgTop,
                    bgMid,
                    bgBottom
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
