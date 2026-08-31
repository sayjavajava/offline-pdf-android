package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * A-21: Find & Redact. Web reference: `RedactTool.tsx`'s Find UI +
 * `findTextMatches` (`pdf-search.ts`). Builds directly on Spike D's
 * `collectWordPositions`/`locateWordMatches`/`unionCharBoxes`
 * (`PdfTextPositionSpike.kt`) — this file is only the orchestration across
 * a whole document, mirroring `findTextMatches`'s own per-page loop.
 *
 * Same shape as [MatchResult] on the web: [matchesByPage] is deliberately
 * the same `Map<Int, List<RedactionRect>>` shape `RedactScreen`'s own
 * `redactions` state already uses, so "add all" is a plain map merge, no
 * translation layer, exactly like `handleAddAllMatches` (`RedactTool.tsx`).
 */
data class FindMatchResult(
    val totalMatches: Int,
    val matchesByPage: Map<Int, List<RedactionRect>>,
    /** Matches found but not turned into a box because they cross a
     * visual line — draw these by hand instead, same as the web version. */
    val skippedByPage: Map<Int, Int>,
    /** Pages with no text layer at all (most likely scanned) — same
     * signal `ExtractTextTool.tsx`/`ExtractTextScreen.kt` already surface. */
    val noTextLayerPages: List<Int>,
)

private const val MIN_QUERY_LENGTH = 2

/**
 * Finds every occurrence of [query] across the whole of [document] and
 * returns ready-to-use redaction boxes. Nothing is applied — same "review
 * before anything destructive happens" shape the rest of this tool
 * already has (`RedactScreen`'s own hand-drawn box list).
 */
fun findTextMatches(document: PDDocument, query: String, caseSensitive: Boolean = false): FindMatchResult {
    if (query.length < MIN_QUERY_LENGTH) {
        throw IllegalArgumentException("Search text must be at least $MIN_QUERY_LENGTH characters.")
    }

    val matchesByPage = mutableMapOf<Int, MutableList<RedactionRect>>()
    val skippedByPage = mutableMapOf<Int, Int>()
    val noTextLayerPages = mutableListOf<Int>()
    var totalMatches = 0

    for (index in 0 until document.numberOfPages) {
        val pageNumber = index + 1
        val words = collectWordPositions(document, pageNumber)
        if (words.all { it.text.isBlank() }) {
            noTextLayerPages.add(pageNumber)
            continue
        }

        val matches = locateWordMatches(words, query, caseSensitive)
        if (matches.isEmpty()) continue

        val page = document.getPage(index)
        val rotation = page.rotation
        val mediaBox = page.mediaBox
        // Effective (as-displayed) page height for the coordinate flip --
        // swapped for a 90/270-rotated page, since TextPosition's own
        // coordinates are already in that effective frame (Spike D's own
        // finding, PdfTextPositionSpike.kt).
        val effectiveHeightPts = if (rotation == 90 || rotation == 270) mediaBox.width else mediaBox.height

        var skipped = 0
        for (touches in matches) {
            val boxes = touches.flatMap { touch ->
                words[touch.wordIndex].chars.subList(touch.startInWord, touch.endInWord)
            }
            val rect = unionCharBoxes(boxes, effectiveHeightPts, rotation)
            if (rect != null) {
                matchesByPage.getOrPut(pageNumber) { mutableListOf() }.add(rect)
                totalMatches++
            } else {
                skipped++
            }
        }
        if (skipped > 0) skippedByPage[pageNumber] = skipped
    }

    return FindMatchResult(totalMatches, matchesByPage, skippedByPage, noTextLayerPages)
}
