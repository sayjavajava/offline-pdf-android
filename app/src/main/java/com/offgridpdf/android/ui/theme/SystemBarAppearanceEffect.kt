package com.offgridpdf.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.offgridpdf.android.ui.common.findActivity

/**
 * Keeps the status and navigation bar icons legible against whatever this app
 * is actually painting behind them.
 *
 * `enableEdgeToEdge()` picks light or dark system bar icons from the *system's*
 * dark-mode setting. That is the right guess for an app without a theme
 * setting of its own, and the wrong one for this app: someone whose phone is
 * in light mode but who chose Dark in Settings got dark icons on this app's
 * dark background, and the clock and battery indicator disappeared. The
 * reverse combination did the same in light.
 *
 * So the appearance follows [darkTheme] — the app's own resolved theme, the
 * same value the palette comes from — rather than the system's.
 */
@Composable
fun SystemBarAppearanceEffect(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return

    // SideEffect rather than LaunchedEffect: this is pushing Compose state out
    // to a non-Compose object, and it has to be re-applied after any
    // successful recomposition that changed the theme.
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        // "Light bars" means dark icons, for a light background.
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
