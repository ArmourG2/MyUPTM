package com.myuptm.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appFont: AppFont = AppFont.DEFAULT
)