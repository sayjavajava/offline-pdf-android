package com.offgridpdf.android.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
 * Reads the full contents of a picked `Uri` as raw bytes — for tools that
 * need the file's own bytes directly (batch image-to-PDF, A-12) rather
 * than a parsed `PDDocument` (`loadPdfFromUri`, `PdfLoaderAndroid.kt`).
 */
suspend fun readBytesFromUri(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected file.")
        stream.use { it.readBytes() }
    }

/**
 * The real filename a picker or save dialog knows this `Uri` by.
 *
 * Not `uri.lastPathSegment`. For a `content://` `Uri`, that is the last
 * segment of the provider's own opaque document id — for the local storage
 * provider it happens to look plausible (`document:155387` still reads as
 * junk, but at least it's consistent), and for other providers it can be
 * unrelated to the file entirely (`msf:155387` was a real one, shown to a
 * user as if it were the picked file's name). `DISPLAY_NAME` is a column
 * [OpenableColumns] documents as required for any provider backing
 * `ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT`, so this resolves
 * correctly for every picker and every save dialog in the app; the fallback
 * below is only for a provider that violates that contract.
 *
 * Used both for a file the user picked (open) and one they just named in
 * the system save dialog (create) — same query, same guarantee, either
 * direction.
 */
suspend fun queryDisplayName(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull() ?: "document"
    }

/**
 * A reasonable filename base for a save-suggestion, e.g. for
 * `"${suggestedBaseName(context, uri)}_rotated90.pdf"`. Extracted here after
 * it was first written as a private copy in `SplitScreen.kt` (A-3) — every
 * single-file tool from here on needs the same thing, so it belongs in
 * shared infra rather than being pasted into each new screen.
 */
suspend fun suggestedBaseName(context: Context, uri: Uri): String {
    val name = queryDisplayName(context, uri)
    return name.removeSuffix(".pdf").removeSuffix(".PDF")
}
