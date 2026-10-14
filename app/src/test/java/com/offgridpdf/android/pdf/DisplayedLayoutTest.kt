package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Page Numbers and Watermark must lay text out on the page *as displayed*.
 * The oracle doesn't trust `PageSpace.kt`'s maths at all: a stamp on a
 * rotated or offset page must land exactly where the same stamp lands on a
 * plain, unrotated page with the same displayed size, as PDFBox's own
 * `TextPosition` reports it. `TextPosition` applies `/Rotate` and the crop
 * origin itself (proven by Spike D, `PdfTextPositionSpikeTest`).
 */
class DisplayedLayoutTest {

    private class Shape(val label: String, val rotation: Int, val mediaBox: PDRectangle)

    private val shapes = listOf(
        Shape("/Rotate 90", 90, PDRectangle(0f, 0f, 612f, 792f)),
        Shape("/Rotate 180", 180, PDRectangle(0f, 0f, 612f, 792f)),
        Shape("/Rotate 270", 270, PDRectangle(0f, 0f, 612f, 792f)),
        Shape("MediaBox at (100,100)", 0, PDRectangle(100f, 100f, 612f, 792f)),
        Shape("MediaBox at (100,100), /Rotate 90", 90, PDRectangle(100f, 100f, 612f, 792f)),
    )

    private fun page(mediaBox: PDRectangle, rotation: Int): PDDocument {
        val doc = PDDocument()
        val page = PDPage(mediaBox)
        page.rotation = rotation
        doc.addPage(page)
        return doc
    }

    /** The plain page a reader couldn't tell apart from [shape]. */
    private fun reference(shape: Shape): PDDocument {
        val (w, h) = displayedSize(PageBox(0f, 0f, shape.mediaBox.width, shape.mediaBox.height), shape.rotation)
        return page(PDRectangle(0f, 0f, w, h), 0)
    }

    private fun glyphs(bytes: ByteArray): List<CharBox> =
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc -> collectWordPositions(doc, 1).flatMap { it.chars } }

    private fun assertSameLayout(expected: List<CharBox>, actual: List<CharBox>, what: String) {
        assertTrue("$what: nothing drawn", expected.isNotEmpty())
        assertEquals("$what: glyph count", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertEquals("$what: glyph $i x", e.x, a.x, 0.5f)
            assertEquals("$what: glyph $i y", e.y, a.y, 0.5f)
        }
    }

    @Test
    fun `page numbers land where they would on the equivalent plain page`() {
        for (position in listOf(PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.TOP_RIGHT, PageNumberPosition.BOTTOM_LEFT)) {
            val options = PageNumberOptions(prefix = "Page ", position = position)
            for (shape in shapes) {
                val expected = reference(shape).use { glyphs(addPageNumbers(it, options)) }
                val actual = page(shape.mediaBox, shape.rotation).use { glyphs(addPageNumbers(it, options)) }
                assertSameLayout(expected, actual, "$position on ${shape.label}")
            }
        }
    }

    @Test
    fun `a watermark lands where it would on the equivalent plain page, at the chosen angle`() {
        for (angle in listOf(0f, 30f)) {
            val options = WatermarkOptions(fontSize = 40f, color = WatermarkColor(0f, 0f, 0f), opacity = 1f, rotation = angle)
            for (shape in shapes) {
                val expected = reference(shape).use { glyphs(addWatermark(it, "DRAFT", options)) }
                val actual = page(shape.mediaBox, shape.rotation).use { glyphs(addWatermark(it, "DRAFT", options)) }
                assertSameLayout(expected, actual, "$angle° watermark on ${shape.label}")
            }
        }
    }

    @Test
    fun `an ordinary page is laid out exactly as before`() {
        // Unrotated, box at (0,0): bottom-centre is (612 - label width) / 2 across, 36pt up.
        val label = page(PDRectangle(0f, 0f, 612f, 792f), 0).use { glyphs(addPageNumbers(it, PageNumberOptions())) }
        assertEquals(1, label.size)
        val width = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA.getStringWidth("1") / 1000f * 12f
        assertEquals((612f - width) / 2f, label[0].x, 0.01f)
        assertEquals(792f - 36f, label[0].y, 0.01f)
    }
}
