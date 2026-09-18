package com.apoorv.yrb.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B1B18),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E5DE),
    onPrimaryContainer = Color(0xFF1B1B18),
    secondary = Color(0xFF67635C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F3ED),
    onBackground = Color(0xFF1B1B18),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1B1B18),
    surfaceVariant = Color(0xFFEDE9E1),
    onSurfaceVariant = Color(0xFF625F58),
    outline = Color(0xFF817D75),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2EFE8),
    onPrimary = Color(0xFF22221F),
    primaryContainer = Color(0xFF34332F),
    onPrimaryContainer = Color(0xFFF2EFE8),
    secondary = Color(0xFFC9C4BA),
    onSecondary = Color(0xFF302F2B),
    background = Color(0xFF11110F),
    onBackground = Color(0xFFF0EDE6),
    surface = Color(0xFF191917),
    onSurface = Color(0xFFF0EDE6),
    surfaceVariant = Color(0xFF282723),
    onSurfaceVariant = Color(0xFFC9C4BA),
    outline = Color(0xFF928D84),
    error = Color(0xFFFFB4AB)
)

@Composable
fun YrbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}
