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
 * The cache folder (`cacheDir/chain/`) is cleared on every write — a chain
 * hop only ever needs the most recent result, and this keeps an
 * interrupted chain (app killed mid-flow) from leaving stale files behind
 * indefinitely.
 */
suspend fun writeBytesToCacheUri(context: Context, bytes: ByteArray, fileName: String): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "chain")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
