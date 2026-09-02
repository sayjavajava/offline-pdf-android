package com.offgridpdf.android

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

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_TOOL -> {
                intent.getStringExtra(EXTRA_TOOL_ID)?.let { toolId ->
                    PendingNavigation.set("tool/$toolId")
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    PendingFile.set(uri)
                    PendingNavigation.set(ROUTE_DASHBOARD)
                }
            }
            Intent.ACTION_SEND -> {
                sharedPdfUri(intent)?.let { uri ->
                    PendingFile.set(uri)
                    PendingNavigation.set(ROUTE_DASHBOARD)
                }
            }
        }
    }

    private fun sharedPdfUri(intent: Intent): Uri? =
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
}
