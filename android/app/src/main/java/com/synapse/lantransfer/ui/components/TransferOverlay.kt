package com.synapse.lantransfer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.synapse.lantransfer.ui.theme.*

@Composable
fun TransferOverlay(
    fileName: String,
    progress: Float,
    transferredBytes: String,
    totalBytes: String,
    speed: String,
    isVisible: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BgElevatedSolid, BgCardSolid)
                    )
                )
                .border(1.dp, GlassBorder, shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Transferring...",
                        style = SynapseTypography.displaySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = fileName,
                        style = SynapseTypography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = TextMuted
                    )
                }
            }

            // Circular progress
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(80.dp),
                    color = Accent1,
                    trackColor = AccentSubtle,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = SynapseTypography.titleLarge,
                    color = Accent1
                )
            }

            // Linear progress
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Accent1,
                trackColor = AccentSubtle
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$transferredBytes / $totalBytes",
                    style = SynapseTypography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = speed,
                    style = SynapseTypography.labelSmall,
                    color = Accent1
                )
            }
        }
    }
}
