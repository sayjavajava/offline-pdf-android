package com.offgridpdf.android.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands a just-produced file to another app through the system share sheet.
 *
 * The file is copied into a private cache folder and shared through this
 * app's own [FileProvider], rather than forwarding the `content://` Uri the
 * user picked in the save dialog. Forwarding a Storage Access Framework
 * grant to a third app is not reliably permitted, and when it fails it fails
 * inside the *receiving* app, which is a miserable thing to diagnose from a
 * bug report. A copy under an authority this app owns always works.
 *
 * The folder is cleared before each write and again on app start
 * ([clearShareCache], from `OffGridPdfApplication`), for the same reason the
 * chain cache is: a document this app was trusted to process should not sit
 * in cache indefinitely afterwards.
 *
 * Sharing only ever happens because the user pressed the button. Nothing
 * here runs on its own, and nothing leaves the device unless the app they
 * pick in the sheet sends it.
 */
private const val SHARE_DIR = "share"

suspend fun shareFileIntent(context: Context, file: SavedFile): Intent {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val target = File(dir, sanitizeFileName(file.displayName))
        target.writeBytes(file.bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
    return Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Deletes anything left in the share cache. Called once on app start. */
suspend fun clearShareCache(context: Context) {
    withContext(Dispatchers.IO) {
        File(context.cacheDir, SHARE_DIR).deleteRecursively()
    }
}

/**
 * A display name comes from the user and from a content provider, so it can
 * contain anything -- including a path separator, which would put the copy
 * somewhere other than the share folder. Keep it to a plain file name.
 */
internal fun sanitizeFileName(name: String): String {
    val cleaned = name.map { c -> if (c == '/' || c == '\\' || c.isISOControl()) '_' else c }
        .joinToString("")
        .trim()
        .trimStart('.')
    return cleaned.ifBlank { "document" }
}
