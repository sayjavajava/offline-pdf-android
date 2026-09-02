package com.offgridpdf.android.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A user's explicit override, or [System] to keep following the device setting (the default). */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

private const val PREFS_NAME = "theme_prefs"
private const val KEY_MODE = "mode"

/** Plain [android.content.SharedPreferences] — a single small string, same reasoning as `RecentToolsStore`. */
class ThemePreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ThemeMode =
        prefs.getString(KEY_MODE, null)?.let { stored -> ThemeMode.entries.find { it.name == stored } } ?: ThemeMode.System

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}

/**
 * The app-wide theme choice, read by [OffGridPdfTheme]. A single observable
 * holder rather than a per-screen preference read — `OffGridPdfTheme` wraps
 * the whole app once (`MainActivity`), so every screen needs to react to a
 * change made from Settings immediately, without a restart.
 *
 * [initialize] must run once, before the first composition (`MainActivity
 * .onCreate`, before `setContent`) — a `SharedPreferences` read is fast and
 * local, so doing it synchronously there (rather than as a suspend/async
 * load with a loading state) keeps `OffGridPdfTheme` simple and avoids a
 * visible theme flash on cold start.
 */
object ThemeState {
    var mode: ThemeMode by mutableStateOf(ThemeMode.System)
        private set

    fun initialize(context: Context) {
        mode = ThemePreferenceStore(context).get()
    }

    fun update(context: Context, newMode: ThemeMode) {
        mode = newMode
        ThemePreferenceStore(context).set(newMode)
    }
}
