package com.offgridpdf.android.files

import android.content.Context
import android.net.Uri
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.common.userMessageFor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The outcome of [runOnEachPdf] — exactly one of [singleBytes] or [zipBytes]
 * is set (never both), matching whether [files] held one entry or several.
 * [failures] is never fatal to the rest of a batch: one bad file (wrong
 * password, a page range that doesn't exist in it, corrupt data) is
 * reported and skipped, not a reason to abandon the others.
 */
data class BatchRunResult(
    val singleBytes: ByteArray?,
    val zipBytes: ByteArray?,
    val successCount: Int,
    val failures: List<String>,
)

/**
 * Backs batch mode (Compress/Watermark/Rotate/Page Numbers — the tools
 * where the same settings genuinely make sense applied to more than one
 * file at once, unlike e.g. Rearrange or Split whose whole point is
 * per-document specifics). A single file behaves exactly like every tool
 * screen's own original one-file flow always has; more than one runs
 * [operate] against each with the same [password], zips the successes
 * together (collision-safe filenames, same suffixing scheme
 * `SplitScreen.kt`'s `zipEntriesFor` already established), and collects
 * per-file failures for the caller to report rather than raising them.
 *
 * Runs on `Dispatchers.Default` (see `PdfTask.kt` for why every tool's PDF
 * work needs to): [operate] is CPU-bound, and a caller's
 * `rememberCoroutineScope()` would otherwise run it on the main thread.
 *
 * [operate] and its `IllegalArgumentException`s are the one piece deliberately
 * left to each screen — Compress/Watermark/Rotate/Page Numbers's own
 * operations (`compressPdf`, `addWatermark`, `rotatePdf`, `addPageNumbers`)
 * differ, but every one of them already throws that same exception type
 * for a bad, user-facing-message-bearing input.
 */
suspend fun runOnEachPdf(
    context: Context,
    files: List<Uri>,
    password: String,
    zipEntrySuffix: String,
    operate: (PDDocument) -> ByteArray,
): BatchRunResult = withContext(Dispatchers.Default) {
    if (files.size <= 1) {
        val uri = files.firstOrNull()
            ?: return@withContext BatchRunResult(null, null, 0, emptyList())
        return@withContext when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
            is PdfLoadResult.Success -> {
                try {
                    BatchRunResult(operate(result.document), null, 1, emptyList())
                } catch (e: Exception) {
                    BatchRunResult(null, null, 0, listOf(userMessageFor(e)))
                } catch (e: OutOfMemoryError) {
                    BatchRunResult(null, null, 0, listOf(TOO_LARGE_MESSAGE))
                } finally {
                    result.document.close()
                }
            }
            PdfLoadResult.PasswordRequired -> BatchRunResult(
                null,
                null,
                0,
                listOf(if (password.isBlank()) "This PDF needs a password." else "Wrong password — try again."),
            )
            is PdfLoadResult.Failure -> BatchRunResult(null, null, 0, listOf(result.message))
        }
    }

    val entries = mutableListOf<ZipEntryData>()
    val failures = mutableListOf<String>()
    val seen = mutableMapOf<String, Int>()
    for (uri in files) {
        val baseName = suggestedBaseName(uri)
        when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
            is PdfLoadResult.Success -> {
                try {
                    val bytes = operate(result.document)
                    val occurrence = (seen[baseName] ?: 0) + 1
                    seen[baseName] = occurrence
                    val disambiguator = if (occurrence > 1) "-$occurrence" else ""
                    entries += ZipEntryData("$baseName$disambiguator$zipEntrySuffix.pdf", bytes)
                } catch (e: Exception) {
                    failures += "$baseName: ${userMessageFor(e)}"
                } catch (e: OutOfMemoryError) {
                    failures += "$baseName: $TOO_LARGE_MESSAGE"
                } finally {
                    result.document.close()
                }
            }
            PdfLoadResult.PasswordRequired -> failures += "$baseName: password required"
            is PdfLoadResult.Failure -> failures += "$baseName: ${result.message}"
        }
    }

    val zipBytes = if (entries.isNotEmpty()) createZip(entries) else null
    BatchRunResult(null, zipBytes, entries.size, failures)
}

/**
 * The zip-result success message every batch-mode screen shows — one
 * formatting rule shared by all four rather than four near-identical copies.
 * [verb] is each tool's own past tense ("Compressed", "Watermarked",
 * "Rotated", "Numbered"); only meaningful when [result] came from more than
 * one file (`result.zipBytes != null`).
 */
fun batchResultMessage(verb: String, result: BatchRunResult): String {
    val successCount = result.successCount
    val base = "$verb $successCount file${if (successCount == 1) "" else "s"}, saved as one zip."
    if (result.failures.isEmpty()) return base
    val shown = result.failures.take(5).joinToString("; ")
    val more = if (result.failures.size > 5) ", and ${result.failures.size - 5} more" else ""
    return "$base ${result.failures.size} failed: $shown$more."
}
