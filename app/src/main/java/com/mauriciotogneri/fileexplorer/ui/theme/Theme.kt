package com.mauriciotogneri.fileexplorer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Charcoal80,
    onPrimary = Grey10,
    primaryContainer = Charcoal40,
    onPrimaryContainer = Charcoal80,
    secondary = Teal80,
    onSecondary = Grey10,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal80,
    tertiary = Orange80,
    onTertiary = Grey10,
    tertiaryContainer = Orange40,
    onTertiaryContainer = Orange80,
    background = Grey10,
    onBackground = Grey90,
    surface = Grey10,
    onSurface = Grey90,
    surfaceVariant = Grey20,
    onSurfaceVariant = Grey90,
    error = Red80,
    onError = Grey10,
    errorContainer = Red40,
    onErrorContainer = Red80
)

private val LightColorScheme = lightColorScheme(
    primary = Charcoal40,
    onPrimary = Grey99,
    primaryContainer = Charcoal80,
    onPrimaryContainer = Grey10,
    secondary = Teal40,
    onSecondary = Grey99,
    secondaryContainer = Teal80,
    onSecondaryContainer = Grey10,
    tertiary = Orange40,
    onTertiary = Grey99,
    tertiaryContainer = Orange80,
    onTertiaryContainer = Grey10,
    background = Grey99,
    onBackground = Grey10,
    surface = Grey99,
    onSurface = Grey10,
    surfaceVariant = Grey95,
    onSurfaceVariant = Grey20,
    error = Red40,
    onError = Grey99,
    errorContainer = Red80,
    onErrorContainer = Grey10
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

@Composable
fun FileExplorerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
