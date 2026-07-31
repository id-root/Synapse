package com.synapse.lantransfer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.delay

enum class ToastType(val icon: ImageVector, val bgColor: Color, val borderColor: Color) {
    SUCCESS(Icons.Rounded.CheckCircle, SuccessSubtle, Success),
    ERROR(Icons.Rounded.Error, DangerSubtle, Danger),
    WARNING(Icons.Rounded.Warning, Color(0x1EEAB308), Warning),
    INFO(Icons.Rounded.Info, InfoSubtle, Info)
}

@Composable
fun SynapseToast(
    message: String,
    type: ToastType,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 3000L
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(shape)
                .background(type.bgColor)
                .border(1.dp, type.borderColor.copy(alpha = 0.3f), shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = type.borderColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = SynapseTypography.bodyLarge,
                color = TextPrimary
            )
        }
    }
}
