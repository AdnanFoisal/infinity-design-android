package com.adnanfoisal.infinitydesign.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3F51B5),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF7986CB),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
    background = androidx.compose.ui.graphics.Color(0xFFF7F7FA),
    surface = androidx.compose.ui.graphics.Color.White,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7986CB),
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = androidx.compose.ui.graphics.Color(0xFF3F51B5),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
    background = androidx.compose.ui.graphics.Color(0xFF0F172A),
    surface = androidx.compose.ui.graphics.Color(0xFF1E293B),
)

@Composable
fun InfinityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
