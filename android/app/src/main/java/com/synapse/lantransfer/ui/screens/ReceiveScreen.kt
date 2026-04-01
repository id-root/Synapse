package com.synapse.lantransfer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.ui.components.GlassCard
import com.synapse.lantransfer.ui.components.RadarBlip
import com.synapse.lantransfer.ui.components.RadarDisplay
import com.synapse.lantransfer.ui.components.TransferOverlay
import com.synapse.lantransfer.ui.screens.viewmodel.ReceiveViewModel
import com.synapse.lantransfer.ui.theme.*
import com.synapse.lantransfer.util.formatBytes
import com.synapse.lantransfer.util.formatSpeed

@Composable
fun ReceiveScreen(viewModel: ReceiveViewModel = viewModel()) {
    val scanning by viewModel.isScanning.collectAsState()
    val hasScanned by viewModel.hasScanned.collectAsState()
    val peers by viewModel.discoveredPeers.collectAsState()
    val connectingTo by viewModel.connectingTo.collectAsState()
    val transferState by viewModel.transferState.collectAsState()

    val scrollState = rememberScrollState()

    // Spin animation for scan button
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRotation"
    )

    val showOverlay = transferState is TransferState.Receiving &&
        (transferState as? TransferState.Receiving)?.progress != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Receive Files",
                style = SynapseTypography.displayLarge,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = "Discover devices sending files on your network.",
                style = SynapseTypography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Radar Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Radar visualization with blips for discovered peers
                    val blips = if (scanning || peers.isNotEmpty()) {
                        peers.mapIndexed { index, peer ->
                            val angle = (index * 137.5f) % 360f  // Golden angle distribution
                            val distance = 0.3f + (index * 0.15f).coerceAtMost(0.7f)
                            RadarBlip(angle, distance, peer.name)
                        }
                    } else emptyList()

                    RadarDisplay(
                        radarSize = 220.dp,
                        blips = blips
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status text
                    Text(
                        text = when {
                            scanning -> "Scanning via mDNS..."
                            hasScanned && peers.isNotEmpty() -> "Found ${peers.size} peer(s)"
                            hasScanned -> "No peers found"
                            else -> "Tap scan to discover peers"
                        },
                        style = SynapseTypography.bodyMedium,
                        color = if (scanning) Accent1 else TextSecondary,
                        fontWeight = if (scanning) FontWeight.Medium else FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scan Button
                    Button(
                        onClick = { viewModel.startScan() },
                        enabled = !scanning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = if (scanning) Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.White.copy(alpha = 0.3f)
                                        )
                                    ) else Brush.linearGradient(
                                        listOf(Accent1, Accent2)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (scanning) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(spinRotation)
                                    )
                                }
                                Text(
                                    text = if (scanning) "Scanning..." else "🔍 Scan for Peers",
                                    color = if (scanning) TextSecondary else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Peer Cards
            if (peers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Devices",
                        style = SynapseTypography.bodyMedium,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .background(SuccessSubtle, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${peers.size} online",
                            style = SynapseTypography.labelSmall,
                            color = Success
                        )
                    }
                }

                peers.forEachIndexed { index, peer ->
                    val enterAnim = remember { Animatable(0f) }
                    LaunchedEffect(peer) {
                        kotlinx.coroutines.delay(index * 100L)
                        enterAnim.animateTo(
                            1f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .scale(enterAnim.value)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Peer avatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AccentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Computer,
                                    contentDescription = null,
                                    tint = Accent1,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.name,
                                    style = SynapseTypography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = peer.fullAddress,
                                    style = SynapseTypography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            Button(
                                onClick = { 
                                    if (viewModel.autoAccept.value) {
                                        viewModel.connectToPeer(peer) 
                                    } else {
                                        viewModel.requestAccept(peer)
                                    }
                                },
                                enabled = connectingTo != peer.fullAddress,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            brush = if (connectingTo == peer.fullAddress)
                                                Brush.linearGradient(listOf(Color(0xFFCCCCCC), Color(0xFFBBBBBB)))
                                            else
                                                Brush.linearGradient(listOf(Accent1, Accent2)),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (connectingTo == peer.fullAddress) "..." else "↓ Connect",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Empty state
            if (hasScanned && peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📡", fontSize = 36.sp, modifier = Modifier.padding(bottom = 12.dp))
                        Text(
                            text = "No peers found. Make sure someone is sending on the same network.",
                            style = SynapseTypography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Transfer progress overlay
        if (showOverlay) {
            val progress = (transferState as? TransferState.Receiving)?.progress
            if (progress != null) {
                TransferOverlay(
                    fileName = progress.fileName,
                    progress = progress.fraction,
                    transferredBytes = formatBytes(progress.bytesTransferred),
                    totalBytes = formatBytes(progress.totalBytes),
                    speed = formatSpeed(progress.speed),
                    isVisible = true,
                    onCancel = { viewModel.cancelReceive() }
                )
            }
        }

        // Auto Accept Dialog
        val pendingPeer by viewModel.pendingPeerRequest.collectAsState()
        if (pendingPeer != null) {
            AlertDialog(
                onDismissRequest = { viewModel.declineAccept() },
                title = { 
                    Text(
                        "Accept Transfer", 
                        style = SynapseTypography.titleLarge,
                        color = TextPrimary
                    ) 
                },
                text = { 
                    Text(
                        "Do you want to accept files from ${pendingPeer!!.name} (${pendingPeer!!.fullAddress})?",
                        style = SynapseTypography.bodyMedium,
                        color = TextSecondary
                    ) 
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmAccept() }) {
                        Text("Accept", color = Accent1, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.declineAccept() }) {
                        Text("Decline", color = TextMuted)
                    }
                },
                containerColor = BgVoid,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
