package com.synapse.lantransfer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.R

val ArchitectsDaughter = FontFamily(
    Font(R.font.architects_daughter, FontWeight.Normal)
)

val Delius = FontFamily(
    Font(R.font.delius, FontWeight.Normal)
)

val ArchitectsDaughterTextStyle = TextStyle(
    fontFamily = ArchitectsDaughter,
    fontWeight = FontWeight.Normal,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)
)

val DeliusTextStyle = TextStyle(
    fontFamily = Delius,
    fontWeight = FontWeight.Normal,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)
)

val SynapseTypography = Typography(
    // Displays use Architects Daughter
    displayLarge = ArchitectsDaughterTextStyle.copy(
        fontSize = 32.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 44.sp
    ),
    displayMedium = ArchitectsDaughterTextStyle.copy(
        fontSize = 26.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 36.sp
    ),
    displaySmall = ArchitectsDaughterTextStyle.copy(
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 28.sp
    ),
    
    // Headlines use Architects Daughter
    headlineLarge = ArchitectsDaughterTextStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = ArchitectsDaughterTextStyle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = ArchitectsDaughterTextStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),

    // Titles
    titleLarge = ArchitectsDaughterTextStyle.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = DeliusTextStyle.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, fontWeight = FontWeight.Medium),
    titleSmall = DeliusTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),

    // Body uses Delius
    bodyLarge = DeliusTextStyle.copy(fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = DeliusTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = DeliusTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // Labels use Delius
    labelLarge = DeliusTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),
    labelMedium = DeliusTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = DeliusTextStyle.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)
)
