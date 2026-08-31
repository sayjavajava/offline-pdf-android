package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Spike C measurement — see `PdfCompressSpike.kt`'s header and
 * `CODE_AUDIT.md`'s Spike C write-up for the full context. Real numbers
 * only: this deliberately fails on its first run so the CI log's full
 * exception message (`testLogging` is already configured for this,
 * A-10) carries the actual measured byte counts back out of the only
 * environment this sandbox can run PdfBox-Android in at all.
 */
class PdfCompressSpikeTest {

    private fun buildRepetitiveFixture(): PDDocument {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.setFont(PDType1Font.HELVETICA, 10f)
            // Deliberately repetitive: many identical operator sequences,
            // the shape most amenable to a higher compression ratio.
            for (i in 0 until 200) {
                stream.beginText()
                stream.newLineAtOffset(72f, (700 - i * 3).toFloat())
                stream.showText("The quick brown fox jumps over the lazy dog. Line $i.")
                stream.endText()
            }
        }
        return document
    }

    @Test
    fun `measure real before-after size from recompressing a repetitive content stream`() {
        val document = buildRepetitiveFixture()
        val result = recompressPageContentStreams(document)
        fail("MEASURED: original=${result.originalBytes} recompressed=${result.recompressedBytes}")
        document.close()
    }
}
