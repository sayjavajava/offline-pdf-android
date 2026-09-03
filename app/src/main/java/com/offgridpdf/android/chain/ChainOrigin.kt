package com.offgridpdf.android.chain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The original file's base name (no extension, no operation suffix), carried
 * alongside [PendingFile] when a chained result is handed to the next tool —
 * so that tool's own chained result can still be named from the *original*
 * file rather than from "chained-result", however many hops preceded it.
 *
 * Only meaningful together with a [PendingFile] set by the same hand-off:
 * cleared wherever [PendingFile] is set from something other than a chain
 * continuation (a fresh `VIEW`/`SEND` intent), so a later chain can't
 * inherit an unrelated earlier origin.
 */
object ChainOrigin {
    var baseName: String? by mutableStateOf(null)
        private set

    fun set(baseName: String) {
        this.baseName = baseName
    }

    /** Reads and clears in one step, mirroring [PendingFile.consume]. */
    fun consume(): String? {
        val current = baseName
        baseName = null
        return current
    }

    fun clear() {
        baseName = null
    }
}
