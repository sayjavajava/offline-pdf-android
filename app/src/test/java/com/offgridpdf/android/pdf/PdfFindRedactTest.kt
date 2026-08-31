package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web reference: `RedactTool.tsx`'s Find UI + `findTextMatches`
 * (`pdf-search.ts`). Unlike A-18/A-19/A-20's disclosed `Bitmap` gap, this
 * is genuinely, fully testable under the JVM unit-test stub — text
 * extraction (`collectWordPositions`, `PdfTextPositionSpike.kt`) never
 * touches `android.graphics.Bitmap`, confirmed by Spike D.
 */
class PdfFindRedactTest {

    private fun addTextPage(document: PDDocument, text: String, rotation: Int = 0): PDPage {
        val page = PDPage(PDRectangle.LETTER)
        page.rotation = rotation
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(72f, 700f)
            stream.showText(text)
            stream.endText()
        }
        return page
    }

    @Test
    fun `findTextMatches rejects a query shorter than 2 characters`() {
        val document = PDDocument()
        addTextPage(document, "Some Content")
        val e = assertThrows(IllegalArgumentException::class.java) {
            findTextMatches(document, "a")
        }
        document.close()
        assertEquals("Search text must be at least 2 characters.", e.message)
    }

    @Test
    fun `findTextMatches finds a real match and reports a sane RedactionRect`() {
        val document = PDDocument()
        addTextPage(document, "Secret Content")
        val result = findTextMatches(document, "Secret")
        document.close()

        assertEquals(1, result.totalMatches)
        assertEquals(setOf(1), result.matchesByPage.keys)
        val rect = result.matchesByPage.getValue(1).single()
        assertTrue("width should be positive, got ${rect.width}", rect.width > 0f)
        assertTrue("height should be positive, got ${rect.height}", rect.height > 0f)
        assertTrue(result.skippedByPage.isEmpty())
        assertTrue(result.noTextLayerPages.isEmpty())
    }

    @Test
    fun `findTextMatches reports zero matches for text that is not present`() {
        val document = PDDocument()
        addTextPage(document, "Public Content")
        val result = findTextMatches(document, "Secret")
        document.close()

        assertEquals(0, result.totalMatches)
        assertTrue(result.matchesByPage.isEmpty())
    }

    @Test
    fun `findTextMatches is case-insensitive by default and case-sensitive on request`() {
        val document = PDDocument()
        addTextPage(document, "Secret Content")
        assertEquals(1, findTextMatches(document, "secret").totalMatches)
        assertEquals(0, findTextMatches(document, "secret", caseSensitive = true).totalMatches)
        assertEquals(1, findTextMatches(document, "Secret", caseSensitive = true).totalMatches)
        document.close()
    }

    @Test
    fun `findTextMatches attributes matches to the right page across a multi-page document`() {
        val document = PDDocument()
        addTextPage(document, "Public Content")
        addTextPage(document, "Secret Content")
        addTextPage(document, "More Secret Content")
        val result = findTextMatches(document, "Secret")
        document.close()

        assertEquals(2, result.totalMatches)
        assertEquals(setOf(2, 3), result.matchesByPage.keys)
        assertEquals(1, result.matchesByPage.getValue(2).size)
        assertEquals(1, result.matchesByPage.getValue(3).size)
    }

    @Test
    fun `findTextMatches flags a page with no text layer at all`() {
        val document = PDDocument()
        document.addPage(PDPage(PDRectangle.LETTER)) // blank page, no content stream
        addTextPage(document, "Secret Content")
        val result = findTextMatches(document, "Secret")
        document.close()

        assertEquals(listOf(1), result.noTextLayerPages)
        assertEquals(setOf(2), result.matchesByPage.keys)
    }

    @Test
    fun `findTextMatches finds a real match on a rotated page`() {
        val document = PDDocument()
        addTextPage(document, "Secret Content", rotation = 90)
        val result = findTextMatches(document, "Secret")
        document.close()

        assertEquals(1, result.totalMatches)
        val rect = result.matchesByPage.getValue(1).single()
        assertTrue("width should be positive, got ${rect.width}", rect.width > 0f)
        assertTrue("height should be positive, got ${rect.height}", rect.height > 0f)
    }
}
