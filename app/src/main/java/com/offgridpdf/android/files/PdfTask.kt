package com.offgridpdf.android.files

import android.content.Context
import android.net.Uri
import com.offgridpdf.android.ui.common.userMessageFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two things every tool screen does after the user taps Run: some PDF
 * work, then a write to the location the save picker returned. Both used to
 * happen on whatever dispatcher the screen's `rememberCoroutineScope()`
 * launched on — which is the **main thread**, since a composition's scope
 * inherits `AndroidUiDispatcher.Main`. Only `loadPdfFromUri`/
 * `readBytesFromUri`/`writeBytesToUri` ever hopped off it, so a file was
 * loaded off-thread and then parsed, rendered, re-saved and encoded on the
 * UI thread: a frozen screen (the progress indicator could not even
 * animate) and an ANR on anything large.
 *
 * These two helpers are where that gets fixed once, rather than in twenty
 * screens' worth of near-identical `withContext`/`try`/`catch` blocks.
 */

/**
 * Shown when an operation runs out of memory — shared with [runOnEachPdf]
 * (`BatchRun.kt`) so a batch reports the same thing per file that a single
 * run reports for itself.
 */
const val TOO_LARGE_MESSAGE =
    "This file is too large to process on this device. Try fewer pages, or a smaller scale."

/** The outcome of [runPdfTask] — a value, or a message already fit to show the user. */
sealed interface PdfTaskResult<out T> {
    data class Success<T>(val value: T) : PdfTaskResult<T>
    data class Failure(val message: String) : PdfTaskResult<Nothing>
}

/**
 * Runs [block] on [Dispatchers.Default] (PDF work is CPU-bound — parsing,
 * rendering, image encoding — not IO-bound) and converts any failure into a
 * message rather than letting it escape.
 *
 * That matters more than it looks: `scope.launch { }` has no exception
 * handler, so before this every unexpected throw from PdfBox — an
 * `IOException` out of `PDDocument.save()`, a `RuntimeException` from a
 * malformed page tree — killed the whole app rather than showing an error.
 * Most screens caught only `IllegalArgumentException` (their own validation
 * failures), which covered none of that.
 *
 * [OutOfMemoryError] is caught deliberately, and only here: a PDF tool's
 * most likely OOM is a single large allocation (a page bitmap, a whole
 * output document's bytes) that fails and is immediately unreachable, so
 * the heap recovers and telling the user the file was too big beats
 * dying. This is the one boundary that catches an `Error`; do not widen it
 * to `Throwable` anywhere else.
 */
suspend fun <T> runPdfTask(block: () -> T): PdfTaskResult<T> =
    withContext(Dispatchers.Default) {
        try {
            PdfTaskResult.Success(block())
        } catch (e: Exception) {
            PdfTaskResult.Failure(userMessageFor(e))
        } catch (e: OutOfMemoryError) {
            PdfTaskResult.Failure(TOO_LARGE_MESSAGE)
        }
    }

/**
 * Writes [bytes] to [uri] and reports whether that worked. Every save
 * launcher in the app used to call `writeBytesToUri` bare inside
 * `scope.launch { }`: a revoked SAF permission, a full disk or an ejected
 * card threw an `IOException` that nothing caught, crashing the app at the
 * very last step of a successful operation.
 *
 * Returns [SaveOutcome] rather than a bare message so the screen can tell a
 * finished job from a failed one. It used to return a String either way,
 * which meant "Your PDF has been split successfully." and "Could not open
 * the chosen location for writing." rendered identically, in the same small
 * grey text, and neither announced itself as the end of the run.
 *
 * The display name and MIME type are read back from [uri] rather than
 * passed in: the user has just named the file in the system dialog, and
 * that name is the one to show them and to reuse when sharing.
 */
suspend fun saveResult(context: Context, uri: Uri, bytes: ByteArray, successMessage: String): SaveOutcome =
    try {
        writeBytesToUri(context, uri, bytes)
        SaveOutcome.Saved(
            message = successMessage,
            file = SavedFile(
                bytes = bytes,
                displayName = queryDisplayName(context, uri),
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
            ),
        )
    } catch (e: Exception) {
        SaveOutcome.Failed(userMessageFor(e))
    }
