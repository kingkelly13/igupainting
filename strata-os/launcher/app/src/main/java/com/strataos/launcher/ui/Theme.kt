package com.strataos.launcher.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.strataos.launcher.R

/** Design tokens from the Strata Foundations board. */
object StrataColors {
    val Ink = Color(0xFF0D0E10)
    val Scrim = Color(0xFF0A0B0D)
    val Sheet = Color(0xFF141619)
    val Surface = Color(0xFF17191D)
    val Raised = Color(0xFF212429)
    val Line = Color(0xFF2C3036)
    val LineStrong = Color(0xFF4A4F57)
    val Text = Color(0xFFF3F1EC)
    val Muted = Color(0xFFA9ADB4)
    val OnAccent = Color(0xFF1A1306)
    val OnAccentMuted = Color(0xFF4A3410)

    /** Wallpaper bands, top to bottom. */
    val Bands = listOf(
        Color(0xFF111316),
        Color(0xFF15181C),
        Color(0xFF1A1D22),
        Color(0xFF1F2328),
        Color(0xFF252A30),
    )
}

/** The single accent colour. It only ever marks state: on, selected or live. */
enum class Accent(val label: String, val color: Color) {
    Amber("Amber", Color(0xFFFFB547)),
    Mint("Mint", Color(0xFF6ED3C1)),
    Periwinkle("Periwinkle", Color(0xFF9DB4FF)),
    Coral("Coral", Color(0xFFFF7A59)),
}

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
)

val LocalAccent = staticCompositionLocalOf { Accent.Amber.color }

/** Space Grotesk display style used for the clock, big numbers and titles. */
fun displayStyle(size: TextUnit, weight: FontWeight = FontWeight.Medium): TextStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size,
    letterSpacing = (-0.03).em,
    color = StrataColors.Text,
)

@Composable
fun StrataTheme(accent: Color, content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = Inter, fontSize = 14.sp, color = StrataColors.Text)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = StrataColors.OnAccent,
        background = StrataColors.Ink,
        onBackground = StrataColors.Text,
        surface = StrataColors.Surface,
        onSurface = StrataColors.Text,
        surfaceVariant = StrataColors.Raised,
        onSurfaceVariant = StrataColors.Muted,
        surfaceContainer = StrataColors.Raised,
        outline = StrataColors.Line,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalAccent provides accent,
            LocalTextStyle provides base,
            LocalContentColor provides StrataColors.Text,
            content = content,
        )
    }
}
