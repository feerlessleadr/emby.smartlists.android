package com.feerlessleadr.embysmartlists.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E7B34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F2B6),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF111F10),
    tertiary = Color(0xFF8A2D6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF3A002B),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BF27A),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF005318),
    onPrimaryContainer = Color(0xFFB7F2B6),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFFFAED9),
    onTertiary = Color(0xFF5B0F45),
    tertiaryContainer = Color(0xFF732A5A),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE1E3DC),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE1E3DC),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    error = Color(0xFFFFB4AB),
)

/** The app's own green scheme (Emby green with a pink accent), light and dark; it does not follow the wallpaper. */
@Composable
fun EmbySmartListsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
