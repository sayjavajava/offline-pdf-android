package com.offgridpdf.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(primary = SeedPrimary, secondary = SeedSecondary)
private val DarkColors = darkColorScheme(primary = SeedSecondary, secondary = SeedPrimary)

/**
 * Dynamic color (Android 12+, per-device wallpaper-derived palette) when
 * available, falling back to the seed palette above otherwise — same
 * "respect the platform, degrade gracefully" approach as the web app
 * respecting the browser's own light/dark preference.
 */
@Composable
fun OffGridPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
