package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

/** Web reference: `rearrangePdf` (`pdf-ops.ts`), verified against `RearrangeTool.tsx`'s own behavior. */
class PdfRearrangeTest {

    private fun buildPdf(pageCount: Int): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { i -> document.addPage(PDPage(PDRectangle(200f, 100f + i))) }
        return document
    }

    private fun pageHeights(bytes: ByteArray): List<Float> {
        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { doc ->
                return (0 until doc.numberOfPages).map { doc.getPage(it).mediaBox.height }
            }
        }
    }

    @Test
    fun `rejects a blank pages string`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rearrangePdf(doc, "")
        }
        assertEquals("Enter the pages to keep, in the desired order.", error.message)
        doc.close()
    }

    @Test
    fun `rejects a whitespace-only pages string`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rearrangePdf(doc, "   ")
        }
        assertEquals("Enter the pages to keep, in the desired order.", error.message)
        doc.close()
    }

    @Test
    fun `keeps only the listed pages, in the order given`() {
        val doc = buildPdf(5)
        val bytes = rearrangePdf(doc, "5,1,3")
        assertEquals(listOf(104f, 100f, 102f), pageHeights(bytes))
        doc.close()
    }

    @Test
    fun `omitting a page deletes it`() {
        val doc = buildPdf(4)
        val bytes = rearrangePdf(doc, "1,3")
        assertEquals(listOf(100f, 102f), pageHeights(bytes))
        doc.close()
    }

    @Test
    fun `listing a page twice duplicates it, unlike Rotate's deduplication`() {
        val doc = buildPdf(3)
        val bytes = rearrangePdf(doc, "1,1,2")
        assertEquals(listOf(100f, 100f, 101f), pageHeights(bytes))
        doc.close()
    }

    @Test
    fun `surfaces range errors the same way Split and Rotate do`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rearrangePdf(doc, "99")
        }
        assertEquals("Page 99 is outside this 3-page document.", error.message)
        doc.close()
    }

    @Test
    fun `does not accept the word all as a special case`() {
        // Unlike Split/Rotate, "all" is just an unparseable segment here --
        // there's no meaningful "keep everything, in original order" case
        // to special-case (that would just be a no-op copy).
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rearrangePdf(doc, "all")
        }
        assertEquals("Could not understand \"all\" in the page range.", error.message)
        doc.close()
    }
}
