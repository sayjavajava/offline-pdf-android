package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web reference: `extractPdfText` (`pdf-render.ts`), verified against
 * `ExtractTextTool.tsx`.
 */
class PdfExtractTextTest {

    /** [pageTexts] is one entry per page; `null` leaves that page blank (no text drawn). */
    private fun buildPdf(vararg pageTexts: String?): PDDocument {
        val document = PDDocument()
        for (text in pageTexts) {
            val page = PDPage()
            document.addPage(page)
            if (text != null) {
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
        }
        return document
    }

    @Test
    fun `extracts the text drawn on a page`() {
        val document = buildPdf("Hello World")
        val result = extractText(document, "all")
        assertEquals(1, result.size)
        assertEquals(1, result[0].pageNumber)
        assertEquals("Hello World", result[0].text)
        document.close()
    }

    @Test
    fun `extracts text only for the requested page range, in order`() {
        val document = buildPdf("Page One", "Page Two", "Page Three")
        val result = extractText(document, "1,3")
        assertEquals(2, result.size)
        assertEquals(1, result[0].pageNumber)
        assertEquals("Page One", result[0].text)
        assertEquals(3, result[1].pageNumber)
        assertEquals("Page Three", result[1].text)
        document.close()
    }

    @Test
    fun `a page with no text content comes back with an empty string, not an error`() {
        val document = buildPdf(null)
        val result = extractText(document, "all")
        assertEquals(1, result.size)
        assertTrue(result[0].text.isEmpty())
        document.close()
    }

    @Test
    fun `defaults to every page, in document order, when given all`() {
        val document = buildPdf("Page One", "Page Two")
        val result = extractText(document, "all")
        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result.map { it.pageNumber })
        assertEquals(listOf("Page One", "Page Two"), result.map { it.text })
        document.close()
    }

    @Test
    fun `rejects a page number that doesn't exist in the document`() {
        val document = buildPdf("Page One", "Page Two")
        val error = assertThrows(IllegalArgumentException::class.java) {
            extractText(document, "5")
        }
        assertEquals("Page 5 is outside this 2-page document.", error.message)
        document.close()
    }
}
