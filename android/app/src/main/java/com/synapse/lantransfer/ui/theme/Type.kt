package com.synapse.lantransfer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.R

val GamjaFlower = FontFamily(
    Font(R.font.gamja_flower, FontWeight.Normal)
)

val GamjaFlowerTextStyle = TextStyle(
    fontFamily = GamjaFlower,
    fontWeight = FontWeight.Normal,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)
)

val SynapseTypography = Typography(
    // Displays use Gamja Flower
    displayLarge = GamjaFlowerTextStyle.copy(
        fontSize = 32.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 44.sp
    ),
    displayMedium = GamjaFlowerTextStyle.copy(
        fontSize = 26.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 36.sp
    ),
    displaySmall = GamjaFlowerTextStyle.copy(
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 28.sp
    ),
    
    // Headlines use Gamja Flower
    headlineLarge = GamjaFlowerTextStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = GamjaFlowerTextStyle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = GamjaFlowerTextStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),

    // Titles
    titleLarge = GamjaFlowerTextStyle.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = GamjaFlowerTextStyle.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, fontWeight = FontWeight.Medium),
    titleSmall = GamjaFlowerTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),

    // Body uses Gamja Flower
    bodyLarge = GamjaFlowerTextStyle.copy(fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = GamjaFlowerTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = GamjaFlowerTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // Labels use Gamja Flower
    labelLarge = GamjaFlowerTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),
    labelMedium = GamjaFlowerTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = GamjaFlowerTextStyle.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)
)
