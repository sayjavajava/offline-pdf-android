package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Spike D (`ANDROID_IMPLEMENTATION_PLAN.md`) — see `PdfTextPositionSpike.kt`'s
 * own header for the full real-source verification this is built on.
 *
 * The acceptance test below (`collectWordPositions is already page-rotation-
 * adjusted...`) is this spike's real deliverable: proof, against a real PDF
 * with a real `/Rotate 90` page, that `TextPosition` genuinely applies the
 * rotation rather than reporting raw content-stream coordinates — the same
 * real bug class the web version's own throwaway rotation test caught
 * before real code was written for `pdf-search.ts` ("a naive 'just read
 * item.transform's e/f' approach would silently misplace every box on any
 * rotated page"). Unlike page *rendering* (A-18/A-19/A-20's disclosed
 * `Bitmap` gap), text extraction never touches `android.graphics.Bitmap`,
 * so this runs for real under the JVM unit-test stub — no emulator needed.
 */
class PdfTextPositionSpikeTest {

    // --- locateWordMatches ---

    private fun word(text: String, startsNewLine: Boolean = false) =
        WordPositions(text, startsNewLine, chars = text.map { CharBox(0f, 0f, 1f, 1f) })

    @Test
    fun `locateWordMatches finds a match fully inside one word`() {
        val words = listOf(word("Hello"), word("World"))
        val matches = locateWordMatches(words, "World")
        assertEquals(1, matches.size)
        assertEquals(listOf(WordMatchTouch(wordIndex = 1, startInWord = 0, endInWord = 5)), matches[0])
    }

    @Test
    fun `locateWordMatches finds a match spanning a word boundary via the joining space`() {
        val words = listOf(word("Hello"), word("World"))
        val matches = locateWordMatches(words, "lo World")
        assertEquals(1, matches.size)
        assertEquals(
            listOf(
                WordMatchTouch(wordIndex = 0, startInWord = 3, endInWord = 5),
                WordMatchTouch(wordIndex = 1, startInWord = 0, endInWord = 5),
            ),
            matches[0],
        )
    }

    @Test
    fun `locateWordMatches is case-insensitive by default and case-sensitive on request`() {
        val words = listOf(word("Secret"))
        assertEquals(1, locateWordMatches(words, "secret").size)
        assertEquals(0, locateWordMatches(words, "secret", caseSensitive = true).size)
        assertEquals(1, locateWordMatches(words, "Secret", caseSensitive = true).size)
    }

    @Test
    fun `locateWordMatches never bridges two lines that are not actually adjacent`() {
        // "...Smith" on one line, "Hello..." starting the next -- searching
        // for "SmithHello" must never match, the same real bug the web
        // version's own hasEOL-driven line join exists to prevent.
        val words = listOf(word("Smith"), word("Hello", startsNewLine = true))
        assertEquals(0, locateWordMatches(words, "SmithHello").size)
        // But each word alone, and a same-line phrase, still matches.
        assertEquals(1, locateWordMatches(words, "Smith").size)
        assertEquals(1, locateWordMatches(words, "Hello").size)
    }

    @Test
    fun `locateWordMatches returns nothing for an empty query`() {
        assertEquals(0, locateWordMatches(listOf(word("Hello")), "").size)
    }

    // --- unionCharBoxes ---

    @Test
    fun `unionCharBoxes converts a single known box to a known RedactionRect`() {
        // A char box at upper-left-origin (10, 20, 30x8) on an 800pt-tall
        // page: right=10+30=40, bottom=20+8=28, both padded by 1.5.
        val box = CharBox(x = 10f, y = 20f, width = 30f, height = 8f)
        val rect = unionCharBoxes(listOf(box), pageHeightPts = 800f)
        assertEquals(10f - 1.5f, rect!!.x, 0.001f)
        assertEquals(800f - (28f + 1.5f), rect.y, 0.001f)
        assertEquals(30f + 3f, rect.width, 0.001f)
        assertEquals(8f + 3f, rect.height, 0.001f)
    }

    @Test
    fun `unionCharBoxes unions multiple same-line boxes into their bounding box`() {
        val boxes = listOf(
            CharBox(x = 0f, y = 100f, width = 10f, height = 12f),
            CharBox(x = 10f, y = 101f, width = 10f, height = 12f),
            CharBox(x = 20f, y = 99f, width = 10f, height = 12f),
        )
        val rect = unionCharBoxes(boxes, pageHeightPts = 800f)
        assertEquals(0f - 1.5f, rect!!.x, 0.001f)
        assertEquals(30f + 3f, rect.width, 0.001f)
    }

    @Test
    fun `unionCharBoxes returns null when boxes are not on the same visual line`() {
        val boxes = listOf(
            CharBox(x = 0f, y = 100f, width = 10f, height = 12f),
            CharBox(x = 0f, y = 400f, width = 10f, height = 12f),
        )
        assertNull(unionCharBoxes(boxes, pageHeightPts = 800f))
    }

    @Test
    fun `unionCharBoxes returns null for an empty box list`() {
        assertNull(unionCharBoxes(emptyList(), pageHeightPts = 800f))
    }

    // --- collectWordPositions: the real rotated-page acceptance test ---

    @Test
    fun `collectWordPositions is already page-rotation-adjusted -- rotated text lands where it visually appears, not at its raw content-stream coordinates`() {
        // Two pages, identical mediaBox (Letter: 612x792pt) and identical
        // text drawn at identical content-stream coordinates -- page 1
        // unrotated, page 2 with a real /Rotate 90. If TextPosition
        // ignored rotation (the real bug class this test exists to catch),
        // both pages would report the exact same word position; they must
        // not.
        val document = PDDocument()
        val page1 = PDPage(PDRectangle.LETTER)
        document.addPage(page1)
        PDPageContentStream(document, page1).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(72f, 700f)
            stream.showText("Secret Content")
            stream.endText()
        }
        val page2 = PDPage(PDRectangle.LETTER)
        page2.rotation = 90
        document.addPage(page2)
        PDPageContentStream(document, page2).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(72f, 700f)
            stream.showText("Secret Content")
            stream.endText()
        }

        val page1Words = collectWordPositions(document, pageNumber = 1)
        val page2Words = collectWordPositions(document, pageNumber = 2)
        document.close()

        assertEquals(listOf("Secret", "Content"), page1Words.map { it.text })
        assertEquals(listOf("Secret", "Content"), page2Words.map { it.text })

        val page1FirstChar = page1Words[0].chars[0]
        val page2FirstChar = page2Words[0].chars[0]

        // Derived directly from TextPosition's real constructor formulas
        // (verified against the pinned v2.0.27.0 source): for rotation 0,
        // x = Tx, y = pageHeight - Ty; for rotation 90, x = Ty, y = Tx.
        // Content-stream offset was (Tx, Ty) = (72, 700), page height 792.
        assertTrue("page 1 (unrotated) x should be ~72, got ${page1FirstChar.x}", abs(page1FirstChar.x - 72f) < 1f)
        assertTrue("page 1 (unrotated) y should be ~92, got ${page1FirstChar.y}", abs(page1FirstChar.y - 92f) < 1f)
        assertTrue("page 2 (rotated 90) x should be ~700, got ${page2FirstChar.x}", abs(page2FirstChar.x - 700f) < 1f)
        assertTrue("page 2 (rotated 90) y should be ~72, got ${page2FirstChar.y}", abs(page2FirstChar.y - 72f) < 1f)

        // The real proof: the rotated page's position is not the same as
        // the unrotated page's -- a naive, non-rotation-aware
        // implementation would report identical values for both.
        assertTrue(abs(page1FirstChar.x - page2FirstChar.x) > 100f)
        assertTrue(abs(page1FirstChar.y - page2FirstChar.y) > 10f)
    }

    @Test
    fun `a search on a rotated page produces a real, sane RedactionRect via unionCharBoxes`() {
        // End-to-end: collectWordPositions -> locateWordMatches ->
        // unionCharBoxes on the same rotated page above. A 90/270-rotated
        // page's *effective* (as-displayed) height is its mediaBox WIDTH,
        // not its mediaBox height -- TextPosition's coordinates are
        // already in that effective, swapped frame (see this spike's own
        // header comment and CODE_AUDIT.md's write-up for the caveat A-21
        // needs to carry forward: pass the effective/display height, not
        // raw `mediaBox.height`, for a 90/270-rotated page).
        val document = PDDocument()
        val page = PDPage(PDRectangle.LETTER)
        page.rotation = 90
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(72f, 700f)
            stream.showText("Secret Content")
            stream.endText()
        }

        val words = collectWordPositions(document, pageNumber = 1)
        document.close()

        val matches = locateWordMatches(words, "Secret")
        assertEquals(1, matches.size)
        val boxes = matches[0].flatMap { touch ->
            words[touch.wordIndex].chars.subList(touch.startInWord, touch.endInWord)
        }
        // Effective display height for a 90-rotated Letter page: 612pt
        // (the mediaBox's own width).
        val rect = unionCharBoxes(boxes, pageHeightPts = PDRectangle.LETTER.width)
        requireNotNull(rect)
        assertTrue("width should be positive, got ${rect.width}", rect.width > 0f)
        assertTrue("height should be positive, got ${rect.height}", rect.height > 0f)
        assertTrue("x should be within the effective page bounds", rect.x >= 0f && rect.x < PDRectangle.LETTER.height)
        assertTrue("y should be within the effective page bounds", rect.y >= 0f && rect.y < PDRectangle.LETTER.width)
    }
}
