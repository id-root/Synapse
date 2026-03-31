package com.synapse.lantransfer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.ui.components.GlassCard
import com.synapse.lantransfer.ui.components.TransferOverlay
import com.synapse.lantransfer.ui.screens.viewmodel.SendViewModel
import com.synapse.lantransfer.ui.theme.*
import com.synapse.lantransfer.util.formatBytes
import com.synapse.lantransfer.util.formatSpeed

@Composable
fun SendScreen(viewModel: SendViewModel = viewModel()) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isSending by viewModel.isBroadcasting.collectAsState()
    val senderPort by viewModel.senderPort.collectAsState()
    val transferState by viewModel.transferState.collectAsState()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    val scrollState = rememberScrollState()

    // Transfer overlay
    val showOverlay = transferState is TransferState.Sending &&
        (transferState as? TransferState.Sending)?.progress != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Send Files",
                style = SynapseTypography.displayLarge,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = "Select files to share on your local network.",
                style = SynapseTypography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Drop Zone
            val dropScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "dropScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(dropScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { filePickerLauncher.launch(arrayOf("*/*")) }
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Upload icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Accent1, Accent2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap to browse files",
                        style = SynapseTypography.displaySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select files from your device to share",
                        style = SynapseTypography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    // Browse Button
                    Box(
                        modifier = Modifier
                            .background(Brush.linearGradient(listOf(Accent1, Accent2)), RoundedCornerShape(20.dp))
                            .clickable { filePickerLauncher.launch(arrayOf("*/*")) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Browse Files",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Selected Files List
            if (selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ready to Send",
                        style = SynapseTypography.bodyMedium,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .background(AccentSubtle, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${selectedFiles.size} file${if (selectedFiles.size > 1) "s" else ""}",
                            style = SynapseTypography.labelSmall,
                            color = Accent2
                        )
                    }
                }

                selectedFiles.forEachIndexed { index, file ->
                    val enterTransition = remember { Animatable(0f) }
                    LaunchedEffect(file) {
                        enterTransition.animateTo(
                            1f,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = 300f
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .scale(enterTransition.value)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // File icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AccentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = Accent1,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = SynapseTypography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(file.size),
                                    style = SynapseTypography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removeFile(index) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DangerSubtle)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    tint = Danger,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    if (isSending) {
                        // Broadcasting state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Pulse animation
                            val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
                                initialValue = 1f,
                                targetValue = 1.8f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulseScale"
                            )
                            val pulseAlpha by rememberInfiniteTransition(label = "pulseA").animateFloat(
                                initialValue = 0.6f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulseAlpha"
                            )

                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .scale(pulseScale)
                                        .background(
                                            Accent1.copy(alpha = pulseAlpha),
                                            CircleShape
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Accent1, CircleShape)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Broadcasting on LAN",
                                    style = SynapseTypography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Port ${senderPort ?: "—"}",
                                    style = SynapseTypography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopBroadcasting() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Danger
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(Danger.copy(alpha = 0.3f), Danger.copy(alpha = 0.3f)))
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("⏹ Stop", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Ready state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ready to broadcast",
                                style = SynapseTypography.bodyMedium,
                                color = TextSecondary
                            )

                            Button(
                                onClick = { viewModel.startBroadcasting() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Accent1, Accent2)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 22.dp, vertical = 11.dp)
                                ) {
                                    Text(
                                        text = "▶ Start Sending",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Transfer progress overlay
        if (showOverlay) {
            val progress = (transferState as? TransferState.Sending)?.progress
            if (progress != null) {
                TransferOverlay(
                    fileName = progress.fileName,
                    progress = progress.fraction,
                    transferredBytes = formatBytes(progress.bytesTransferred),
                    totalBytes = formatBytes(progress.totalBytes),
                    speed = formatSpeed(progress.speed),
                    isVisible = true,
                    onCancel = { viewModel.stopBroadcasting() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
