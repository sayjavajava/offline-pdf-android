package com.offgridpdf.android.files

import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backs tool chaining ("Continue with another tool" — `ToolScaffold.kt`,
 * `RedactScreen.kt`): a just-produced result never has a real SAF `Uri` of
 * its own (the user hasn't chosen a save location for it, or chose one but
 * the next tool shouldn't have to re-open the system picker to find it
 * again), so this writes it to a private cache folder and mints a
 * `content://` `Uri` through [FileProvider] the next tool screen's
 * `loadPdfFromUri` can open exactly like any SAF-picked file.
 *
 * The cache folder ([CHAIN_DIR]) is cleared on every write — a chain hop
 * only ever needs the most recent result — and again on app start
 * ([clearChainCache], from `OffGridPdfApplication`). "On the next write"
 * alone was not enough: chain once and close the app, and that document
 * stayed on disk until the next chain, which might be never. For an app
 * whose whole point is that documents don't go anywhere, a decrypted or
 * redacted PDF sitting in the cache indefinitely is the wrong default.
 */
private const val CHAIN_DIR = "chain"

suspend fun writeBytesToCacheUri(context: Context, bytes: ByteArray, fileName: String): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CHAIN_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

/**
 * Deletes anything left in the chain cache. Called once on app start, so a
 * chain that was never completed — or was completed and then abandoned —
 * doesn't leave a document on disk for the life of the install.
 *
 * Safe to call while nothing is chaining: a missing directory is a no-op.
 */
suspend fun clearChainCache(context: Context) {
    withContext(Dispatchers.IO) {
        File(context.cacheDir, CHAIN_DIR).deleteRecursively()
    }
}
