package com.kgurgul.openksef.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue700 = Color(0xFF1565C0)
private val Blue500 = Color(0xFF1E88E5)
private val Blue200 = Color(0xFF90CAF9)
private val Indigo700 = Color(0xFF283593)
private val Indigo400 = Color(0xFF5C6BC0)
private val Orange500 = Color(0xFFFF9800)
private val Orange200 = Color(0xFFFFCC80)

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = Color.White,
    primaryContainer = Blue200,
    onPrimaryContainer = Indigo700,
    secondary = Indigo400,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5CAE9),
    onSecondaryContainer = Indigo700,
    tertiary = Orange500,
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8EAF6),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Indigo700,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue200,
    secondary = Color(0xFF9FA8DA),
    onSecondary = Indigo700,
    secondaryContainer = Indigo400,
    onSecondaryContainer = Color(0xFFC5CAE9),
    tertiary = Orange200,
    onTertiary = Color(0xFF4E2600),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun OpenKsefTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
