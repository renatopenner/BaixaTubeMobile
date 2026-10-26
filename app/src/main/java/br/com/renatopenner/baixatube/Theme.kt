package br.com.renatopenner.baixatube

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* paleta herdada do BaixaTube desktop (app/theme.py) */

private val DarkBg = Color(0xFF12151A)
private val DarkSurface = Color(0xFF191D24)
private val DarkSurfaceAlt = Color(0xFF21262F)
private val DarkBorder = Color(0xFF2C333E)
private val DarkText = Color(0xFFEEF2F8)
private val DarkMuted = Color(0xFFA3AEC0)
private val DarkAccent = Color(0xFFD93025)
private val DarkOk = Color(0xFF3DDC97)
private val DarkWarn = Color(0xFFF2C14E)
private val DarkErr = Color(0xFFFF6B6B)

private val LightBg = Color(0xFFF4F6F9)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceAlt = Color(0xFFEEF1F6)
private val LightBorder = Color(0xFFDBE1E9)
private val LightText = Color(0xFF151A21)
private val LightMuted = Color(0xFF556070)
private val LightAccent = Color(0xFFC62828)
private val LightOk = Color(0xFF0F8A5F)
private val LightWarn = Color(0xFF9A6B00)
private val LightErr = Color(0xFFC0271F)

/** cor de sucesso/erro fora do ColorScheme padrão */
object ExtraColors {
    var ok: Color = DarkOk
    var warn: Color = DarkWarn
}

private val DarkScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.White,
    primaryContainer = DarkAccent,
    onPrimaryContainer = Color.White,
    secondary = DarkOk,
    onSecondary = Color(0xFF04231A),
    tertiary = DarkWarn,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkErr,
    onError = Color(0xFF240404),
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightAccent,
    onPrimaryContainer = Color.White,
    secondary = LightOk,
    onSecondary = Color.White,
    tertiary = LightWarn,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightErr,
    onError = Color.White,
)

val Chroma = RoundedCornerShape(18)

@Composable
fun BaixaTubeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    ExtraColors.ok = if (dark) DarkOk else LightOk
    ExtraColors.warn = if (dark) DarkWarn else LightWarn
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
