package com.offgridpdf.android.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "privacy_prefs"
private const val KEY_BLOCK_SCREEN_CAPTURE = "block_screen_capture"

/** Plain [android.content.SharedPreferences] — a single flag, same reasoning as `ThemePreferenceStore`. */
class SecureScreenPreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Boolean = prefs.getBoolean(KEY_BLOCK_SCREEN_CAPTURE, false)

    fun set(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_SCREEN_CAPTURE, enabled).apply()
    }
}

/**
 * Whether to set `WindowManager.LayoutParams.FLAG_SECURE` on the app's
 * window, which stops Android from putting this app's contents in
 * screenshots, screen recordings, and the thumbnail the recents switcher
 * shows.
 *
 * Worth having in an app that opens documents the user did not want to hand
 * to anyone: the recents thumbnail in particular is taken automatically when
 * the app is backgrounded, so leaving Redact mid-edit puts a picture of the
 * unredacted page into a surface other apps and onlookers can reach.
 *
 * **Off by default, deliberately.** It is a real trade: with it on, the user
 * cannot screenshot their own work either, and some screen-sharing and
 * accessibility tooling sees a black window. Silently breaking screenshots
 * for everyone to defend against a threat most people do not have is the
 * wrong default; the people who need it can say so.
 *
 * Same shape as [com.offgridpdf.android.ui.theme.ThemeState]: one observable
 * holder, loaded once at startup, so a change in Settings takes effect on the
 * live window rather than at the next launch.
 */
object SecureScreenState {
    var enabled: Boolean by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        enabled = SecureScreenPreferenceStore(context).get()
    }

    fun update(context: Context, newValue: Boolean) {
        enabled = newValue
        SecureScreenPreferenceStore(context).set(newValue)
    }
}
