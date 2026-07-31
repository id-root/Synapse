package com.synapse.lantransfer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val SynapseLightColorScheme = lightColorScheme(
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
    outline = BorderDefault,
    outlineVariant = BorderSubtle,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgBase,
    surfaceTint = Accent1
)

private val SynapseDarkColorScheme = darkColorScheme(
    primary = Accent1Dark,
    onPrimary = Color.Black,
    primaryContainer = AccentSubtleDark,
    onPrimaryContainer = Accent1Dark,
    secondary = Accent2Dark,
    onSecondary = Color.Black,
    secondaryContainer = AccentSubtleDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = Success,
    onTertiary = Color.White,
    tertiaryContainer = SuccessSubtle,
    onTertiaryContainer = Success,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSubtle,
    onErrorContainer = Danger,
    background = BgVoidDark,
    onBackground = TextPrimaryDark,
    surface = BgBaseDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BgCardSolidDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDefaultDark,
    outlineVariant = BorderSubtleDark,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BgBaseDark,
    surfaceTint = Accent1Dark
)

@Composable
fun SynapseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SynapseDarkColorScheme else SynapseLightColorScheme
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(
            color = if (darkTheme) BgVoidDark else Color.White,
            darkIcons = !darkTheme
        )
        systemUiController.setNavigationBarColor(
            color = if (darkTheme) BgVoidDark else Color.White,
            darkIcons = !darkTheme
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SynapseTypography,
        shapes = SynapseShapes,
        content = content
    )
}
