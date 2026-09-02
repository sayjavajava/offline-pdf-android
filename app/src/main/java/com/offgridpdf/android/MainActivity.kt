package com.offgridpdf.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.chain.PendingNavigation
import com.offgridpdf.android.chain.ROUTE_DASHBOARD
import com.offgridpdf.android.ui.dashboard.ACTION_OPEN_TOOL
import com.offgridpdf.android.ui.dashboard.EXTRA_TOOL_ID
import com.offgridpdf.android.ui.dashboard.pdfTools
import com.offgridpdf.android.ui.navigation.OffGridNavHost
import com.offgridpdf.android.ui.theme.OffGridPdfTheme

/**
 * Single-Activity app (all navigation is `OffGridNavHost`'s Compose Nav
 * Graph, not separate Activities) — so both a fresh launch (`onCreate`) and
 * an already-running one being handed a new shortcut/share/view intent
 * (`onNewIntent`, enabled by `android:launchMode="singleTop"` in the
 * manifest) route through the same [handleIncomingIntent].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            OffGridPdfTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OffGridNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Everything here arrives from outside the app, so none of it is trusted.
     * This Activity is `exported="true"` (it has to be — that is what makes
     * "open with" and "share to" work), which means any installed app can
     * start it with any action, any extras and any `Uri`, not just the
     * shortcuts and file managers these branches are written for.
     */
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_TOOL -> {
                // Only ids this app actually has a tool for. An arbitrary
                // string reached navController.navigate() before, and one
                // containing a "/" (or an empty one) does not match the
                // "tool/{toolId}" route, so navigate() threw and the app
                // crashed on launch — trivially triggerable by any other app.
                intent.getStringExtra(EXTRA_TOOL_ID)
                    ?.takeIf { id -> pdfTools.any { it.id == id } }
                    ?.let { toolId -> PendingNavigation.set("tool/$toolId") }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.takeIf { it.isReadableContent() }?.let { uri ->
                    PendingFile.set(uri)
                    PendingNavigation.set(ROUTE_DASHBOARD)
                }
            }
            Intent.ACTION_SEND -> {
                sharedPdfUri(intent)?.takeIf { it.isReadableContent() }?.let { uri ->
                    PendingFile.set(uri)
                    PendingNavigation.set(ROUTE_DASHBOARD)
                }
            }
        }
    }

    private fun sharedPdfUri(intent: Intent): Uri? =
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
}

/**
 * `content://` only — the scheme the Storage Access Framework, file managers
 * and share sheets all hand over, and the only one that carries a real
 * per-Uri grant.
 *
 * A `file://` Uri notably does not: it is just a path, opened with this app's
 * own uid, so another app could aim one at this app's own private storage and
 * have a tool read it. Nothing is sent anywhere (there is no INTERNET
 * permission, and results go only where the user picks in the save dialog),
 * so this is defence in depth rather than a plugged leak — but there is no
 * legitimate sender that needs `file://`, so there is no cost to refusing it.
 */
private fun Uri.isReadableContent(): Boolean = scheme == ContentResolver.SCHEME_CONTENT
