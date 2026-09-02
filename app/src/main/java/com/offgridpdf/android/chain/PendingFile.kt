package com.offgridpdf.android.chain

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A file waiting to be picked up by whichever tool screen composes next —
 * the shared mechanism behind both entry points into tool chaining:
 *
 * - Incoming: `MainActivity` sets this from a `VIEW`/`SEND` intent (another
 *   app opened or shared a PDF into OffGridPDF), or a tool screen sets it
 *   after producing a result the user chose to hand to another tool
 *   ("Continue with another tool" — `ToolScaffold.kt`/`RedactScreen.kt`).
 * - Consumption: every PDF-accepting tool screen seeds its picked-file
 *   state from [consume] instead of `null` on first composition, so it
 *   opens already loaded — no picker shown, no picker needed.
 *
 * A single app-wide holder rather than a nav-argument: content `Uri`s
 * (especially the SAF/VIEW/SEND-granted ones this carries) don't survive
 * being serialized into a route string, and only one tool screen is ever
 * composed at a time, so there is never more than one real consumer.
 */
object PendingFile {
    var uri: Uri? by mutableStateOf(null)
        private set

    fun set(uri: Uri) {
        this.uri = uri
    }

    /** Reads and clears in one step, so a second screen never re-consumes the same file. */
    fun consume(): Uri? {
        val current = uri
        uri = null
        return current
    }

    fun clear() {
        uri = null
    }
}
