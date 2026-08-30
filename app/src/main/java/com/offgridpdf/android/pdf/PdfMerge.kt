package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * Merges already-open [documents] into one combined PDF's bytes, in the
 * given order. Web reference: `mergePdf` (`pdf-ops.ts`).
 *
 * Per-file loading and naming *which* file failed happens on the Android
 * side (`MergeScreen.kt`) — that's the only place a Storage-Access-
 * Framework `Uri`'s display name is available, and it's genuinely
 * Android-specific I/O, not PDF logic. This function's job is just the
 * page-copying core, kept pure and directly testable, same separation
 * `PdfSplit.kt` already established.
 */
fun mergePdf(documents: List<PDDocument>): ByteArray {
    if (documents.size < 2) {
        throw IllegalArgumentException("Please select at least 2 PDF files to merge.")
    }
    PDDocument().use { merged ->
        for (document in documents) {
            for (index in 0 until document.numberOfPages) {
                // importPage (not addPage) actually copies the page's
                // resources into the destination document — see
                // PdfSplit.kt's splitPdfToSingleFile for the same note.
                merged.importPage(document.getPage(index))
            }
        }
        val out = ByteArrayOutputStream()
        merged.save(out)
        return out.toByteArray()
    }
}
