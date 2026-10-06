package com.myuptm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myuptm.domain.model.AppFont
import com.myuptm.domain.model.AppSettings
import com.myuptm.domain.model.ThemeMode
import com.myuptm.domain.model.ThemePalette
import com.myuptm.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1. Create the DataStore instance at the top level of the file.
// This ensures only one instance of the DataStore is created for the whole app.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    // 2. Define the keys we will use to store and retrieve data.
    // We use strings to represent our Enums because DataStore doesn't store Enums directly.
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_FONT = stringPreferencesKey("app_font")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
    }

    // 3. Read the settings from DataStore and map them to our AppSettings domain model
    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            // Read the string values, defaulting to SYSTEM, DEFAULT and DYNAMIC if they don't exist yet
            val themeModeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val appFontString = preferences[PreferencesKeys.APP_FONT] ?: AppFont.DEFAULT.name
            val themePaletteString = preferences[PreferencesKeys.THEME_PALETTE] ?: ThemePalette.DYNAMIC.name

            AppSettings(
                // valueOf converts the string back into our Enum
                themeMode = ThemeMode.valueOf(themeModeString),
                appFont = AppFont.valueOf(appFontString),
                themePalette = ThemePalette.valueOf(themePaletteString)
            )
        }
    }

    // 4. Save the new ThemeMode to DataStore
    override suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    // 5. Save the new AppFont to DataStore
    override suspend fun updateAppFont(font: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT] = font.name
        }
    }

    // 6. Save the new ThemePalette to DataStore
    override suspend fun updateThemePalette(palette: ThemePalette) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_PALETTE] = palette.name
        }
    }
}