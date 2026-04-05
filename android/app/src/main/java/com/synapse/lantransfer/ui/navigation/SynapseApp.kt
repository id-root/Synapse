package com.synapse.lantransfer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synapse.lantransfer.ui.components.AnimatedBackground
import com.synapse.lantransfer.ui.screens.*

@Composable
fun SynapseApp() {
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
            MainAppContent()
        }
    }
}

@Composable
fun MainAppContent() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            SynapseBottomBar(navController = navController)
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Elegant Beige background animation
            AnimatedBackground()

            NavHost(
                navController = navController,
                startDestination = Screen.Send.route,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                composable(Screen.Send.route) {
                    SendScreen()
                }
                composable(Screen.Receive.route) {
                    ReceiveScreen()
                }
                composable(Screen.History.route) {
                    HistoryScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
