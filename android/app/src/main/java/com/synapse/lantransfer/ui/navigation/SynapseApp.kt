package com.synapse.lantransfer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.ui.components.AnimatedBackground
import com.synapse.lantransfer.ui.components.CurtainTransition
import com.synapse.lantransfer.ui.components.PigeonLogo
import com.synapse.lantransfer.ui.screens.*
import com.synapse.lantransfer.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SynapseApp(
    isDarkTheme: Boolean
) {
    var showSplash by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            fadeIn(animationSpec = tween(800)) togetherWith fadeOut(animationSpec = tween(800))
        },
        label = "splashTransition"
    ) { splash ->
        if (splash) {
            SplashScreen(onFinished = { showSplash = false })
        } else {
            MainAppContent(
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    isDarkTheme: Boolean
) {
    // Which screen is being shown via curtain transition (null = home)
    var activeScreen by remember { mutableStateOf<String?>(null) }
    var curtainOpen by remember { mutableStateOf(false) }

    // Drawer state for History sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle system back when a screen is open
    BackHandler(enabled = curtainOpen) {
        curtainOpen = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !curtainOpen,
        drawerContent = {
            HistoryDrawerContent(
                isDarkTheme = isDarkTheme,
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background (adapts automatically to colorScheme gradients)
            AnimatedBackground()

            // Home screen (always visible beneath curtain)
            HomeScreen(
                isDarkTheme = isDarkTheme,
                onMenuClick = {
                    scope.launch { drawerState.open() }
                },
                onSettingsClick = {
                    activeScreen = Screen.Settings.route
                    curtainOpen = true
                },
                onSendClick = {
                    activeScreen = Screen.Send.route
                    curtainOpen = true
                },
                onReceiveClick = {
                    activeScreen = Screen.Receive.route
                    curtainOpen = true
                },
                onHotspotShareClick = {
                    activeScreen = Screen.HotspotShare.route
                    curtainOpen = true
                }
            )

            // Curtain transition overlay
            if (activeScreen != null) {
                CurtainTransition(
                    isOpen = curtainOpen,
                    onFullyClosed = {
                        activeScreen = null
                    }
                ) {
                    // Full-screen destination content with its own background
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedBackground()

                        when (activeScreen) {
                            Screen.Send.route -> SendScreen(
                                onBack = { curtainOpen = false }
                            )
                            Screen.Receive.route -> ReceiveScreen(
                                onBack = { curtainOpen = false }
                            )
                            Screen.Settings.route -> SettingsScreen(
                                onBack = { curtainOpen = false }
                            )
                            Screen.HotspotShare.route -> HotspotShareScreen(
                                onBack = { curtainOpen = false },
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * History sidebar / drawer content.
 */
@Composable
private fun HistoryDrawerContent(
    isDarkTheme: Boolean,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = if (isDarkTheme) BgVoidDark else HomeBgTop,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Drawer header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
            ) {
                PigeonLogo(logoSize = 40.dp, enableAnimation = false)
                Text(
                    text = "Synapse",
                    style = SynapseTypography.displayMedium,
                    color = if (isDarkTheme) TextPrimaryDark else TextPrimary
                )
            }

            HorizontalDivider(
                color = if (isDarkTheme) BorderSubtleDark else HomeBgMid,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // History icon + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isDarkTheme) BgCardSolidDark else HomeBgMid.copy(alpha = 0.6f)
                    )
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = if (isDarkTheme) Accent1Dark else Accent1,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Transfer History",
                    style = SynapseTypography.titleMedium,
                    color = if (isDarkTheme) TextPrimaryDark else TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History content (embedded)
            Box(modifier = Modifier.weight(1f)) {
                HistoryScreen()
            }
        }
    }
}
