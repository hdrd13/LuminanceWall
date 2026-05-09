package com.hdrd13.luminancewall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hdrd13.luminancewall.R

val SFPro = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold)
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = SFPro),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = SFPro),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = SFPro),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = SFPro),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = SFPro),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = SFPro),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = SFPro),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = SFPro),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = SFPro),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = SFPro),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = SFPro),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = SFPro),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = SFPro),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = SFPro),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = SFPro)
)