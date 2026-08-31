package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Web reference: `addWatermark`/`WatermarkOptions` (`pdf-ops.ts`), verified against `AddWatermarkTool.tsx`'s option ranges. */
class PdfWatermarkTest {

    private fun buildPdf(pageCount: Int = 1): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { document.addPage(PDPage()) }
        return document
    }

    private val defaultOptions = WatermarkOptions(
        fontSize = 50f,
        color = WatermarkColor(1f, 0f, 0f),
        opacity = 0.5f,
        rotation = 45f,
    )

    // --- validation --------------------------------------------------------

    @Test
    fun `rejects blank watermark text`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            addWatermark(doc, "   ", defaultOptions)
        }
        assertEquals("Enter watermark text.", error.message)
        doc.close()
    }

    @Test
    fun `rejects opacity outside 0 to 1`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            addWatermark(doc, "X", defaultOptions.copy(opacity = 1.5f))
        }
        assertEquals("Opacity must be between 0 and 1.", error.message)
        doc.close()
    }

    @Test
    fun `rejects font size outside 1 to 300`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            addWatermark(doc, "X", defaultOptions.copy(fontSize = 301f))
        }
        assertEquals("Font size must be between 1 and 300.", error.message)
        doc.close()
    }

    @Test
    fun `rejects rotation outside -360 to 360`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            addWatermark(doc, "X", defaultOptions.copy(rotation = 400f))
        }
        assertEquals("Rotation must be between -360 and 360 degrees.", error.message)
        doc.close()
    }

    @Test
    fun `rejects a color channel outside 0 to 1`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            addWatermark(doc, "X", defaultOptions.copy(color = WatermarkColor(1.2f, 0f, 0f)))
        }
        assertEquals("Watermark colour channels must each be between 0 and 1.", error.message)
        doc.close()
    }

    // --- actual output -------------------------------------------------------

    @Test
    fun `output round-trips and keeps the same page count`() {
        val doc = buildPdf(pageCount = 2)
        val bytes = addWatermark(doc, "CONFIDENTIAL", defaultOptions)
        doc.close()

        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { reloaded ->
                assertEquals(2, reloaded.numberOfPages)
            }
        }
    }

    @Test
    fun `output is larger than an unwatermarked save, confirming content was actually written`() {
        val doc = buildPdf()
        val baseline = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()

        val watermarked = addWatermark(doc, "CONFIDENTIAL", defaultOptions)
        doc.close()

        assertTrue(watermarked.size > baseline.size)
    }

    @Test
    fun `tiling produces a larger output than a single centered stamp`() {
        val singleDoc = buildPdf()
        val single = addWatermark(singleDoc, "CONFIDENTIAL", defaultOptions)
        singleDoc.close()

        val tiledDoc = buildPdf()
        val tiled = addWatermark(tiledDoc, "CONFIDENTIAL", defaultOptions.copy(tile = true))
        tiledDoc.close()

        assertTrue(tiled.size > single.size)
    }

    @Test
    fun `works across multiple pages`() {
        val doc = buildPdf(pageCount = 3)
        val bytes = addWatermark(doc, "DRAFT", defaultOptions)
        doc.close()

        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { reloaded ->
                assertEquals(3, reloaded.numberOfPages)
            }
        }
    }
}
