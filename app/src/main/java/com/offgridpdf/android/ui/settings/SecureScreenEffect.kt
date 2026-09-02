package com.offgridpdf.android.ui.settings

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.ui.common.findActivity

/**
 * Keeps the window's `FLAG_SECURE` in step with [SecureScreenState].
 *
 * A composable effect rather than a one-shot call in `onCreate`, because the
 * setting is toggled from inside the running app: the user turns it on in
 * Settings and expects the very next trip to the recents switcher to be
 * covered, not the one after the next cold start.
 *
 * The flag lives on the window, and there is exactly one window here (this is
 * a single-Activity app), so applying it at the root of the content covers
 * every screen including Redact's page preview.
 */
@Composable
fun SecureScreenEffect() {
    val enabled = SecureScreenState.enabled
    val activity = LocalContext.current.findActivity()

    DisposableEffect(enabled, activity) {
        val window = activity?.window
        if (window != null) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { }
    }
}
