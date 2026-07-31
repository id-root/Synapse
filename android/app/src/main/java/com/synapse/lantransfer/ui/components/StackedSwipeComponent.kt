package com.synapse.lantransfer.ui.components

import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ModeCard { SEND, RECEIVE }

// Basic AGSL Shader to mimic a liquid/metal distortion effect (Requires API 33+)
const val LIQUID_METAL_SHADER = """
    uniform float2 iResolution;
    uniform float iTime;
    uniform float speedMultiplier;
    
    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        
        float time = iTime * speedMultiplier * 2.0;
        float wave1 = sin(uv.x * 10.0 + time) * 0.05;
        float wave2 = cos(uv.y * 8.0 - time * 0.8) * 0.05;
        
        float2 distortedUv = uv + float2(wave1, wave2);
        
        float metallic = sin((distortedUv.x + distortedUv.y) * 15.0) * 0.5 + 0.5;
        float highlight = pow(metallic, 3.0);
        
        half3 color = half3(0.2, 0.2, 0.25) + half3(highlight * 0.8);
        return half4(color, 1.0);
    }
"""

@Composable
fun StackedSwipeComponent(
    isDarkTheme: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(84.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        SendReceiveStackedPills(
            isDarkTheme = isDarkTheme,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick
        )
    }
}

@Composable
private fun SendReceiveStackedPills(
    isDarkTheme: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    var activeMode by remember { mutableStateOf(ModeCard.SEND) }
    val scope = rememberCoroutineScope()
    var isAnimating by remember { mutableStateOf(false) }

    // Swipe animation states
    val sendOffsetY = remember { Animatable(0f) }
    val receiveOffsetY = remember { Animatable(12f) }
    val sendScale = remember { Animatable(1f) }
    val receiveScale = remember { Animatable(0.9f) }
    var sendZIndex by remember { mutableStateOf(2f) }
    var receiveZIndex by remember { mutableStateOf(1f) }

    fun triggerModeSwap() {
        if (isAnimating) return
        isAnimating = true
        scope.launch {
            if (activeMode == ModeCard.SEND) {
                // Send Card flies UP, scales down, then slides to back
                launch { sendOffsetY.animateTo(-80f, tween(180, easing = FastOutSlowInEasing)) }
                launch { sendScale.animateTo(0.8f, tween(180)) }

                // Receive Card slides to front
                launch { receiveOffsetY.animateTo(0f, tween(200, easing = LinearOutSlowInEasing)) }
                launch { receiveScale.animateTo(1f, tween(200)) }

                delay(130)
                sendZIndex = 1f
                receiveZIndex = 2f

                // Send Card slides back down to back
                launch { sendOffsetY.animateTo(12f, tween(180, easing = FastOutSlowInEasing)) }
                launch { sendScale.animateTo(0.9f, tween(180)) }

                delay(180)
                activeMode = ModeCard.RECEIVE
            } else {
                // Receive Card flies UP, scales down, then slides to back
                launch { receiveOffsetY.animateTo(-80f, tween(180, easing = FastOutSlowInEasing)) }
                launch { receiveScale.animateTo(0.8f, tween(180)) }

                // Send Card slides to front
                launch { sendOffsetY.animateTo(0f, tween(200, easing = LinearOutSlowInEasing)) }
                launch { sendScale.animateTo(1f, tween(200)) }

                delay(130)
                receiveZIndex = 1f
                sendZIndex = 2f

                // Receive Card slides back down to back
                launch { receiveOffsetY.animateTo(12f, tween(180, easing = FastOutSlowInEasing)) }
                launch { receiveScale.animateTo(0.9f, tween(180)) }

                delay(180)
                activeMode = ModeCard.SEND
            }
            isAnimating = false
        }
    }

    var dragAccumulator = 0f
    val inputModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onDragEnd = {
                if (dragAccumulator < -40f) { // Swiped up
                    triggerModeSwap()
                }
                dragAccumulator = 0f
            },
            onDragCancel = { dragAccumulator = 0f },
            onDrag = { change, dragAmount ->
                change.consume()
                dragAccumulator += dragAmount.y
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(inputModifier)
    ) {
        // ── RECEIVE CARD ──
        LiquidMetalPill(
            label = "Receive Files",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CallReceived,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
            },
            offsetY = receiveOffsetY.value,
            scale = receiveScale.value,
            zIndex = receiveZIndex,
            isFront = activeMode == ModeCard.RECEIVE,
            onClick = {
                if (activeMode == ModeCard.RECEIVE) {
                    onReceiveClick()
                } else {
                    triggerModeSwap()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // ── SEND CARD ──
        LiquidMetalPill(
            label = "Send Files",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CallMade,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
            },
            offsetY = sendOffsetY.value,
            scale = sendScale.value,
            zIndex = sendZIndex,
            isFront = activeMode == ModeCard.SEND,
            onClick = {
                if (activeMode == ModeCard.SEND) {
                    onSendClick()
                } else {
                    triggerModeSwap()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LiquidMetalPill(
    label: String,
    icon: @Composable () -> Unit,
    offsetY: Float,
    scale: Float,
    zIndex: Float,
    isFront: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Inner click physics scaling
    val clickScale by animateFloatAsState(
        targetValue = if (isPressed && isFront) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "ClickScale"
    )

    val clickOffsetY = if (isPressed && isFront) 2.dp else 0.dp

    // Combine stacked scaling/offsets and click interaction values
    val finalScaleX = scale * clickScale
    val finalScaleY = scale * clickScale
    val finalOffsetY = offsetY.dp + clickOffsetY

    val depthZ by animateFloatAsState(
        targetValue = if (isPressed && isFront) 5f else 15f,
        label = "DepthZ"
    )

    // Shader Time Tracking
    var time by remember { mutableFloatStateOf(0f) }
    val speedMultiplier = if (isPressed && isFront) 2.4f else 0.6f

    LaunchedEffect(isFront) {
        if (isFront) {
            while (true) {
                withInfiniteAnimationFrameMillis { frameTimeMillis ->
                    time = frameTimeMillis / 1000f
                }
            }
        }
    }

    val shaderBrush = remember(time, speedMultiplier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            object : ShaderBrush() {
                override fun createShader(size: androidx.compose.ui.geometry.Size): android.graphics.Shader {
                    val runtimeShader = RuntimeShader(LIQUID_METAL_SHADER)
                    runtimeShader.setFloatUniform("iResolution", size.width, size.height)
                    runtimeShader.setFloatUniform("iTime", time)
                    runtimeShader.setFloatUniform("speedMultiplier", speedMultiplier)
                    return runtimeShader
                }
            }
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF2C2D35), Color(0xFF15161A))
            )
        }
    }

    Box(
        modifier = modifier
            .zIndex(zIndex)
            .graphicsLayer {
                translationY = finalOffsetY.toPx()
                scaleX = finalScaleX
                scaleY = finalScaleY
                cameraDistance = 8 * density
            }
            .height(60.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Liquid Metal Shader Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (isPressed && isFront) 2.dp.toPx() else 0f
                }
                .shadow(
                    elevation = if (isPressed && isFront) 2.dp else 12.dp,
                    shape = RoundedCornerShape(30.dp),
                    spotColor = Color.Black,
                    ambientColor = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(30.dp))
                .background(shaderBrush)
        )

        // Layer 2: Inner Dark Container (Bezel Border Effect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF202020), Color(0xFF0C0C0C))
                    )
                )
        )

        // Layer 3: Foreground Content (Icon & Text)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = Color(0xFFCCCCCC),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
