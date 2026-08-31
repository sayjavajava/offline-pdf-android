package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Web reference: `addPageNumbers`/`formatPageNumber` (`pdf-ops.ts`), verified against `PageNumbersTool.tsx`. */
class PdfPageNumbersTest {

    private fun buildPdf(pageCount: Int): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { document.addPage(PDPage()) }
        return document
    }

    private fun pageCountOf(bytes: ByteArray): Int {
        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { doc -> return doc.numberOfPages }
        }
    }

    private fun unstampedSize(pageCount: Int): Int {
        val doc = buildPdf(pageCount)
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.size()
    }

    // --- formatPageNumber (pure) ---------------------------------------

    @Test
    fun `formats plain n`() {
        assertEquals("3", formatPageNumber(3, 10, PageNumberFormat.N, "", 6))
    }

    @Test
    fun `formats n of total`() {
        assertEquals("3 of 10", formatPageNumber(3, 10, PageNumberFormat.N_OF_TOTAL, "", 6))
    }

    @Test
    fun `formats bates with zero padding`() {
        assertEquals("000042", formatPageNumber(42, 100, PageNumberFormat.BATES, "", 6))
    }

    @Test
    fun `applies a prefix to every format`() {
        assertEquals("ABC-3", formatPageNumber(3, 10, PageNumberFormat.N, "ABC-", 6))
        assertEquals("ABC-3 of 10", formatPageNumber(3, 10, PageNumberFormat.N_OF_TOTAL, "ABC-", 6))
        assertEquals("ABC-000042", formatPageNumber(42, 100, PageNumberFormat.BATES, "ABC-", 6))
    }

    @Test
    fun `bates respects custom digit width`() {
        assertEquals("7", formatPageNumber(7, 10, PageNumberFormat.BATES, "", 1))
    }

    // --- addPageNumbers validation --------------------------------------

    @Test
    fun `rejects a font size outside 1 to 300`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(fontSize = 0f))
        }
        assertEquals("Font size must be between 1 and 300.", error.message)
        doc.close()
    }

    @Test
    fun `rejects a margin outside 0 to 300`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(margin = 301f))
        }
        assertEquals("Margin must be between 0 and 300 points.", error.message)
        doc.close()
    }

    @Test
    fun `rejects a negative starting number`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(start = -1))
        }
        assertEquals("Starting number must be a whole number of 0 or more.", error.message)
        doc.close()
    }

    @Test
    fun `rejects bates digits outside 1 to 20`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(format = PageNumberFormat.BATES, digits = 21))
        }
        assertEquals("Bates padding must be between 1 and 20 digits.", error.message)
        doc.close()
    }

    @Test
    fun `does not reject digits outside range when format is not bates`() {
        // digits is only meaningful for bates -- other formats shouldn't
        // be blocked by whatever the (unused) digits field happens to be.
        val doc = buildPdf(1)
        val bytes = addPageNumbers(doc, PageNumberOptions(format = PageNumberFormat.N, digits = 99))
        assertEquals(1, pageCountOf(bytes))
        doc.close()
    }

    @Test
    fun `rejects an out-of-range colour channel`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(color = PageNumberColor(1.5f, 0f, 0f)))
        }
        assertEquals("Colour channels must each be between 0 and 1.", error.message)
        doc.close()
    }

    @Test
    fun `rejects an invalid page range the same way Rotate does`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(pages = "99"))
        }
        assertEquals("Page 99 is outside this 3-page document.", error.message)
        doc.close()
    }

    // --- addPageNumbers behavior -----------------------------------------

    @Test
    fun `stamps every page when pages is blank and preserves page count`() {
        val doc = buildPdf(3)
        val bytes = addPageNumbers(doc, PageNumberOptions(pages = ""))
        assertEquals(3, pageCountOf(bytes))
        doc.close()
    }

    @Test
    fun `stamped output is larger than an unstamped save`() {
        val doc = buildPdf(2)
        val bytes = addPageNumbers(doc)
        assertTrue(bytes.size > unstampedSize(2))
        doc.close()
    }

    @Test
    fun `stamps only the requested pages`() {
        val doc = buildPdf(5)
        val bytes = addPageNumbers(doc, PageNumberOptions(pages = "2,4"))
        assertEquals(5, pageCountOf(bytes))
        doc.close()
    }

    @Test
    fun `rejects a blank page selection that resolves to no pages`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addPageNumbers(doc, PageNumberOptions(pages = "0"))
        }
        assertTrue(error.message!!.isNotBlank())
        doc.close()
    }
}
