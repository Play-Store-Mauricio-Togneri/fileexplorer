package com.mauriciotogneri.fileexplorer.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    val currentTheme: ThemeMode
        get() = _themeMode.value

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
    }
}
