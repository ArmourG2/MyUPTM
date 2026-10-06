package com.myuptm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.AppFont
import com.myuptm.domain.model.ThemeMode
import com.myuptm.domain.model.ThemePalette
import com.myuptm.ui.theme.seedColor
import com.myuptm.ui.theme.toFontFamily

@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun ThemeModeRow(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth(0.7f)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        )
                    ) { Text(mode.label) }
                }
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "Auto"
    }

// Sprint 12: theme-colour picker — one tappable swatch per palette.
// DYNAMIC gets a rainbow ring to distinguish it from fixed seeds.
@Composable
fun ThemePaletteRow(
    themePalette: ThemePalette,
    onPaletteSelected: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Theme Colour",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePalette.entries.forEach { palette ->
                Swatch(
                    palette = palette,
                    selected = themePalette == palette,
                    onClick = { onPaletteSelected(palette) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = paletteCaption(themePalette),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Swatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                brush = if (palette == ThemePalette.DYNAMIC) {
                    Brush.horizontalGradient(
                        0f to Color(0xFF1565C0),
                        0.35f to Color(0xFF388E3C),
                        0.65f to Color(0xFFEF6C00),
                        1f to Color(0xFF7C4DFF)
                    )
                } else Brush.horizontalGradient(
                    0f to palette.seedColor,
                    1f to palette.seedColor
                ),
                shape = CircleShape
            )
            .border(width = if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .clickable(role = Role.RadioButton) { onClick() }
    )
}

private fun paletteCaption(palette: ThemePalette): String = when (palette) {
    ThemePalette.DYNAMIC -> "Dynamic — follows your wallpaper (Material You)"
    ThemePalette.UPTM_BLUE -> "UPTM Blue — the brand palette"
    ThemePalette.TEAL -> "Teal"
    ThemePalette.PURPLE -> "Purple"
    ThemePalette.GREEN -> "Green"
    ThemePalette.ORANGE -> "Orange"
}

private val AppFont.label: String
    get() = when (this) {
        AppFont.DEFAULT -> "Default"
        AppFont.SERIF -> "Serif"
        AppFont.MONOSPACE -> "Mono"
    }

@Composable
fun FontPickerRow(
    appFont: AppFont,
    onFontSelected: (AppFont) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Text Font",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFont.entries.forEach { font ->
                FilterChip(
                    selected = appFont == font,
                    onClick = { onFontSelected(font) },
                    label = { Text(text = font.label, fontFamily = font.toFontFamily()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}