package com.synapse.lantransfer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.ui.components.PigeonLogo
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800),
        label = "logoAlpha"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "textAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgVoid),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PigeonLogo(
                logoSize = 120.dp,
                enableAnimation = true,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Synapse",
                style = SynapseTypography.displayLarge,
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure LAN File Transfer",
                style = SynapseTypography.bodyLarge,
                color = TextMuted,
                modifier = Modifier.alpha(textAlpha)
            )
        }
    }
}
