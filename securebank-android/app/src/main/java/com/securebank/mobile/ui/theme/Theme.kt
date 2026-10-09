package com.securebank.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * Mesmos tokens do web (securebank-web/src/index.css): escuro índigo por padrão, com cartões de vidro; o claro é uma
 * alternativa escolhida pelo usuário (Mais → Tema). Um acento de interface (violeta); as demais cores existem para
 * distinguir séries nos gráficos e estados (ok, aviso, erro). Contraste AA nos dois temas.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFFA191FF),
    onPrimary = Color(0xFF130E3A),
    primaryContainer = Color(0xFF312A6B),
    onPrimaryContainer = Color(0xFFD9D3FF),
    secondaryContainer = Color(0xFF312A6B),
    onSecondaryContainer = Color(0xFFD9D3FF),
    surfaceVariant = Color(0xFF241D5E),
    surfaceContainerLowest = Color(0xFF0C0826),
    surfaceContainerLow = Color(0xFF150F3D),
    surfaceContainer = Color(0xFF1A1348),
    surfaceContainerHigh = Color(0xFF211A55),
    surfaceContainerHighest = Color(0xFF292160),
    background = Color(0xFF0F0B2E),
    onBackground = Color(0xFFF1EFFF),
    surface = Color(0xFF1A1348),
    onSurface = Color(0xFFF1EFFF),
    onSurfaceVariant = Color(0xFFA7A3D6),
    outline = Color(0x1CFFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFFF8FA3),
    onError = Color(0xFF3B0A18),
    errorContainer = Color(0xFF4A2036),
    onErrorContainer = Color(0xFFFFD9DF),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E5FC),
    onPrimaryContainer = Color(0xFF3F32A8),
    secondaryContainer = Color(0xFFE8E5FC),
    onSecondaryContainer = Color(0xFF3F32A8),
    surfaceVariant = Color(0xFFEFEDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6FE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F0FC),
    surfaceContainerHighest = Color(0xFFEAE8F9),
    background = Color(0xFFF1F0FB),
    onBackground = Color(0xFF17123D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17123D),
    onSurfaceVariant = Color(0xFF5F5B88),
    outline = Color(0xFFE1DEF3),
    outlineVariant = Color(0xFFECEAF8),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7A1A12),
)

/** Cores que o Material não tem: estados, gráficos e as superfícies de vidro. */
data class SbColors(
    val dark: Boolean,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    /** Séries dos gráficos (mesma ordem do web: --chart-1 … --chart-8). */
    val chart: List<Color>,
    /** Cartão de vidro: translúcido sobre o fundo (no claro, quase opaco). */
    val glass: Color,
    val glassBorder: Color,
    /** Fundo sólido para o que não pode ser translúcido (dica de gráfico, menus). */
    val panel: Color,
    val pageTop: Color,
    val pageBottom: Color,
    val glowA: Color,
    val glowB: Color,
)

private val DarkSb = SbColors(
    dark = true,
    ok = Color(0xFF5FE3AD),
    okSoft = Color(0x245FE3AD),
    warn = Color(0xFFFFC562),
    warnSoft = Color(0x26FFC562),
    chart = listOf(Color(0xFFA191FF), Color(0xFF38D9C7), Color(0xFFFFB454), Color(0xFFFF7A96), Color(0xFF62B8FF), Color(0xFF7FE3A9), Color(0xFFD58CF5), Color(0xFFA7A3D6)),
    glass = Color(0x0FFFFFFF),
    glassBorder = Color(0x1CFFFFFF),
    panel = Color(0xFF1D1558),
    pageTop = Color(0xFF0F0B2E),
    pageBottom = Color(0xFF1B1253),
    glowA = Color(0x807A5CFF),
    glowB = Color(0x2138D9C7),
)

private val LightSb = SbColors(
    dark = false,
    ok = Color(0xFF17663A),
    okSoft = Color(0xFFE4F4EA),
    warn = Color(0xFF8A5A00),
    warnSoft = Color(0xFFFFF4DC),
    chart = listOf(Color(0xFF6A58E8), Color(0xFF0F9D8F), Color(0xFFD98A1E), Color(0xFFE5506F), Color(0xFF3B8FD9), Color(0xFF3FAE78), Color(0xFFA15FD8), Color(0xFF7C78A8)),
    glass = Color(0xEBFFFFFF),
    glassBorder = Color(0xFFE1DEF3),
    panel = Color(0xFFFFFFFF),
    pageTop = Color(0xFFF4F2FF),
    pageBottom = Color(0xFFECEBFA),
    glowA = Color(0x297A5CFF),
    glowB = Color(0x140F9D8F),
)

private val LocalSb = staticCompositionLocalOf { DarkSb }

object Sb {
    val colors: SbColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSb.current
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Raio dos cartões de vidro (mesmo 20 do web). */
val CardShape = RoundedCornerShape(20.dp)

@Composable
fun SecureBankTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val sb = if (darkTheme) DarkSb else LightSb
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Ícones da barra de status e de navegação legíveis sobre o fundo escolhido (e não sobre o tema do sistema).
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalSb provides sb) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = shapes) {
            // Fundo da tela inteira: degradê índigo com dois brilhos (violeta no canto de cima, turquesa embaixo).
            Box(
                Modifier.fillMaxSize()
                    .background(sb.pageTop)
                    .drawBehind {
                        drawRect(Brush.linearGradient(listOf(sb.pageTop, sb.pageBottom), start = Offset.Zero, end = Offset(size.width, size.height)))
                        drawRect(Brush.radialGradient(listOf(sb.glowA, Color.Transparent), center = Offset(size.width * 0.92f, 0f), radius = size.width * 1.1f))
                        drawRect(Brush.radialGradient(listOf(sb.glowB, Color.Transparent), center = Offset(0f, size.height), radius = size.width))
                    },
            ) { content() }
        }
    }
}

/** Verde de entrada de dinheiro (o --ok do web), com contraste AA nos dois temas. */
@Composable
@ReadOnlyComposable
fun creditColor(): Color = Sb.colors.ok
