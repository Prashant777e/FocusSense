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
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Navy700,
    onSecondaryContainer = PurpleAccentBg,
    tertiary = EmeraldSafe,
    onTertiary = Color.White,
    background = SurfaceDark,
    surface = CardDark,
    surfaceVariant = Navy700,
    onSurfaceVariant = TextSecondaryDark,
    outline = Navy700,
    outlineVariant = Color(0xFF475569),
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = CoralDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = IndigoDark,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = PurpleAccentBg,
    onSecondaryContainer = Color(0xFF581C87),
    tertiary = EmeraldSafe,
    onTertiary = Color.White,
    background = SurfaceLight,
    surface = CardLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = CoralDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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
