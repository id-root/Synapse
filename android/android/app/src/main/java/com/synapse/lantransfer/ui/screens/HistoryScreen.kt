package com.synapse.lantransfer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synapse.lantransfer.data.model.TransferDirection
import com.synapse.lantransfer.data.model.TransferRecord
import com.synapse.lantransfer.data.model.TransferStats
import com.synapse.lantransfer.data.model.TransferStatus
import com.synapse.lantransfer.ui.components.GlassCard
import com.synapse.lantransfer.ui.screens.viewmodel.HistoryViewModel
import com.synapse.lantransfer.ui.theme.*
import com.synapse.lantransfer.util.formatBytes
import com.synapse.lantransfer.util.formatTimestamp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsState(initial = emptyList())
    val stats by viewModel.stats.collectAsState(initial = TransferStats(0, 0, 0))
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    text = "Transfer History",
                    style = SynapseTypography.displayLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Text(
                    text = "All your recent file transfers.",
                    style = SynapseTypography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            if (isLoading) {
                items(3) {
                    SkeletonRow()
                }
            } else if (entries.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📥", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                            Text(
                                text = "No transfers yet",
                                style = SynapseTypography.displaySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Your sent and received files will appear here.",
                                style = SynapseTypography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            value = stats.sentCount.toString(),
                            label = "Sent",
                            color = Accent1,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = stats.receivedCount.toString(),
                            label = "Received",
                            color = Accent1,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = stats.completedCount.toString(),
                            label = "Success",
                            color = Success,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                itemsIndexed(entries) { index, entry ->
                    HistoryItem(entry = entry, index = index)
                }
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = SynapseTypography.displayMedium,
                color = color
            )
            Text(
                text = label,
                style = SynapseTypography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SkeletonRow() {
    val shimmerAlpha by rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "shimmer"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentSubtle.copy(alpha = shimmerAlpha))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentSubtle.copy(alpha = shimmerAlpha))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentSubtle.copy(alpha = shimmerAlpha))
            )
        }
    }
}

@Composable
fun HistoryItem(entry: TransferRecord, index: Int) {
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        enterAnim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f))
    }

    val isReceive = entry.direction == TransferDirection.RECEIVE
    val ok = entry.status == TransferStatus.COMPLETED

    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .scale(enterAnim.value)
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { showDetails = true }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isReceive) InfoSubtle else AccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isReceive) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                    contentDescription = null,
                    tint = if (isReceive) Info else Accent1,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    style = SynapseTypography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        text = formatTimestamp(entry.timestamp),
                        style = SynapseTypography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(" · ", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "${if (isReceive) "From" else "To"} ${entry.peerName}",
                        style = SynapseTypography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    if (entry.fileSize > 0) {
                        Text(" · ", color = TextMuted, fontSize = 11.sp)
                        Text(
                            text = formatBytes(entry.fileSize),
                            style = SynapseTypography.labelSmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (ok) SuccessSubtle else DangerSubtle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (ok) Success else Danger,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = if (ok) "Done" else "Failed",
                    style = SynapseTypography.labelSmall,
                    color = if (ok) Success else Danger,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = {
                Text(
                    text = "Transfer Details",
                    style = SynapseTypography.displayMedium,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("File Name", entry.fileName)
                    DetailRow("Direction", if (isReceive) "Receive" else "Send")
                    DetailRow("Peer Device", entry.peerName)
                    DetailRow("Date", formatTimestamp(entry.timestamp))
                    if (entry.fileSize > 0) {
                        DetailRow("Size", formatBytes(entry.fileSize))
                    }
                    DetailRow("Status", if (ok) "Completed" else "Failed", 
                        valueColor = if (ok) Success else Danger)
                    
                    if (!entry.errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Error Reason", style = SynapseTypography.labelSmall, color = TextSecondary)
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .background(DangerSubtle, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                        ) {
                            Text(text = entry.errorMessage ?: "", style = SynapseTypography.bodySmall, color = Danger)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close", color = Accent1)
                }
            },
            containerColor = BgCardSolid,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = SynapseTypography.bodyMedium, color = TextSecondary)
        Text(text = value, style = SynapseTypography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}
