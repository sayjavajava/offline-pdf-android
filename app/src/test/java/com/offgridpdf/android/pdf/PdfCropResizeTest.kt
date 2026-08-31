package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Web reference: `cropPdf`/`resizePdf`/`PAPER_SIZES` (`pdf-ops.ts`), verified against `CropResizeTool.tsx`. */
class PdfCropResizeTest {

    private fun buildPdf(vararg boxes: PDRectangle): PDDocument {
        val document = PDDocument()
        for (box in boxes) document.addPage(PDPage(box))
        return document
    }

    private fun reload(bytes: ByteArray): PDDocument = PDDocument.load(ByteArrayInputStream(bytes))

    // --- cropPdf ---------------------------------------------------------

    @Test
    fun `rejects a negative margin, naming which edge`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val error = assertThrows(IllegalArgumentException::class.java) {
            cropPdf(doc, CropMargins(top = -1f, bottom = 0f, left = 0f, right = 0f))
        }
        assertEquals("The top margin must be a number of 0 or more.", error.message)
        doc.close()
    }

    @Test
    fun `rejects margins larger than the page, naming its size`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val error = assertThrows(IllegalArgumentException::class.java) {
            cropPdf(doc, CropMargins(top = 200f, bottom = 200f, left = 0f, right = 0f))
        }
        assertEquals("The margins are larger than page 1 (200×300 pt).", error.message)
        doc.close()
    }

    @Test
    fun `crops every page by the given margins, moving only the crop box`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val bytes = cropPdf(doc, CropMargins(top = 10f, bottom = 20f, left = 5f, right = 15f))
        doc.close()

        reload(bytes).use { reloaded ->
            val page = reloaded.getPage(0)
            val crop = page.cropBox
            assertEquals(5f, crop.lowerLeftX, 0.01f)
            assertEquals(20f, crop.lowerLeftY, 0.01f)
            assertEquals(180f, crop.width, 0.01f) // 200 - 5 - 15
            assertEquals(270f, crop.height, 0.01f) // 300 - 10 - 20

            // MediaBox is untouched -- crop is non-destructive.
            assertEquals(200f, page.mediaBox.width, 0.01f)
            assertEquals(300f, page.mediaBox.height, 0.01f)
        }
    }

    @Test
    fun `crops only the requested pages`() {
        val doc = buildPdf(PDRectangle(200f, 300f), PDRectangle(200f, 300f))
        val bytes = cropPdf(doc, CropMargins(top = 10f, bottom = 10f, left = 10f, right = 10f), pages = "1")
        doc.close()

        reload(bytes).use { reloaded ->
            assertEquals(180f, reloaded.getPage(0).cropBox.width, 0.01f)
            assertEquals(200f, reloaded.getPage(1).cropBox.width, 0.01f)
        }
    }

    @Test
    fun `surfaces range errors the same way Rotate does`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val error = assertThrows(IllegalArgumentException::class.java) {
            cropPdf(doc, CropMargins(0f, 0f, 0f, 0f), pages = "99")
        }
        assertEquals("Page 99 is outside this 1-page document.", error.message)
        doc.close()
    }

    // --- resizePdf ---------------------------------------------------------

    @Test
    fun `rejects a non-positive target size`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val error = assertThrows(IllegalArgumentException::class.java) {
            resizePdf(doc, PaperSize(0f, 100f))
        }
        assertEquals("Target page size must be a positive width and height.", error.message)
        doc.close()
    }

    @Test
    fun `scale-to-fit resizes every page to exactly the target box`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val bytes = resizePdf(doc, PAPER_SIZES.getValue("A4"))
        doc.close()

        reload(bytes).use { reloaded ->
            val box = reloaded.getPage(0).mediaBox
            assertEquals(595.28f, box.width, 0.01f)
            assertEquals(841.89f, box.height, 0.01f)
        }
    }

    @Test
    fun `stretch resizes non-uniformly to the exact target box too`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val bytes = resizePdf(doc, PaperSize(400f, 400f), stretch = true)
        doc.close()

        reload(bytes).use { reloaded ->
            val box = reloaded.getPage(0).mediaBox
            assertEquals(400f, box.width, 0.01f)
            assertEquals(400f, box.height, 0.01f)
        }
    }

    @Test
    fun `resizes only the requested pages`() {
        val doc = buildPdf(PDRectangle(200f, 300f), PDRectangle(200f, 300f))
        val bytes = resizePdf(doc, PaperSize(400f, 400f), pages = "1")
        doc.close()

        reload(bytes).use { reloaded ->
            assertEquals(400f, reloaded.getPage(0).mediaBox.width, 0.01f)
            assertEquals(200f, reloaded.getPage(1).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun `output round-trips and keeps the page count`() {
        val doc = buildPdf(PDRectangle(200f, 300f), PDRectangle(200f, 300f), PDRectangle(200f, 300f))
        val bytes = resizePdf(doc, PaperSize(400f, 400f))
        doc.close()

        reload(bytes).use { reloaded -> assertEquals(3, reloaded.numberOfPages) }
    }

    @Test
    fun `resized output is larger than an unresized save, confirming a transform was actually written`() {
        val doc = buildPdf(PDRectangle(200f, 300f))
        val baseline = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()

        val resized = resizePdf(doc, PaperSize(400f, 400f))
        doc.close()

        assertTrue(resized.size > baseline.size)
    }

    @Test
    fun `scale is applied ahead of pre-existing content, not undone by a save-restore wrapper`() {
        // A blank PDPage() has no content stream at all, which skips
        // PDPageContentStream's append/prepend-to-existing-stream branch
        // entirely -- the one place a resetContext bug could silently
        // wrap the prepended `cm` in a save/restore pair that cancels it
        // before the original content runs. This test gives the page
        // real content first specifically to exercise that branch.
        val doc = PDDocument()
        val page = PDPage(PDRectangle(200f, 300f))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.addRect(0f, 0f, 10f, 10f)
            stream.fill()
        }

        val bytes = resizePdf(doc, PaperSize(400f, 400f), stretch = true)
        doc.close()

        reload(bytes).use { reloaded ->
            val reloadedPage = reloaded.getPage(0)
            assertEquals(400f, reloadedPage.mediaBox.width, 0.01f)
            assertEquals(400f, reloadedPage.mediaBox.height, 0.01f)

            val content = String(reloadedPage.contents.readBytes(), Charsets.ISO_8859_1)
            val cmIndex = content.indexOf(" cm")
            val rectIndex = content.indexOf(" re")
            assertTrue(
                "expected a cm operator before the original re (rect) operator, got: $content",
                cmIndex in 0 until rectIndex,
            )
        }
    }
}
