package com.synapse.lantransfer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.delay
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TransferAnimationController(
    state: TransferState,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Auto collapse after a while if expanded
    LaunchedEffect(expanded, state) {
        if (expanded && state !is TransferState.Idle && state !is TransferState.Completed && state !is TransferState.Error) {
            delay(5000)
            expanded = false
        }
    }

    // Spring animation for smooth expansion/collapse
    val transition = updateTransition(targetState = expanded, label = "island_transition")

    val width by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy) },
        label = "width"
    ) { isExpanded -> if (isExpanded) 340.dp else 180.dp }

    val height by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy) },
        label = "height"
    ) { isExpanded -> if (isExpanded) 140.dp else 40.dp }

    val cornerRadius by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy) },
        label = "cornerRadius"
    ) { isExpanded -> if (isExpanded) 44.dp else 20.dp }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .drawBackdrop(
                backdrop = emptyBackdrop(),
                shape = { RoundedCornerShape(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.65f))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (state !is TransferState.Idle && state !is TransferState.Completed && state !is TransferState.Error) {
                    expanded = !expanded
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        transition.AnimatedContent(
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            }
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedIsland(state = state, onCancel = onCancel)
            } else {
                CollapsedIsland(state = state)
            }
        }
    }
}

@Composable
private fun CollapsedIsland(state: TransferState) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Compact Leading
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = Accent1,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val statusText = when (state) {
                is TransferState.Discovering -> "Discovering..."
                is TransferState.Sending -> "Sending..."
                is TransferState.Receiving -> "Receiving..."
                is TransferState.Completed -> "Completed"
                is TransferState.Error -> "Failed"
                else -> "Preparing"
            }
            AnimatedContent(
                targetState = statusText,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith (slideOutVertically { height -> -height } + fadeOut())
                },
                label = "statusTextAnimation"
            ) { targetText ->
                Text(
                    text = targetText,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
        
        // Compact Trailing
        val progress = when(state) {
            is TransferState.Sending -> state.progress?.fraction ?: 0f
            is TransferState.Receiving -> state.progress?.fraction ?: 0f
            is TransferState.Completed -> 1f
            else -> 0f
        }
        
        if (state is TransferState.Sending || state is TransferState.Receiving) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(20.dp),
                color = Accent1,
                trackColor = Color.DarkGray,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ExpandedIsland(state: TransferState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val (title, progressText, progressVal) = when (state) {
            is TransferState.Discovering -> Triple("Scanning for Peers", "0%", 0f)
            is TransferState.Sending -> {
                Triple("Sending...", "${state.progress?.percent ?: 0}%", state.progress?.fraction ?: 0f)
            }
            is TransferState.Receiving -> {
                Triple("Receiving...", "${state.progress?.percent ?: 0}%", state.progress?.fraction ?: 0f)
            }
            is TransferState.Completed -> Triple("Transfer Complete", "100%", 1f)
            is TransferState.Error -> Triple("Transfer Failed", "0%", 0f)
            else -> Triple("Preparing Transfer", "0%", 0f)
        }

        // Top Row: Leading, Center, Trailing
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF1976D2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Synapse",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()) togetherWith (slideOutVertically { height -> -height } + fadeOut())
                        },
                        label = "titleAnimation"
                    ) { targetTitle ->
                        Text(
                            text = targetTitle,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            // Center (Empty for now)
            Spacer(modifier = Modifier.weight(1f))

            // Trailing
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Red)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Bottom
        val animatedProgress by animateFloatAsState(targetValue = progressVal, label = "progress")

        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 8.dp)
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF1976D2),
                trackColor = Color.DarkGray
            )
        }
    }
}
