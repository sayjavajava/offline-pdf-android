package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Spike C measurement — see `PdfCompressSpike.kt`'s header and
 * `CODE_AUDIT.md`'s Spike C write-up for the full context. This
 * deliberately fails with the real measured numbers so the CI log (the
 * only place this sandbox can actually run PdfBox-Android) carries them
 * back out — a repeat of the same technique used before a real bug (double
 * -compression) was caught by this very correctness check and fixed in
 * `PdfCompressSpike.kt`.
 */
class PdfCompressSpikeTest {

    private val repeatedLine = "The quick brown fox jumps over the lazy dog."

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
                stream.showText("$repeatedLine Line $i.")
                stream.endText()
            }
        }
        return document
    }

    @Test
    fun `measure real before-after size and verify recompressed content is still valid`() {
        val document = buildRepetitiveFixture()
        val result = recompressPageContentStreams(document)

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()

        val reloaded = PDDocument.load(ByteArrayInputStream(out.toByteArray()))
        val text = extractText(reloaded, "all").single().text
        assertTrue("recompressed content must still contain its original text", text.contains(repeatedLine))
        assertEquals(200, Regex(Regex.escape(repeatedLine)).findAll(text).count())
        reloaded.close()

        fail("MEASURED: original=${result.originalBytes} recompressed=${result.recompressedBytes}")
    }
}
