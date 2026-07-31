package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.synapse.lantransfer.ui.theme.GlassBorder

enum class GlassIntensity {
    Light, Medium, Strong
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    intensity: GlassIntensity = GlassIntensity.Medium,
    animated: Boolean = true,
    glowColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val bgOpacity = when (intensity) {
        GlassIntensity.Strong -> 0.65f
        GlassIntensity.Medium -> 0.40f
        GlassIntensity.Light -> 0.25f
    }

    val backgroundColor = Color.White.copy(alpha = bgOpacity)

    val shimmerTranslate = if (animated) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val translate by infiniteTransition.animateFloat(
            initialValue = -500f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_translate"
        )
        translate
    } else {
        0f
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.3f)
        ),
        start = Offset(0f, 0f),
        end = Offset(100f, 100f)
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
    ) {
        if (glowColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(glowColor.copy(alpha = 0.2f))
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(28.dp))
                .background(backgroundColor)
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Box(modifier = Modifier.matchParentSize().background(borderBrush, alpha = 0.1f))

            if (animated) {
                val shimmerBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startX = shimmerTranslate,
                    endX = shimmerTranslate + 400f
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(shimmerBrush)
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp)),
            content = content
        )
    }
}
