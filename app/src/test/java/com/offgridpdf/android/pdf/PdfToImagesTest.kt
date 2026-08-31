package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Web reference: `renderPdfPages` (`pdf-render.ts`), verified against
 * `PdfToImagesTool.tsx`. Real page rendering touches
 * `android.graphics.Bitmap`/`Bitmap.compress()` for every call, unlike
 * A-22's compress tool (which only touches `Bitmap` when a page actually
 * has an embedded image, letting an image-free document be tested as a
 * real no-op) — there is no Bitmap-free path through this tool to test
 * under the JVM unit-test stub, so real render output/quality genuinely
 * needs a device/emulator (Spike A already proved PdfBox-Android's own
 * `PDFRenderer` is the right renderer for this app; what's not verified
 * here is this specific tool's own visual output). What *is* real and
 * verified below: scale and page-range validation both happen before
 * `PDFRenderer`/`Bitmap` is ever touched, so those checks are genuinely
 * testable here.
 */
class PdfToImagesTest {

    @Test
    fun `zero scale is rejected before any rendering happens`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val e = assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "all", 0f)
        }
        document.close()
        assertEquals("Scale must be between 0.0 and 8.0.", e.message)
    }

    @Test
    fun `a scale above 8 is rejected`() {
        val document = PDDocument()
        document.addPage(PDPage())
        assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "all", 8.5f)
        }
        document.close()
    }

    @Test
    fun `a negative or NaN scale is rejected`() {
        val document = PDDocument()
        document.addPage(PDPage())
        assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "all", -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "all", Float.NaN)
        }
        document.close()
    }

    @Test
    fun `a page number outside the document is rejected before any rendering happens`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val e = assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "5", 2f)
        }
        document.close()
        assertEquals("Page 5 is outside this 1-page document.", e.message)
    }

    @Test
    fun `an unparseable page range is rejected`() {
        val document = PDDocument()
        document.addPage(PDPage())
        assertThrows(IllegalArgumentException::class.java) {
            renderPdfPagesToPng(document, "not-a-range", 2f)
        }
        document.close()
    }
}
