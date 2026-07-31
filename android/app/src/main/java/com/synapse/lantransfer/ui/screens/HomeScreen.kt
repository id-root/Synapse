package com.synapse.lantransfer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.synapse.lantransfer.ui.components.PigeonLogo
import com.synapse.lantransfer.ui.components.StackedSwipeComponent
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onHotspotShareClick: () -> Unit
) {
    // Typewriter effect for hero text
    val fullText = "Hey, what do you\nwant to share?"
    var animatedText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        animatedText = ""
        fullText.forEach { char ->
            animatedText += char
            delay(40) // speed of typing
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hamburger menu → opens History drawer
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = if (isDarkTheme) TextPrimaryDark else TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Settings label (center)
            Text(
                text = "Settings",
                style = SynapseTypography.titleMedium,
                color = if (isDarkTheme) TextPrimaryDark else TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSettingsClick() }
            )

            // Wi-Fi Direct / Local Hotspot trigger
            IconButton(onClick = onHotspotShareClick) {
                HotspotWifiIcon(
                    tintColor = if (isDarkTheme) TextPrimaryDark else TextPrimary
                )
            }
        }

        // ── Hero Section (centered) ──
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pigeon logo — decorative, animated (Larger Size)
            PigeonLogo(
                logoSize = 96.dp,
                enableAnimation = true
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Hero text with Typewriter animation (Larger Size)
            Text(
                text = animatedText,
                style = SynapseTypography.displayLarge.copy(
                    fontSize = 36.sp,
                    lineHeight = 46.sp
                ),
                color = if (isDarkTheme) TextPrimaryDark else TextPrimary,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }

        // ── Bottom Pill - Send/Receive ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            StackedSwipeComponent(
                isDarkTheme = isDarkTheme,
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick
            )
        }
    }
}

@Composable
fun HotspotWifiIcon(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        // Red center dot from user's upload image
        drawCircle(
            color = Color(0xFFFF1744),
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.8f)
        )

        val strokeWidth = w * 0.08f

        // First arc
        drawArc(
            color = tintColor,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.3f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Second arc
        drawArc(
            color = tintColor,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Third arc
        drawArc(
            color = tintColor,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.9f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
