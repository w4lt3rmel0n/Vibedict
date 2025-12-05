package com.waltermelon.vibedict.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.waltermelon.vibedict.R

// This remains the same
val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Normal)
)

//
// NEW: Define the custom font family for the title tag
//
@OptIn(ExperimentalTextApi::class)
val TitleTagFontFamily = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(110f),
            FontVariation.slant(0f),
            FontVariation.grade(150)
        )
    )
)

//
// NEW: Define the custom "titleTag" text style using the new FontFamily
//
val titleTag = TextStyle(
    fontFamily = TitleTagFontFamily,
    fontSize = 15.sp, // Size: 15
    // "Optical Size: Auto" is handled automatically by the font
    // when you set a specific fontSize (15.sp).
    // The font weight is now controlled by the variation settings.
)

// Set of Material typography styles (remains mostly the same)
val defaultTypography = Typography()
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = RobotoFlex),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = RobotoFlex),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = RobotoFlex),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = RobotoFlex),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = RobotoFlex),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = RobotoFlex),

    titleLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = RobotoFlex),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = RobotoFlex),

    bodyLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = RobotoFlex),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = RobotoFlex),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = RobotoFlex),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = RobotoFlex)
)
