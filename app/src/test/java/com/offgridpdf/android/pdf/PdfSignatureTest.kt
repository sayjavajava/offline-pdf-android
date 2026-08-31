package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Web reference: `addSignature` (`pdf-ops.ts`), `placeSignatureImage`
 * (`pdf-signature.ts`), verified against `SignatureTool.tsx`.
 *
 * Only the page-existence and unsupported-image-format validation
 * (both checked, and throw, before ever touching PDFBox's image
 * embedding) are covered here — see `addSignature`'s (`PdfSignature.kt`)
 * doc comment for why the actual embedding behavior can't be exercised
 * under this project's plain-JUnit setup (same `android.graphics.BitmapFactory`
 * gap as A-12's `imagesToPdf`).
 */
class PdfSignatureTest {

    private fun buildPdf(pageCount: Int): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { document.addPage(PDPage()) }
        return document
    }

    private val pngMagicBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    @Test
    fun `rejects a page number that doesn't exist in the document`() {
        val doc = buildPdf(2)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addSignature(doc, pngMagicBytes, "signature.png", SignaturePlacement(page = 3, x = 0f, y = 0f, width = 100f, height = 50f))
        }
        assertEquals("Page 3 does not exist in this PDF (it has 2 page(s)).", error.message)
        doc.close()
    }

    @Test
    fun `rejects page 0, since placement is 1-based`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addSignature(doc, pngMagicBytes, "signature.png", SignaturePlacement(page = 0, x = 0f, y = 0f, width = 100f, height = 50f))
        }
        assertEquals("Page 0 does not exist in this PDF (it has 1 page(s)).", error.message)
        doc.close()
    }

    @Test
    fun `rejects an unsupported signature image type before touching PDFBox`() {
        val doc = buildPdf(1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            addSignature(doc, byteArrayOf(1, 2, 3), "signature.txt", SignaturePlacement(page = 1, x = 0f, y = 0f, width = 100f, height = 50f))
        }
        assertEquals(
            "Could not read the signature image: unsupported image type. Please use JPEG or PNG.",
            error.message,
        )
        doc.close()
    }
}
