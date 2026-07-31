package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.synapse.lantransfer.R
import com.synapse.lantransfer.ui.theme.AccentGlow

@Composable
fun PigeonLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 80.dp,
    enableAnimation: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pigeon")

    val wingRotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wingRotation"
    )

    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier.size(logoSize),
        contentAlignment = Alignment.Center
    ) {
        if (enableAnimation) {
            Image(
                painter = painterResource(id = R.drawable.pigeon_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(logoSize * 1.3f)
                    .alpha(glowAlpha * 0.4f)
                    .scale(1.2f),
                colorFilter = ColorFilter.tint(AccentGlow)
            )
        }

        Image(
            painter = painterResource(id = R.drawable.pigeon_logo),
            contentDescription = "Synapse Logo",
            modifier = Modifier
                .size(logoSize)
                .then(
                    if (enableAnimation) {
                        Modifier
                            .rotate(wingRotation)
                            .offset { IntOffset(0, floatOffsetY.toInt()) }
                    } else Modifier
                )
        )
    }
}
