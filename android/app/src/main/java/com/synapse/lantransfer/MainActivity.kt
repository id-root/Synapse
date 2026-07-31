package com.synapse.lantransfer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.synapse.lantransfer.data.local.PreferencesManager
import com.synapse.lantransfer.ui.navigation.SynapseApp
import com.synapse.lantransfer.ui.theme.SynapseTheme
import com.synapse.lantransfer.util.OverlayPermissionManager

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled — the app works with or without them */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let Compose handle the system windows (draw under status bar/nav bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request necessary permissions
        requestPermissions()
        requestOverlayPermission()

        val prefsManager = PreferencesManager(applicationContext)

        setContent {
            val themeMode by prefsManager.themeMode.collectAsState(initial = "light")
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkMode = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark // "system"
            }
            SynapseTheme(darkTheme = isDarkMode) {
                SynapseApp(
                    isDarkTheme = isDarkMode
                )
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Network permissions (always needed)
        if (!hasPermission(Manifest.permission.INTERNET)) {
            permissions.add(Manifest.permission.INTERNET)
        }

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES))
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO))
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO))
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            // Notification permission for foreground service
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Legacy storage permissions
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Camera permission for QR scanning
        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA)
        }

        // Location & Nearby Wi-Fi permissions for Hotspot
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Handle result if necessary
    }

    private fun requestOverlayPermission() {
        if (!OverlayPermissionManager.hasOverlayPermission(this)) {
            val intent = OverlayPermissionManager.getOverlayPermissionIntent(this)
            overlayPermissionLauncher.launch(intent)
        }
    }
}
