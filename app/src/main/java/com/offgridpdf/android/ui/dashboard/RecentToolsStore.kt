package com.offgridpdf.android.ui.dashboard

import android.content.Context

private const val PREFS_NAME = "recent_tools"
private const val KEY_RECENT_IDS = "recent_ids"
private const val MAX_STORED = 8

/**
 * Backs the Dashboard's Recent row: the last few tool ids opened, most
 * recent first, deduped. Plain [android.content.SharedPreferences] rather
 * than DataStore — this is a single small string, not worth a new
 * dependency for.
 *
 * Also drives the home-screen shortcuts ([ShortcutsManager]) — every
 * recorded use rebuilds them from the same list, so the two stay in sync
 * for free rather than needing their own separate tracking.
 */
class RecentToolsStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recentToolIds(): List<String> =
        prefs.getString(KEY_RECENT_IDS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun recordUsed(toolId: String) {
        val updated = (listOf(toolId) + recentToolIds().filter { it != toolId }).take(MAX_STORED)
        prefs.edit().putString(KEY_RECENT_IDS, updated.joinToString(",")).apply()
        ShortcutsManager.update(context, updated)
    }
}
