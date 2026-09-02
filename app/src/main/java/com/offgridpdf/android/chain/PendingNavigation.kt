package com.offgridpdf.android.chain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The dashboard's nav route — defined here rather than in
 * `OffGridNavHost.kt` so tool screens (which set [PendingNavigation] but
 * shouldn't otherwise know navigation-graph internals) have something to
 * target without depending on the `ui.navigation` package.
 */
const val ROUTE_DASHBOARD = "dashboard"

/**
 * A nav route waiting to be jumped to once `OffGridNavHost` is composed —
 * set by `MainActivity` when the app is launched from a home-screen
 * shortcut (`ShortcutsManager.kt`) that should open straight into a
 * specific tool, bypassing the dashboard. `NavHost`'s own `NavController`
 * doesn't exist yet at the point `MainActivity.onCreate`/`onNewIntent`
 * runs, so this is consumed a moment later from a `LaunchedEffect` instead
 * of navigated to directly.
 */
object PendingNavigation {
    var target: String? by mutableStateOf(null)
        private set

    fun set(route: String) {
        target = route
    }

    fun consume(): String? {
        val current = target
        target = null
        return current
    }
}
