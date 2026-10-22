package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color.White,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Navy700,
    onSecondaryContainer = Color.White,
    tertiary = AmberWarning,
    background = SurfaceDark,
    surface = CardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = CoralDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    primaryContainer = IndigoLight,
    onPrimaryContainer = Navy900,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = PurpleAccentBg,
    onSecondaryContainer = Navy900,
    tertiary = AmberWarning,
    background = SurfaceLight,
    surface = CardLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = CoralDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false for consistent branded colors
    content: @Composable () -> Unit
) {
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
