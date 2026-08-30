package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * Rebuilds [document] keeping only [pages], in that order — a page
 * omitted from [pages] is deleted; listed more than once, it's
 * duplicated. Web reference: `rearrangePdf` (`pdf-ops.ts`).
 *
 * Unlike Rotate (A-5): this does **not** default to "all" when blank
 * (there is no meaningful "all" here — that would just be a no-op
 * copy, so a blank range is rejected outright), and does **not**
 * deduplicate requested pages — duplicates are the whole point of
 * "list a page twice to duplicate it". Same `parsePageRange` core as
 * every other page-range tool, different validation and dedup choices
 * layered on top, each correct for what this specific tool means.
 */
fun rearrangePdf(document: PDDocument, pages: String): ByteArray {
    if (pages.isBlank()) {
        throw IllegalArgumentException("Enter the pages to keep, in the desired order.")
    }

    val parsed = parsePageRange(pages, document.numberOfPages)
    if (parsed.errors.isNotEmpty()) {
        throw IllegalArgumentException(parsed.errors.joinToString(" "))
    }
    if (parsed.indices.isEmpty()) {
        throw IllegalArgumentException("Invalid page range specified.")
    }

    PDDocument().use { newDocument ->
        for (index in parsed.indices) {
            newDocument.importPage(document.getPage(index))
        }
        val out = ByteArrayOutputStream()
        newDocument.save(out)
        return out.toByteArray()
    }
}
