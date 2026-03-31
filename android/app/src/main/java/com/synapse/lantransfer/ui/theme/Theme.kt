package com.synapse.lantransfer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val SynapseColorScheme = lightColorScheme(
    primary = Accent1,
    onPrimary = Color.White,
    primaryContainer = AccentSubtle,
    onPrimaryContainer = Accent2,
    secondary = Accent2,
    onSecondary = Color.White,
    secondaryContainer = AccentSubtle,
    onSecondaryContainer = TextPrimary,
    tertiary = Success,
    onTertiary = Color.White,
    tertiaryContainer = SuccessSubtle,
    onTertiaryContainer = Success,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSubtle,
    onErrorContainer = Danger,
    background = BgVoid,
    onBackground = TextPrimary,
    surface = BgBase,
    onSurface = TextPrimary,
    surfaceVariant = BgCardSolid,
    onSurfaceVariant = TextSecondary,
    outline = BorderStrong,
    outlineVariant = BorderSubtle,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgBase,
    surfaceTint = Accent1
)

@Composable
fun SynapseTheme(content: @Composable () -> Unit) {
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = BgVoid,
            darkIcons = true
        )
        systemUiController.setNavigationBarColor(
            color = BgVoid,
            darkIcons = true
        )
    }

    MaterialTheme(
        colorScheme = SynapseColorScheme,
        typography = SynapseTypography,
        shapes = SynapseShapes,
        content = content
    )
}
