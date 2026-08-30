package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

/** Web reference: `mergePdf` (`pdf-ops.ts`), verified against `MergeTool.tsx`'s own behavior. */
class PdfMergeTest {

    private fun buildPdf(vararg pageHeights: Float): PDDocument {
        val document = PDDocument()
        for (height in pageHeights) document.addPage(PDPage(PDRectangle(200f, height)))
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
    fun `rejects fewer than 2 documents`() {
        val doc = buildPdf(100f)
        val error = assertThrows(IllegalArgumentException::class.java) {
            mergePdf(listOf(doc))
        }
        assertEquals("Please select at least 2 PDF files to merge.", error.message)
        doc.close()
    }

    @Test
    fun `rejects an empty list`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            mergePdf(emptyList())
        }
        assertEquals("Please select at least 2 PDF files to merge.", error.message)
    }

    @Test
    fun `combines pages from every document in order`() {
        val docA = buildPdf(100f, 101f)
        val docB = buildPdf(200f)
        val docC = buildPdf(300f, 301f, 302f)

        val bytes = mergePdf(listOf(docA, docB, docC))

        assertEquals(listOf(100f, 101f, 200f, 300f, 301f, 302f), pageHeights(bytes))
        docA.close()
        docB.close()
        docC.close()
    }

    @Test
    fun `merging exactly 2 documents works`() {
        val docA = buildPdf(100f)
        val docB = buildPdf(200f)

        val bytes = mergePdf(listOf(docA, docB))

        assertEquals(listOf(100f, 200f), pageHeights(bytes))
        docA.close()
        docB.close()
    }
}
