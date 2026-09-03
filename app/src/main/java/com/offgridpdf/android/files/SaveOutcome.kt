package com.offgridpdf.android.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A file this app produced and successfully wrote somewhere.
 *
 * Carries the bytes as well as the destination because the two are used for
 * different things: the file is already saved where the user asked, and the
 * bytes are what a later Share writes into a cache copy it is allowed to
 * hand to another app (see `shareFile`).
 */
data class SavedFile(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String,
) {
    // Data class equality on a ByteArray compares references, which is
    // never what a caller means. These are only compared by Compose to
    // decide whether to recompose, so identity is the honest answer -- but
    // spell it out rather than leaving the array's surprising default.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * The result of trying to save. Two cases rather than one message string,
 * because the screen needs to tell them apart: a success gets a completion
 * banner with somewhere to go next, a failure gets an error.
 */
sealed interface SaveOutcome {
    val message: String

    data class Saved(override val message: String, val file: SavedFile) : SaveOutcome

    data class Failed(override val message: String) : SaveOutcome
}

/** The `Saved` file, or null. Saves every call site an `as?` cast. */
val SaveOutcome.savedFileOrNull: SavedFile?
    get() = (this as? SaveOutcome.Saved)?.file

/**
 * The name the user actually chose in the system save dialog, for showing
 * back to them and for naming the share copy.
 *
 * Falls back to the Uri's last path segment, and then to a generic name: a
 * provider is not obliged to answer this query, and a missing display name
 * is not a reason to fail a save that already succeeded.
 */
internal suspend fun queryDisplayName(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull()
        queried ?: uri.lastPathSegment ?: "document"
    }
