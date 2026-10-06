package com.myuptm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.myuptm.domain.model.AppFont
import com.myuptm.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = BlueOnSecondary,
    secondaryContainer = BlueSecondaryContainer,
    onSecondaryContainer = BlueOnSecondaryContainer,
    tertiary = BlueTertiary,
    onTertiary = BlueOnTertiary,
    tertiaryContainer = BlueTertiaryContainer,
    onTertiaryContainer = BlueOnTertiaryContainer,
    background = BlueBackground,
    onBackground = BlueOnBackground,
    surface = BlueSurface,
    onSurface = BlueOnSurface,
    // M3 tonal surface tiers — these give light mode real layering (the old
    // alpha-washed cards relied on dark mode to read).
    surfaceContainerLowest = BlueSurfaceLowest,
    surfaceContainerLow = BlueSurfaceContainerLow,
    surfaceContainer = BlueSurfaceContainer,
    surfaceContainerHigh = BlueSurfaceContainerHigh,
    surfaceContainerHighest = BlueSurfaceContainerHighest
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = BlueOnSecondaryDark,
    secondaryContainer = BlueSecondaryContainerDark,
    onSecondaryContainer = BlueOnSecondaryContainerDark,
    tertiary = BlueTertiaryDark,
    onTertiary = BlueOnTertiaryDark,
    tertiaryContainer = BlueTertiaryContainerDark,
    onTertiaryContainer = BlueOnTertiaryContainerDark,
    background = BlueBackgroundDark,
    onBackground = BlueOnBackgroundDark,
    surface = BlueSurfaceDark,
    onSurface = BlueOnSurfaceDark,
    surfaceContainerLowest = BlueSurfaceLowestDark,
    surfaceContainerLow = BlueSurfaceContainerLowDark,
    surfaceContainer = BlueSurfaceContainerDark,
    surfaceContainerHigh = BlueSurfaceContainerHighDark,
    surfaceContainerHighest = BlueSurfaceContainerHighestDark
)

@Composable
fun MyUPTMTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appFont: AppFont = AppFont.DEFAULT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Sprint 9 (owner): Material You — on Android 12+ the wallpaper palette drives
    // the whole scheme (light AND dark), which is what "reflects Material You 3"
    // means. The seeded blue scheme stays as the pre-12 / brand fallback.
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val fontFamily = appFont.toFontFamily()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontFamily),
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)
            ) {
                content()
            }
        }
    )
}