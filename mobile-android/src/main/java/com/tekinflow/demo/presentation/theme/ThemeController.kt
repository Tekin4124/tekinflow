package com.tekinflow.demo.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class ThemeController(
    val isDarkTheme: Boolean,
    val onThemeChange: (Boolean) -> Unit
)

val LocalThemeController = staticCompositionLocalOf {
    ThemeController(
        isDarkTheme = false,
        onThemeChange = {}
    )
}
