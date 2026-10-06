package com.myuptm.domain.model

/**
 * Sprint 12: theme-colour picker. DYNAMIC keeps the Sprint 9 Material You
 * behavior (wallpaper-derived on Android 12+); the rest are fixed seed
 * palettes, expanded to full M3 schemes in `ui/theme/Theme.kt` via
 * Material Kolor. Works on every API level (29+), unlike DYNAMIC.
 */
enum class ThemePalette {
    DYNAMIC,
    UPTM_BLUE,
    TEAL,
    PURPLE,
    GREEN,
    ORANGE
}
