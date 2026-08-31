package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** One page's extracted text — empty when the page has no text layer. */
data class ExtractedPageText(val pageNumber: Int, val text: String)

/**
 * Extracts text per page, read-only — the source document is never
 * touched. [pages] is a page-range string ("1, 3-5", or "all"), resolved
 * the same way Split PDF's own page range is (`resolvePageIndices`,
 * `PdfSplit.kt`).
 *
 * A scanned page is pictures of words with no text layer, so it legitimately
 * comes back with an empty string here — same as `PDFTextStripper` and the
 * web version's own `extractPdfText`. Callers must check for that (see
 * `ExtractTextTool.tsx`'s "no text layer" toast) rather than silently
 * handing back a blank result that looks like the tool worked.
 *
 * `PDFTextStripper` is reused across pages, not recreated per page — its
 * own `getText` resets all per-call state (`currentPageNo`, the article/
 * character maps) at the start of every invocation, verified against the
 * real PdfBox-Android source before relying on it.
 */
fun extractText(document: PDDocument, pages: String): List<ExtractedPageText> {
    val indices = resolvePageIndices(pages, document.numberOfPages)
    val stripper = PDFTextStripper()
    return indices.map { index ->
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        ExtractedPageText(pageNumber = index + 1, text = stripper.getText(document).trimEnd())
    }
}
