package com.offgridpdf.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reworked "paper & ink" identity (see the UI redesign mockups): a warm
 * editorial look, deliberately not Material You. Dynamic (wallpaper-derived)
 * color is dropped entirely — it's exactly the "looks like every other
 * Android app" quality the redesign set out to fix — in favor of this
 * bespoke light/dark palette in both [MaterialTheme.colorScheme] (so plain
 * Material3 components still look right) and [LocalOffGridPalette] (the
 * four category accents Material3's ColorScheme has no slot for).
 */
val LocalOffGridPalette = staticCompositionLocalOf { LightOffGridPalette }

private fun colorSchemeFor(palette: OffGridPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = palette.organize,
        onPrimary = palette.onAccent,
        secondary = palette.convert,
        onSecondary = palette.onAccent,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.paperRaised,
        onSurface = palette.ink,
        surfaceVariant = palette.paperRaised,
        onSurfaceVariant = palette.inkSecondary,
        outline = palette.hairlineStrong,
        outlineVariant = palette.hairline,
    )
} else {
    lightColorScheme(
        primary = palette.organize,
        onPrimary = palette.onAccent,
        secondary = palette.convert,
        onSecondary = palette.onAccent,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.paperRaised,
        onSurface = palette.ink,
        surfaceVariant = palette.paperRaised,
        onSurfaceVariant = palette.inkSecondary,
        outline = palette.hairlineStrong,
        outlineVariant = palette.hairline,
    )
}

private val OffGridShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val OffGridTypography = Typography(
    headlineLarge = TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.1).sp),
    titleLarge = TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun OffGridPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkOffGridPalette else LightOffGridPalette

    CompositionLocalProvider(LocalOffGridPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette, darkTheme),
            typography = OffGridTypography,
            shapes = OffGridShapes,
            content = content,
        )
    }
}
