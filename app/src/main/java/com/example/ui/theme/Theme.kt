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
    primary = MinimalistPrimaryDark,
    secondary = MinimalistSecondaryDark,
    tertiary = MinimalistPurpleAccentBg,
    background = MinimalistBgDark,
    surface = MinimalistSurfaceDark,
    onPrimary = Color.Black,
    onSecondary = MinimalistOnSecondaryDark,
    onBackground = MinimalistTextDark,
    onSurface = MinimalistTextDark,
    surfaceVariant = MinimalistBorderDark,
    onSurfaceVariant = MinimalistTextMutedDark
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalistPrimaryLight,
    secondary = MinimalistSecondaryLight,
    tertiary = MinimalistPurpleAccentBg,
    background = MinimalistBgLight,
    surface = MinimalistSurfaceLight,
    onPrimary = Color.White,
    onSecondary = MinimalistOnSecondaryLight,
    onBackground = MinimalistTextLight,
    onSurface = MinimalistTextLight,
    surfaceVariant = MinimalistBorderLight,
    onSurfaceVariant = MinimalistTextMutedLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color disabled to showcase our custom premium cyber colors!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
