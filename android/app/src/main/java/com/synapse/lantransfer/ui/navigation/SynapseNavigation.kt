package com.synapse.lantransfer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.synapse.lantransfer.ui.theme.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Send : Screen("send", "Send", Icons.Rounded.CallMade)
    object Receive : Screen("receive", "Receive", Icons.Rounded.CallReceived)
    object History : Screen("history", "History", Icons.Rounded.History)
    object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)
}

val mainScreens = listOf(
    Screen.Send,
    Screen.Receive,
    Screen.History,
    Screen.Settings
)

@Composable
fun SynapseBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BgElevated,
                            Glass
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassBorder,
                            BorderSubtle
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            mainScreens.forEach { screen ->
                val selected = currentRoute == screen.route
                val animatedColor by animateColorAsState(
                    if (selected) Accent1 else TextSecondary,
                    label = "color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(36.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!selected) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                if (targetState) {
                                    scaleIn(spring(dampingRatio = 0.5f)) togetherWith fadeOut(tween(200))
                                } else {
                                    fadeIn(tween(200)) togetherWith scaleOut(tween(200))
                                }
                            },
                            label = "iconAnim"
                        ) { isSelected ->
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                tint = animatedColor,
                                modifier = Modifier.size(if (isSelected) 26.dp else 24.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = selected,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(Accent1, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
