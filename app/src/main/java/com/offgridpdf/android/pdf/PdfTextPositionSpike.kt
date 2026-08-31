package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Spike D (`ANDROID_IMPLEMENTATION_PLAN.md`) — text-position lookup for
 * Find & Redact (A-21). The plan calls this out as the one place in the
 * whole plan with no direct Android equivalent to fall back on: the web
 * version's `pdf-search.ts` gets an exact on-page rectangle for a text
 * match by reusing pdf.js's own `TextLayer` — real, browser-measured
 * `<div>`s — then reading a DOM `Range`'s `getClientRects()`. Android has
 * neither a DOM nor a font-metric-measuring layout engine to borrow.
 *
 * The real path, confirmed against the actual pinned pdfbox-android
 * `v2.0.27.0` source (not assumed) before relying on it:
 * - `PDFTextStripper.writeString(String text, List<TextPosition> textPositions)`
 *   is a real, documented, overridable extension point (`PDFTextStripper.java`)
 *   — its default implementation ignores `textPositions` and just calls
 *   `writeString(text)`. Verified call site (`writeLine`): it's invoked once
 *   per *word*, in reading order, with exactly that word's own characters'
 *   `TextPosition`s (one call per `WordWithTextPositions`).
 * - `TextPosition.getX()/getY()/getWidth()/getHeight()` (`TextPosition.java`)
 *   are already page-rotation-adjusted — upper-left origin, real PDF-point
 *   units — computed once in the constructor directly from the page's own
 *   rotation and the text rendering matrix (`x = getXRot(rotation)`, etc.).
 *   This is genuinely *simpler* than the web's own approach, which needs a
 *   `viewport`/`item.transform` reconciliation specifically because raw
 *   pdf.js `item.transform` is *not* rotation-adjusted — no matrix math or
 *   separate rotation handling is needed here at all, confirmed by the
 *   rotated-page fixture test below.
 * - `writeLineSeparator()` is a second real, confirmed extension point.
 *   Verified call order in the real source (`handleLineSeparation`): a
 *   completed line's words are all emitted via `writeString` *before*
 *   `writeLineSeparator()` fires for the line that follows — used here the
 *   same way the web's `hasEOL`-driven `\n` insertion is (`pdf-search.ts`'s
 *   `locateMatches`): so a search can never propose a match that silently
 *   bridges two lines that aren't actually adjacent on the page.
 *
 * Genuinely new code, not a port, per the plan's own framing — the pure
 * pieces (`locateWordMatches`, `unionCharBoxes`) get direct unit tests
 * against literal fixture data, the same reasoning `pdf-search.ts` splits
 * `locateMatches`/`unionMatchRects` out for. Not wired into any tool or
 * dashboard entry yet — A-21 (Find & Redact) is the real tool that uses
 * this, same relationship Spike C's `PdfCompressSpike.kt` has to A-22.
 */

/** One captured character's page-rotation-adjusted bounding box, upper-left
 * origin, real PDF-point units (`TextPosition.getX/getY/getWidth/getHeight`). */
data class CharBox(val x: Float, val y: Float, val width: Float, val height: Float)

/** One word, in reading order, with its own characters' boxes.
 * [startsNewLine] is true exactly for the first word following a real
 * `writeLineSeparator()` call — i.e. the first word of a new line. */
data class WordPositions(val text: String, val startsNewLine: Boolean, val chars: List<CharBox>)

private class WordCollectingStripper : PDFTextStripper() {
    val words = mutableListOf<WordPositions>()
    private var pendingLineBreak = false

    override fun writeLineSeparator() {
        pendingLineBreak = true
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isNotEmpty()) {
            words.add(
                WordPositions(
                    text = text,
                    startsNewLine = pendingLineBreak,
                    chars = textPositions.map { CharBox(it.x, it.y, it.width, it.height) },
                ),
            )
            pendingLineBreak = false
        }
    }
}

/**
 * Captures every word on [pageNumber] (1-based, matching `extractText`'s
 * own convention) of [document], in reading order, with each word's own
 * per-character boxes. Pure text extraction — unlike page rendering
 * (`PDFRenderer`), this never touches `android.graphics.Bitmap`, so unlike
 * A-18/A-19/A-20's disclosed render gap, this is genuinely, fully testable
 * under this project's JVM unit-test stub, real page content included.
 */
fun collectWordPositions(document: PDDocument, pageNumber: Int): List<WordPositions> {
    val stripper = WordCollectingStripper()
    stripper.sortByPosition = true
    stripper.startPage = pageNumber
    stripper.endPage = pageNumber
    stripper.getText(document)
    return stripper.words
}

/** Where one match touches one word — [startInWord]/[endInWord] are
 * character offsets into that word's own [WordPositions.chars], not the
 * whole page (mirrors `pdf-search.ts`'s `MatchOffset`). */
data class WordMatchTouch(val wordIndex: Int, val startInWord: Int, val endInWord: Int)

/**
 * Finds every occurrence of [query] across [words] (already in reading
 * order) and maps each to the word(s) it touches. Pure — no `PDDocument`,
 * no `TextPosition` — so this is the piece that gets direct unit tests with
 * plain fixture data, the same reasoning `pdf-search.ts` splits
 * `locateMatches` out for.
 *
 * Words are joined by a single space, except where [WordPositions.startsNewLine]
 * is true, where a `\n` is used instead — so a match can never silently
 * bridge two lines that aren't actually adjacent on the page (the same
 * reason `pdf-search.ts`'s own `locateMatches` joins on `hasEOL`).
 */
fun locateWordMatches(
    words: List<WordPositions>,
    query: String,
    caseSensitive: Boolean = false,
): List<List<WordMatchTouch>> {
    if (query.isEmpty()) return emptyList()

    val joined = StringBuilder()
    val wordRanges = mutableListOf<IntRange>()
    for ((index, word) in words.withIndex()) {
        if (index > 0) joined.append(if (word.startsNewLine) '\n' else ' ')
        val start = joined.length
        joined.append(word.text)
        wordRanges.add(start until joined.length)
    }

    val haystack = if (caseSensitive) joined.toString() else joined.toString().lowercase()
    val needle = if (caseSensitive) query else query.lowercase()

    val matches = mutableListOf<List<WordMatchTouch>>()
    var searchFrom = 0
    while (true) {
        val start = haystack.indexOf(needle, searchFrom)
        if (start == -1) break
        val end = start + needle.length
        searchFrom = end

        val touches = mutableListOf<WordMatchTouch>()
        for ((wordIndex, range) in wordRanges.withIndex()) {
            val overlapStart = maxOf(start, range.first)
            val overlapEnd = minOf(end, range.last + 1)
            if (overlapStart < overlapEnd) {
                touches.add(
                    WordMatchTouch(
                        wordIndex = wordIndex,
                        startInWord = overlapStart - range.first,
                        endInWord = overlapEnd - range.first,
                    ),
                )
            }
        }
        if (touches.isNotEmpty()) matches.add(touches)
    }
    return matches
}

/** How close two boxes' vertical centers must be to count as "the same
 * visual line" — generous, mirrors `pdf-search.ts`'s own `LINE_TOLERANCE_PX`
 * (real font metrics can vary a couple points within one line: mixed
 * fonts, super/subscripts). Same unit as [CharBox] (PDF points). */
private const val LINE_TOLERANCE_PT = 3f

/** Fixed safety margin added to every side of a found match's box — a
 * slightly generous box is the safe direction for something about to be
 * permanently deleted, same reasoning as `pdf-search.ts`'s own
 * `REDACTION_PADDING_PT`. */
private const val MATCH_PADDING_PT = 1.5f

/**
 * Unions [boxes] (already page-rotation-adjusted, upper-left origin) into a
 * single [RedactionRect] in this app's own bottom-left-origin PDF-point
 * convention (`PdfRedact.kt`'s `toPixelRect`/`pixelToPdfRect`), or `null` if
 * they don't share one visual line — no single rectangle is honest for a
 * match that crosses a line break; the caller counts these as skipped
 * rather than guessing, mirroring `pdf-search.ts`'s own `unionMatchRects`.
 */
fun unionCharBoxes(boxes: List<CharBox>, pageHeightPts: Float): RedactionRect? {
    if (boxes.isEmpty()) return null

    val midY = { box: CharBox -> box.y + box.height / 2f }
    val firstMidY = midY(boxes[0])
    val sameLine = boxes.all { kotlin.math.abs(midY(it) - firstMidY) <= LINE_TOLERANCE_PT }
    if (!sameLine) return null

    val left = boxes.minOf { it.x } - MATCH_PADDING_PT
    val right = boxes.maxOf { it.x + it.width } + MATCH_PADDING_PT
    val top = boxes.minOf { it.y } - MATCH_PADDING_PT
    val bottom = boxes.maxOf { it.y + it.height } + MATCH_PADDING_PT

    return RedactionRect(
        x = left,
        y = pageHeightPts - bottom,
        width = right - left,
        height = bottom - top,
    )
}
