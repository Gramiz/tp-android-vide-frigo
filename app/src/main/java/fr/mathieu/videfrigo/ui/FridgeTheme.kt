package fr.mathieu.videfrigo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun FridgeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFA8D5B8), onPrimary = Color(0xFF103824),
        background = Color(0xFF111C16), surface = Color(0xFF111C16),
        onBackground = Color(0xFFE2EDE4), onSurface = Color(0xFFE2EDE4),
    ) else lightColorScheme(
        primary = Color(0xFF245E43), onPrimary = Color.White,
        primaryContainer = Color(0xFFE5F0E7), onPrimaryContainer = Color(0xFF203A2D),
        background = Color(0xFFFAFBF7), surface = Color(0xFFFAFBF7),
        onBackground = Color(0xFF203A2D), onSurface = Color(0xFF203A2D),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
