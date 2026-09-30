package com.tonex.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Paleta tirada do firmware (`source/main/web/style.css`), não inventada:
 * fundo #1F1F1F, painel #2A2A2A, laranja #FB9230, dourado #D1A60C,
 * azul #3890D5. A fonte é a Montserrat que o display do controlador já usa.
 */
val Screen = Color(0xFF1F1F1F)
val Panel = Color(0xFF2A2A2A)
val PanelRaised = Color(0xFF333333)
val Line = Color(0xFF3A3A3A)
val Orange = Color(0xFFFB9230)
val Gold = Color(0xFFD1A60C)
val Blue = Color(0xFF3890D5)
val Muted = Color(0xFFAEB1B4)
val Dim = Color(0xFF6E7275)
val Stage = Color(0xFF0D0D0E)

private val Scheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A1206),
    secondary = Gold,
    onSecondary = Color(0xFF1A1206),
    background = Screen,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Color(0xFFE85D4C),
)

/** `Montserrat` com fallback — se o APK não empacotar a fonte, cai no sans-serif. */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
    ),
)

@Composable
fun TonexTheme(content: @Composable () -> Unit) {
    // O app é sempre escuro, com ou sem dark mode do sistema: espelha o display
    // do pedal e evita um flash branco quando o músico está no palco.
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
