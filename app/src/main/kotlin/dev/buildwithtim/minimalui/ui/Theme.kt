package dev.buildwithtim.minimalui.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors =
    darkColorScheme(
        background = Color.Black,
        onBackground = Color.White,
        primary = Color.White,
        onPrimary = Color.Black,
    )

/** Black background, white text, the focused row inverted (see BRANDING.md). */
@Composable
fun MinimalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
