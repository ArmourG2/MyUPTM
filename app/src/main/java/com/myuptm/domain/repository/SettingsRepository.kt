package com.myuptm.domain.repository

import com.myuptm.domain.model.AppFont
import com.myuptm.domain.model.AppSettings
import com.myuptm.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateAppFont(font: AppFont)
}