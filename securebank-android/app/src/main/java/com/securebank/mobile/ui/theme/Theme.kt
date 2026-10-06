package com.securebank.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Mesmos tokens do web (securebank-web/src/index.css): um acento teal, neutros frios, um único raio (10 dp),
 * claro e escuro seguindo o sistema, contraste AA nos dois.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F1EF),
    onPrimaryContainer = Color(0xFF0B5F58),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF14191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14191C),
    onSurfaceVariant = Color(0xFF566168),
    outline = Color(0xFFDFE3E6),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFDECEA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF06201D),
    primaryContainer = Color(0xFF11302D),
    onPrimaryContainer = Color(0xFF5EEAD4),
    background = Color(0xFF0E1214),
    onBackground = Color(0xFFE9EEF0),
    surface = Color(0xFF161C1F),
    onSurface = Color(0xFFE9EEF0),
    onSurfaceVariant = Color(0xFF9AA7AD),
    outline = Color(0xFF2A3338),
    error = Color(0xFFFF8A7A),
    errorContainer = Color(0xFF3A1B18),
)

private val radius = RoundedCornerShape(10.dp)
private val shapes = Shapes(extraSmall = radius, small = radius, medium = radius, large = radius, extraLarge = radius)

@Composable
fun SecureBankTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = shapes, content = content)
}
