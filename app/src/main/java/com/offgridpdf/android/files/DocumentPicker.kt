package com.offgridpdf.android.files

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Storage-Access-Framework wrappers (`ANDROID_IMPLEMENTATION_PLAN.md` A-2,
 * tool-docs repo) — this app's equivalent of the web app's
 * `<input type="file">` (open) and blob-download (`download.ts`, save)
 * pair. No permission is requested for either: SAF hands back a
 * caller-scoped `Uri` good for this app only, which is why neither
 * `READ_EXTERNAL_STORAGE` nor `WRITE_EXTERNAL_STORAGE` appears in the
 * manifest — consistent with this project's offline/least-access stance.
 */

/** Pick a single file. Returns null if the user backs out of the picker. */
@Composable
fun rememberOpenDocumentLauncher(
    onResult: (Uri?) -> Unit,
): androidx.activity.result.ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onResult)

/** Pick several files at once (merge, batch image-to-PDF, etc. — A-4/A-12). */
@Composable
fun rememberOpenMultipleDocumentsLauncher(
    onResult: (List<Uri>) -> Unit,
): androidx.activity.result.ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onResult)

/**
 * Let the user choose where a result file is saved, returning a `Uri` this
 * app can write to. [suggestedName] is a hint, not a guarantee — the user
 * can rename it in the system picker.
 */
@Composable
fun rememberCreateDocumentLauncher(
    mimeType: String,
    onResult: (Uri?) -> Unit,
): androidx.activity.result.ActivityResultLauncher<String> =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType), onResult)

/** Writes [bytes] to a `Uri` obtained from [rememberCreateDocumentLauncher]. */
suspend fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open the chosen location for writing.")
        stream.use { it.write(bytes) }
    }
}

/**
 * A reasonable filename base for a save-suggestion, e.g. for
 * `"${suggestedBaseName(uri)}_rotated90.pdf"`. Extracted here after it was
 * first written as a private copy in `SplitScreen.kt` (A-3) — every
 * single-file tool from here on needs the same thing, so it belongs in
 * shared infra rather than being pasted into each new screen.
 */
fun suggestedBaseName(uri: Uri): String {
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    return name.removeSuffix(".pdf").removeSuffix(".PDF")
}
