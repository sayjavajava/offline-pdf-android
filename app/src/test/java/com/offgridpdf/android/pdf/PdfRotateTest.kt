package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

/** Web reference: `rotatePdf` (`pdf-ops.ts`), verified against `RotateTool.tsx`'s own behavior. */
class PdfRotateTest {

    private fun buildPdf(pageCount: Int): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { document.addPage(PDPage()) }
        return document
    }

    private fun rotations(bytes: ByteArray): List<Int> {
        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { doc ->
                return (0 until doc.numberOfPages).map { doc.getPage(it).rotation }
            }
        }
    }

    @Test
    fun `rejects an angle that is not a multiple of 90`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rotatePdf(doc, 45)
        }
        assertEquals("Rotation angle must be 90, 180, or 270 degrees.", error.message)
        doc.close()
    }

    @Test
    fun `accepts every allowed angle`() {
        for (angle in listOf(90, 180, 270, -90, -180, -270)) {
            val doc = buildPdf(1)
            val bytes = rotatePdf(doc, angle)
            assertEquals(listOf(angle), rotations(bytes))
            doc.close()
        }
    }

    @Test
    fun `rotates every page when pages is blank`() {
        val doc = buildPdf(3)
        val bytes = rotatePdf(doc, 90, pages = "")
        assertEquals(listOf(90, 90, 90), rotations(bytes))
        doc.close()
    }

    @Test
    fun `rotates every page when pages is "all" case-insensitively`() {
        val doc = buildPdf(3)
        val bytes = rotatePdf(doc, 90, pages = "ALL")
        assertEquals(listOf(90, 90, 90), rotations(bytes))
        doc.close()
    }

    @Test
    fun `rotates only the requested pages`() {
        val doc = buildPdf(3)
        val bytes = rotatePdf(doc, 90, pages = "2")
        assertEquals(listOf(0, 90, 0), rotations(bytes))
        doc.close()
    }

    @Test
    fun `rotation is additive relative to the page's current rotation`() {
        val doc = PDDocument()
        val page = PDPage()
        page.rotation = 90
        doc.addPage(page)

        val bytes = rotatePdf(doc, 90, pages = "1")

        assertEquals(listOf(180), rotations(bytes))
        doc.close()
    }

    @Test
    fun `deduplicates a page requested more than once instead of rotating it repeatedly`() {
        val doc = buildPdf(2)
        val bytes = rotatePdf(doc, 90, pages = "1,1,1")
        // If duplicates weren't deduplicated, page 1 would end up rotated
        // 270 (90 applied 3 times), not 90.
        assertEquals(listOf(90, 0), rotations(bytes))
        doc.close()
    }

    @Test
    fun `surfaces range errors the same way Split does`() {
        val doc = buildPdf(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            rotatePdf(doc, 90, pages = "99")
        }
        assertEquals("Page 99 is outside this 3-page document.", error.message)
        doc.close()
    }
}
