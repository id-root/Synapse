package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.synapse.lantransfer.ui.theme.CurtainColor

/**
 * Samsung One UI "Now Bar" style curtain reveal transition.
 *
 * When [isOpen] transitions to true:
 *   - Two opaque panels cover the full screen (closed position)
 *   - They part outward (left slides left, right slides right) to reveal [content]
 *
 * When [isOpen] transitions to false:
 *   - Panels slide inward to close, then [onFullyClosed] fires.
 *
 * Duration: ~400ms, FastOutSlowIn easing.
 */
@Composable
fun CurtainTransition(
    isOpen: Boolean,
    onFullyClosed: () -> Unit,
    content: @Composable () -> Unit
) {
    // Whether we are currently rendering the curtain+content at all
    var isComposed by remember { mutableStateOf(false) }

    // 0 = curtain closed (panels covering screen), 1 = curtain open (panels off-screen)
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            isComposed = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        } else if (isComposed) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
            isComposed = false
            onFullyClosed()
        }
    }

    if (isComposed) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val halfWidthPx = with(LocalDensity.current) { maxWidth.toPx() / 2f }

            // Destination content — composed underneath the curtain panels
            content()

            // Left curtain panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth / 2)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = -progress.value * halfWidthPx
                    }
                    .background(CurtainColor)
            )

            // Right curtain panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth / 2)
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationX = progress.value * halfWidthPx
                    }
                    .background(CurtainColor)
            )
        }
    }
}
