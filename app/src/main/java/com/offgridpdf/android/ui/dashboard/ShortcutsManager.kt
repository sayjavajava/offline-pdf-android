package com.offgridpdf.android.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.offgridpdf.android.MainActivity

/** Set on the launch [Intent] of a shortcut built here; read by `MainActivity`. */
const val ACTION_OPEN_TOOL = "com.offgridpdf.android.action.OPEN_TOOL"
const val EXTRA_TOOL_ID = "tool_id"

/**
 * Home-screen "long press the launcher icon" shortcuts (Android's dynamic
 * shortcuts API), rebuilt from [RecentToolsStore] every time it records a
 * new use — nearly free, since that tracking already exists for the
 * Dashboard's own Recent row. Each shortcut launches straight into its
 * tool, skipping the dashboard entirely (`OffGridNavHost.kt`,
 * `chain/PendingNavigation.kt`).
 */
object ShortcutsManager {
    private const val MAX_SHORTCUTS = 4

    fun update(context: Context, recentToolIds: List<String>) {
        val shortcuts = recentToolIds
            .mapNotNull { id -> pdfTools.find { it.id == id } }
            .take(MAX_SHORTCUTS)
            .map { tool -> shortcutFor(context, tool) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun shortcutFor(context: Context, tool: PdfTool): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TOOL)
            .putExtra(EXTRA_TOOL_ID, tool.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return ShortcutInfoCompat.Builder(context, tool.id)
            .setShortLabel(tool.title)
            .setIcon(IconCompat.createWithResource(context, tool.icon))
            .setIntent(intent)
            .build()
    }
}
