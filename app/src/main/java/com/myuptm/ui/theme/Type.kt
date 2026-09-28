package com.myuptm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myuptm.domain.model.AppFont

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.DEFAULT -> FontFamily.Default
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
}

fun appTypography(family: FontFamily): Typography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(fontFamily = family),
    displayMedium = Typography.displayMedium.copy(fontFamily = family),
    displaySmall = Typography.displaySmall.copy(fontFamily = family),
    headlineLarge = Typography.headlineLarge.copy(fontFamily = family),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = family),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = family),
    titleLarge = Typography.titleLarge.copy(fontFamily = family),
    titleMedium = Typography.titleMedium.copy(fontFamily = family),
    titleSmall = Typography.titleSmall.copy(fontFamily = family),
    bodyLarge = Typography.bodyLarge.copy(fontFamily = family),
    bodyMedium = Typography.bodyMedium.copy(fontFamily = family),
    bodySmall = Typography.bodySmall.copy(fontFamily = family),
    labelLarge = Typography.labelLarge.copy(fontFamily = family),
    labelMedium = Typography.labelMedium.copy(fontFamily = family),
    labelSmall = Typography.labelSmall.copy(fontFamily = family)
)