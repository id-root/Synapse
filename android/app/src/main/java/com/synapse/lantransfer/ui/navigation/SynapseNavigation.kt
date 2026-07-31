package com.synapse.lantransfer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Send : Screen("send", "Send", Icons.Rounded.CallMade)
    object Receive : Screen("receive", "Receive", Icons.Rounded.CallReceived)
    object History : Screen("history", "History", Icons.Rounded.History)
    object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)
    object HotspotShare : Screen("hotspot_share", "Hotspot Share", Icons.Rounded.Share)
}
