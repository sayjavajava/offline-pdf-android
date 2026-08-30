package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * A page-range parse result. `errors` collects problems per invalid
 * segment rather than failing on the first one, so `"1-3, 99"` against a
 * 5-page document reports pages 1-3 as valid and names 99 as the problem,
 * instead of silently dropping the whole range or returning nothing.
 */
data class ParsePageRangeResult(val indices: List<Int>, val errors: List<String>)

private val DIGITS_ONLY = Regex("^\\d+$")

/**
 * Parses a page-range string (e.g. "1, 3-5, 8") into 0-based page
 * indices. Ported from the web app's `parsePageRange` (`pdf-ops.ts`) —
 * behavior, not translated line-for-line, but the error message text is
 * copied verbatim on purpose so `PdfSplitTest.kt` can assert against the
 * exact same cases `parsePageRange.test.ts` does.
 *
 * Deliberate behavior, carried over from the web version: input order is
 * preserved and duplicates are kept — `"5,1"` -> `[4, 0]`, `"1,1"` ->
 * `[0, 0]`. Do not "fix" this to sorted, deduplicated output.
 *
 * One deliberate platform difference: a page number too large to fit in a
 * 32-bit `Int` (e.g. "99999999999") is reported as unparseable rather than
 * accepted like JS's unbounded numbers would — safer than throwing, and
 * not a real-world page range anyone would legitimately type.
 */
fun parsePageRange(rangeStr: String, maxPages: Int): ParsePageRangeResult {
    val indices = mutableListOf<Int>()
    val errors = mutableListOf<String>()
    val outOfRange = mutableListOf<Int>()

    for (rawSegment in rangeStr.split(",")) {
        val segment = rawSegment.trim()
        if (segment.isEmpty()) continue

        if (segment.contains("-")) {
            val parts = segment.split("-")
            if (parts.size != 2) {
                errors.add("""Could not understand "$segment" in the page range.""")
                continue
            }
            val startPart = parts[0].trim()
            val endPart = parts[1].trim()
            val start = startPart.toIntOrNull()
            val end = endPart.toIntOrNull()
            if (start == null || end == null || !DIGITS_ONLY.matches(startPart) || !DIGITS_ONLY.matches(endPart)) {
                errors.add("""Could not understand "$segment" in the page range.""")
                continue
            }
            if (start > end) {
                errors.add(""""$segment" is backwards — did you mean $end-$start?""")
                continue
            }
            if (start < 1 || end > maxPages) {
                for (i in start..end) {
                    if (i < 1 || i > maxPages) outOfRange.add(i)
                }
                continue
            }
            for (i in start..end) {
                indices.add(i - 1)
            }
        } else {
            if (!DIGITS_ONLY.matches(segment)) {
                errors.add("""Could not understand "$segment" in the page range.""")
                continue
            }
            val page = segment.toIntOrNull()
            if (page == null) {
                errors.add("""Could not understand "$segment" in the page range.""")
                continue
            }
            if (page < 1 || page > maxPages) {
                outOfRange.add(page)
                continue
            }
            indices.add(page - 1)
        }
    }

    if (outOfRange.isNotEmpty()) {
        val listed = outOfRange.distinct()
        // A wide range like "1-1000" would otherwise enumerate every page
        // past the end, producing an unreadably long message.
        val maxListed = 8
        val shown = listed.take(maxListed).joinToString(", ")
        val remaining = listed.size - maxListed

        errors.add(
            if (listed.size == 1) {
                "Page $shown is outside this $maxPages-page document."
            } else {
                "Pages $shown${if (remaining > 0) " and $remaining more" else ""} are outside this $maxPages-page document."
            },
        )
    }

    return ParsePageRangeResult(indices, errors)
}

/**
 * Resolves "all" (case-insensitive) or a range string to 0-based page
 * indices against [maxPages], or throws [IllegalArgumentException] with
 * the same joined error text `splitPdf`/`splitPdfToZip` throw on the web
 * side — one place both split modes below share, so they can't disagree
 * on what a given range string means.
 */
fun resolvePageIndices(pages: String, maxPages: Int): List<Int> {
    val indices = if (pages.trim().equals("all", ignoreCase = true)) {
        (0 until maxPages).toList()
    } else {
        val parsed = parsePageRange(pages, maxPages)
        if (parsed.errors.isNotEmpty()) {
            throw IllegalArgumentException(parsed.errors.joinToString(" "))
        }
        parsed.indices
    }
    if (indices.isEmpty()) {
        throw IllegalArgumentException("Invalid page range specified.")
    }
    return indices
}

/** Extracts [pages] from [document] into one new combined PDF's bytes. */
fun splitPdfToSingleFile(document: PDDocument, pages: String): ByteArray {
    val indices = resolvePageIndices(pages, document.numberOfPages)
    PDDocument().use { newDocument ->
        for (index in indices) {
            // importPage (not addPage) is what actually copies the page's
            // resources into the new document — addPage alone would leave
            // the page referencing the source document's own resource
            // dictionary.
            newDocument.importPage(document.getPage(index))
        }
        val out = ByteArrayOutputStream()
        newDocument.save(out)
        return out.toByteArray()
    }
}

/** One extracted page, packaged as its own single-page PDF. */
data class SplitPage(val pageNumber: Int, val bytes: ByteArray)

/**
 * Extracts [pages] from [document], each as its own single-page PDF,
 * instead of one combined file — the "separate files" option
 * `SplitTool.tsx` offers via `splitPdfToZip`. Packaging into a zip is left
 * to the caller (`ZipWriter.kt`), same separation of concerns as the web
 * version.
 */
fun splitPdfToPages(document: PDDocument, pages: String): List<SplitPage> {
    val indices = resolvePageIndices(pages, document.numberOfPages)
    return indices.map { index ->
        val bytes = PDDocument().use { single ->
            single.importPage(document.getPage(index))
            val out = ByteArrayOutputStream()
            single.save(out)
            out.toByteArray()
        }
        SplitPage(pageNumber = index + 1, bytes = bytes)
    }
}
